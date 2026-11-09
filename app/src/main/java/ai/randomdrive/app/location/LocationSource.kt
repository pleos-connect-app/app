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
     * 권한이 없으면 아무것도 내보내지 않고 바로 끝난다.
     */
    @SuppressLint("MissingPermission")
    fun updates(intervalMs: Long = 1_000L): Flow<TrackPoint> = callbackFlow {
        val m = manager
        if (!hasPermission() || m == null) {
            close()
            return@callbackFlow
        }
        val listener = LocationListener { loc -> trySend(loc.toPoint()) }
        val providers = PROVIDERS.filter { p -> runCatching { m.isProviderEnabled(p) }.getOrDefault(false) }
        providers.forEach { p ->
            runCatching { m.requestLocationUpdates(p, intervalMs, 0f, listener, Looper.getMainLooper()) }
                .onFailure { Log.w(TAG, "requestLocationUpdates($p) failed", it) }
        }
        awaitClose { m.removeUpdates(listener) }
    }

    private fun Location.toPoint() = TrackPoint(latitude, longitude, if (time > 0) time else System.currentTimeMillis())

    companion object {
        private const val TAG = "LocationSource"
        // GPS 우선. 실차에서 GPS가 없을 때를 대비해 network도 함께 듣는다(둘 다 오면 시간순으로 섞임).
        private val PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    }
}
