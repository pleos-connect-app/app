package ai.randomdrive.app.location

import ai.randomdrive.app.domain.TrackPoint
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * 위치 수신. Android 표준 LocationManager를 쓴다.
 * 에뮬레이터 내비는 가상 GPS를 인식하지 않지만 LocationManager로는 정상 수신된다(공모전 운영사무국 답변, 실측 확인).
 * Pleos FusedLocation SDK도 같은 좌표를 받지만, 표준 API로 충분해 SDK 의존을 늘리지 않았다.
 */
class LocationSource(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private val manager get() = context.getSystemService(LocationManager::class.java)

    /** 위치 기능(GPS 또는 네트워크)이 켜져 있는지. */
    fun isEnabled(): Boolean = runCatching {
        val m = manager ?: return false
        m.isProviderEnabled(LocationManager.GPS_PROVIDER) || m.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    fun lastKnown(): TrackPoint? {
        if (!hasPermission()) return null
        val m = manager ?: return null
        return PROVIDERS.mapNotNull { p -> runCatching { m.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }?.toPoint()
    }

    /**
     * 위치 업데이트 스트림. 구독이 끊기면(awaitClose) 즉시 해제한다 — 화면이 백그라운드로 가면 수집을 멈추는 용도.
     * - GPS 우선: 최근 [GPS_FRESH_MS] 안에 GPS가 왔으면 network 위치는 버린다(정확도가 다른 두 소스가 섞이면
     *   경로가 지그재그가 되고 도착 판정 체류 시간이 끊김).
     * - 정확도가 [MAX_ACCURACY_M]보다 나쁜 위치는 버린다.
     * - 지금 꺼져 있는 provider에도 등록해 둔다 → 사용자가 나중에 위치를 켜면 그때부터 들어온다.
     * 권한이 없으면 아무것도 내보내지 않고 바로 끝난다.
     */
    @SuppressLint("MissingPermission")
    fun updates(intervalMs: Long = 1_000L): Flow<TrackPoint> = callbackFlow {
        val m = manager
        if (!hasPermission() || m == null) {
            close()
            return@callbackFlow
        }
        var lastGpsAt = 0L
        val listener = LocationListener { loc ->
            if (loc.hasAccuracy() && loc.accuracy > MAX_ACCURACY_M) return@LocationListener
            val now = SystemClock.elapsedRealtime()
            if (loc.provider == LocationManager.GPS_PROVIDER) {
                lastGpsAt = now
            } else if (lastGpsAt != 0L && now - lastGpsAt < GPS_FRESH_MS) {
                return@LocationListener
            }
            trySend(loc.toPoint())
        }
        val existing = runCatching { m.allProviders }.getOrDefault(emptyList())
        PROVIDERS.filter { it in existing }.forEach { p ->
            runCatching { m.requestLocationUpdates(p, intervalMs, 0f, listener, Looper.getMainLooper()) }
                .onFailure { Log.w(TAG, "requestLocationUpdates($p) failed", it) }
        }
        awaitClose { m.removeUpdates(listener) }
    }

    private fun Location.toPoint() = TrackPoint(latitude, longitude, if (time > 0) time else System.currentTimeMillis())

    companion object {
        private const val TAG = "LocationSource"
        private const val GPS_FRESH_MS = 10_000L
        private const val MAX_ACCURACY_M = 100f
        // GPS 우선. 실차에서 GPS가 없을 때를 대비해 network도 함께 듣는다(둘 다 오면 시간순으로 섞임).
        private val PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    }
}
