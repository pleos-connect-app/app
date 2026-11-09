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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
        if (session == null || session.phase != TripPhase.RECORDING) {
            Log.i(TAG, "no recording session, stopping")
            stopSelf()
            return START_NOT_STICKY
        }
        if (!container.location.hasPermission()) {
            Log.w(TAG, "location permission missing, stopping")
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground(session.destination.name)
        if (job == null) job = lifecycleScope.launch { record(session) }
        return START_STICKY
    }

    private suspend fun record(session: TripSession) {
        val store = container.tripStore
        val detector = ArrivalDetector(session.destination.latLng)
        lifecycleScope.launch {
            container.navi.events.collect { e ->
                when (e) {
                    is NaviEvent.WaypointAdded -> store.addEvent(event(TripEventType.WAYPOINT_ADDED, e.name))
                    is NaviEvent.WaypointArrived -> store.addEvent(event(TripEventType.WAYPOINT_ARRIVED, e.name))
                    is NaviEvent.DestinationArrived -> markArrived(ArrivalSource.NAVI_EVENT)
                    else -> Unit
                }
            }
        }
        container.location.updates().collect { p ->
            if (store.session.value?.phase != TripPhase.RECORDING) {
                stopSelf()
                return@collect
            }
            lastPoint = p
            store.appendPoint(p)
            if (detector.onPoint(p)) markArrived(ArrivalSource.APP_GEOFENCE)
        }
    }

    private fun event(type: TripEventType, name: String?): TripEvent {
        val at = lastPoint?.latLng ?: container.tripStore.session.value?.origin ?: LatLng(0.0, 0.0)
        return TripEvent(type, System.currentTimeMillis(), at.lat, at.lng, name)
    }

    private fun markArrived(source: ArrivalSource) {
        val store = container.tripStore
        val cur = store.session.value ?: return
        if (cur.phase != TripPhase.RECORDING) return
        Log.i(TAG, "arrived via $source")
        val now = System.currentTimeMillis()
        val d = cur.destination
        // 도착 이벤트 좌표는 목적지 좌표(내비 이벤트는 위치를 주지 않음, 앱 판정은 반경 안이므로 근사).
        store.update {
            it.copy(
                phase = TripPhase.ARRIVED,
                endedAt = now,
                arrivalSource = source,
                events = it.events + TripEvent(TripEventType.DESTINATION_ARRIVED, now, d.lat, d.lng, d.name),
            )
        }
        stopSelf()
    }

    private fun startInForeground(destinationName: String) {
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
    }

    companion object {
        private const val TAG = "TripRecording"
        private const val CHANNEL_ID = "trip_recording"
        private const val NOTIFICATION_ID = 1001

        /** 화면이 보이는 상태(포그라운드)에서 호출해야 한다 — 백그라운드 위치 FGS 시작 제한. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TripRecordingService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TripRecordingService::class.java))
        }
    }
}
