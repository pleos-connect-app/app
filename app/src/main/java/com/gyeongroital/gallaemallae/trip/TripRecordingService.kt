package com.gyeongroital.gallaemallae.trip

import com.gyeongroital.gallaemallae.MainActivity
import com.gyeongroital.gallaemallae.R
import com.gyeongroital.gallaemallae.GallaemallaeApp
import com.gyeongroital.gallaemallae.domain.ArrivalDetector
import com.gyeongroital.gallaemallae.domain.ArrivalSource
import com.gyeongroital.gallaemallae.domain.LatLng
import com.gyeongroital.gallaemallae.domain.TripEvent
import com.gyeongroital.gallaemallae.domain.TripEventType
import com.gyeongroital.gallaemallae.domain.WaypointTracker
import com.gyeongroital.gallaemallae.domain.isKnown
import com.gyeongroital.gallaemallae.navi.NaviEvent
import com.gyeongroital.gallaemallae.navi.WaypointPoint
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

    private val container get() = (application as GallaemallaeApp).container
    private var job: Job? = null

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
        // 서비스가 다시 시작돼도 이미 기록한 경유지·도착 상태를 이어 간다
        waypoints = WaypointTracker(session.events)
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
                    is NaviEvent.WaypointArrived -> onWaypointArrived(e.name)
                    is NaviEvent.DestinationArrived -> markArrived(ArrivalSource.NAVI_EVENT)
                    else -> Unit
                }
            }
        }
        // 파일 쓰기(점 append)는 메인 스레드 밖에서.
        withContext(Dispatchers.IO) {
            container.location.updates().collect { p ->
                if (store.session.value?.phase != TripPhase.RECORDING) return@collect
                store.appendPoint(p)
                // 경유지 도착(앱 판정): 내비 알림이 오지 않는 환경(에뮬레이터) 대비. 내비 알림이 먼저 왔으면 건너뜀
                synchronized(waypoints) { waypoints.onPoint(p) }.forEach { (name, at) ->
                    store.addEvent(event(TripEventType.WAYPOINT_ARRIVED, name, at))
                }
                if (detector.onPoint(p)) markArrived(ArrivalSource.APP_GEOFENCE)
            }
        }
    }

    // ---- 경유지 ----
    // 좌표는 getWaypointInfo() 응답으로만 온다. 에뮬레이터 내비는 0,0을 주므로 이름·주소로 서버에 묻는다.
    // 끝내 모르면 0,0("모름")으로 기록한다 — 차량 위치로 대신 찍으면 영상에 엉뚱한 곳이 경유지로 나온다.
    private var waypoints = WaypointTracker()
    private var pendingWaypoint: Job? = null

    private fun onWaypointAdded(name: String?) {
        container.navi.requestWaypoints()
        pendingWaypoint?.cancel()
        // 5초 안에 경유지 목록이 안 오면 이름만으로 기록(좌표는 이름으로 찾아 본다).
        pendingWaypoint = lifecycleScope.launch {
            delay(5_000)
            val n = name ?: WaypointTracker.UNNAMED
            if (!reserve(n)) return@launch
            recordWaypoint(n, resolve(n, null))
        }
    }

    private fun onWaypoints(items: List<WaypointPoint>) {
        items.forEach { w ->
            if (!reserve(w.name)) return@forEach
            pendingWaypoint?.cancel()
            val given = LatLng(w.lat, w.lng).takeIf { it.isKnown }
            if (given != null) {
                recordWaypoint(w.name, given)
            } else {
                lifecycleScope.launch { recordWaypoint(w.name, resolve(w.name, w.address)) }
            }
        }
    }

    /** 처음 보는 경유지면 자리를 잡고 true(같은 경유지를 두 번 기록하지 않게). */
    private fun reserve(name: String): Boolean = synchronized(waypoints) {
        if (waypoints.isKnown(name)) false else { waypoints.add(name, null); true }
    }

    /** 이름 → 주소 순으로 서버(VWorld 검색)에 좌표를 묻는다. 못 찾으면 null. */
    private suspend fun resolve(name: String, address: String?): LatLng? =
        container.api.searchPlace(name) ?: address?.takeIf { it.isNotBlank() && it != name }?.let { container.api.searchPlace(it) }

    private fun recordWaypoint(name: String, at: LatLng?) {
        synchronized(waypoints) { waypoints.add(name, at) }
        Log.i(TAG, "waypoint added: $name at=${at ?: "unknown"}")
        container.tripStore.addEvent(event(TripEventType.WAYPOINT_ADDED, name, at))
    }

    private fun onWaypointArrived(name: String) {
        val first = synchronized(waypoints) { waypoints.markArrived(name) }
        if (first) container.tripStore.addEvent(event(TripEventType.WAYPOINT_ARRIVED, name, waypoints.coordOf(name)))
    }

    /** 경유지 이벤트. 좌표를 모르면 [WaypointTracker.UNKNOWN](0,0). */
    private fun event(type: TripEventType, name: String?, at: LatLng?): TripEvent {
        val pos = at ?: WaypointTracker.UNKNOWN
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
            .setSmallIcon(R.drawable.ic_stat_trip)
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
