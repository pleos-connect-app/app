package ai.randomdrive.app.trip

import ai.randomdrive.app.MainActivity
import ai.randomdrive.app.R
import ai.randomdrive.app.RandomDriveApp
import ai.randomdrive.app.domain.ArrivalDetector
import ai.randomdrive.app.domain.ArrivalSource
import ai.randomdrive.app.domain.LatLng
import ai.randomdrive.app.domain.TrackPoint
import ai.randomdrive.app.domain.TripEvent
import ai.randomdrive.app.domain.TripEventType
import ai.randomdrive.app.navi.NaviEvent
import ai.randomdrive.app.navi.WaypointPoint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 여행 중 GPS·내비 이벤트 기록 (MVP F-03/F-04).
 * 내비가 화면을 차지하면 앱 Activity는 백그라운드가 되므로, 위치 수집은 포그라운드 서비스(type=location)에서 한다.
 * - 도착 판정: 내비 onDestinationArrived 또는 앱 자체 판정(ArrivalDetector) 중 먼저 오는 것.
 * - 도착하면 기록을 멈추고 서비스를 끝낸다. 사용자는 정차 후 앱에서 리캡을 만든다.
 */
class TripRecordingService : LifecycleService() {

    private val container get() = (application as RandomDriveApp).container
    private var job: Job? = null
    private var lastPoint: TrackPoint? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val session = container.tripStore.session.value
        // startForegroundService()로 시작됐으면 무조건 먼저 startForeground를 불러야 한다(안 부르고 끝내면 앱이 죽음).
        if (!startInForeground(session?.destination?.name ?: "")) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (session == null || session.phase != TripPhase.RECORDING || !container.location.hasPermission()) {
            Log.i(TAG, "nothing to record (session=${session?.phase}), stopping")
            stopSelf()
            return START_NOT_STICKY
        }
        if (job == null) job = lifecycleScope.launch { record(session) }
        // 프로세스가 죽으면 시스템이 백그라운드에서 재시작하게 두지 않는다 — 백그라운드에서 위치 FGS 시작은 막혀 있다.
        // 사용자가 앱을 다시 열면 MainViewModel.resumeSession()이 기록을 재개한다.
        return START_NOT_STICKY
    }

    private suspend fun record(session: TripSession) {
        val store = container.tripStore
        val detector = ArrivalDetector(session.destination.latLng)
        // 세션이 지워지거나(취소) 기록 단계가 끝나면(종료·도착) 스스로 멈춘다. 화면 쪽은 서비스를 직접 멈추지 않는다.
        lifecycleScope.launch {
            store.session.first { it == null || it.tripId != session.tripId || it.phase != TripPhase.RECORDING }
            Log.i(TAG, "recording finished, stopping service")
            stopSelf()
        }
        lifecycleScope.launch {
            container.navi.events.collect { e ->
                when (e) {
                    is NaviEvent.WaypointAdded -> onWaypointAdded(e.name)
                    is NaviEvent.Waypoints -> onWaypoints(e.items)
                    is NaviEvent.WaypointArrived -> store.addEvent(event(TripEventType.WAYPOINT_ARRIVED, e.name, waypointAt(e.name)))
                    is NaviEvent.DestinationArrived -> markArrived(ArrivalSource.NAVI_EVENT)
                    else -> Unit
                }
            }
        }
        // 파일 쓰기(점 append)는 메인 스레드 밖에서.
        withContext(Dispatchers.IO) {
            container.location.updates().collect { p ->
                if (store.session.value?.phase != TripPhase.RECORDING) return@collect
                lastPoint = p
                store.appendPoint(p)
                if (detector.onPoint(p)) markArrived(ArrivalSource.APP_GEOFENCE)
            }
        }
    }

    // ---- 경유지: 좌표는 getWaypointInfo() 응답으로만 온다 ----
    private val knownWaypoints = mutableListOf<WaypointPoint>()
    private var pendingWaypoint: Job? = null

    private fun onWaypointAdded(name: String?) {
        container.navi.requestWaypoints()
        pendingWaypoint?.cancel()
        // 5초 안에 좌표가 안 오면 차량 위치로라도 남긴다.
        pendingWaypoint = lifecycleScope.launch {
            delay(5_000)
            container.tripStore.addEvent(event(TripEventType.WAYPOINT_ADDED, name, null))
        }
    }

    private fun onWaypoints(items: List<WaypointPoint>) {
        val new = items.filter { w -> knownWaypoints.none { it == w } }
        if (new.isEmpty()) return
        pendingWaypoint?.cancel()
        knownWaypoints += new
        new.forEach { w -> container.tripStore.addEvent(event(TripEventType.WAYPOINT_ADDED, w.name, LatLng(w.lat, w.lng))) }
    }

    private fun waypointAt(name: String): LatLng? = knownWaypoints.firstOrNull { it.name == name }?.let { LatLng(it.lat, it.lng) }

    private fun event(type: TripEventType, name: String?, at: LatLng?): TripEvent {
        val pos = at ?: lastPoint?.latLng ?: container.tripStore.session.value?.origin ?: LatLng(0.0, 0.0)
        return TripEvent(type, System.currentTimeMillis(), pos.lat, pos.lng, name)
    }

    /** 내비 이벤트(메인 스레드)와 앱 판정(IO 스레드)이 동시에 와도 한 번만 기록되도록 검사와 갱신을 store 잠금 안에서 한다. */
    private fun markArrived(source: ArrivalSource) {
        container.tripStore.update { cur ->
            if (cur.phase != TripPhase.RECORDING) return@update cur
            Log.i(TAG, "arrived via $source")
            val now = System.currentTimeMillis()
            val d = cur.destination
            // 도착 이벤트 좌표는 목적지 좌표(내비 이벤트는 위치를 주지 않음, 앱 판정은 반경 안이므로 근사).
            cur.copy(
                phase = TripPhase.ARRIVED,
                endedAt = now,
                arrivalSource = source,
                events = cur.events + TripEvent(TripEventType.DESTINATION_ARRIVED, now, d.lat, d.lng, d.name),
            )
        }
    }

    /** 포그라운드 진입. 백그라운드 시작 제한 등으로 실패하면 false. */
    private fun startInForeground(destinationName: String): Boolean = try {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.recording_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.recording_notification_title))
            .setContentText(getString(R.string.recording_notification_text, destinationName))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        true
    } catch (e: Exception) { // SecurityException, ForegroundServiceStartNotAllowedException
        Log.w(TAG, "startForeground failed", e)
        false
    }

    companion object {
        private const val TAG = "TripRecording"
        private const val CHANNEL_ID = "trip_recording"
        private const val NOTIFICATION_ID = 1001

        /** 화면이 보이는 상태(포그라운드)에서 호출해야 한다 — 백그라운드 위치 FGS 시작 제한. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TripRecordingService::class.java))
        }

    }
}
