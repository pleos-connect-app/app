package com.gyeongroital.gallaemallae.ui

import com.gyeongroital.gallaemallae.BuildConfig
import com.gyeongroital.gallaemallae.domain.MapCamera
import com.gyeongroital.gallaemallae.domain.Tile
import com.gyeongroital.gallaemallae.domain.WebMercator
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 추첨 화면 배경용 위성 타일. 공간정보 오픈플랫폼(VWorld, 국토교통부) WMTS를 우리 서버가 중계한다
 * (`GET /api/v1/map/satellite/{z}/{x}/{y}.jpeg`). VWorld 인증키는 APK에서 꺼낼 수 있어 앱에 두지 않는다.
 * - 앱 내장 가짜 서버(오프라인 개발)에서는 [enabled]=false → 화면은 지도 없는 배경으로 그린다.
 * - 서버에 키가 없거나 받지 못하면(503) 연속 실패로 꺼지고 같은 대체 화면이 된다.
 * - Pleos 단말에는 Google 지도가 없고 목적지가 모두 국내라 국내 공공 타일을 선택.
 * - 홈 화면에서 미리 받아 두고([prefetch]) 추첨 화면이 같은 요청을 이어받는다(진행 중 요청 공유).
 */
object SatelliteTiles {
    private const val TAG = "SatelliteTiles"
    private const val TIMEOUT_MS = 8_000
    private const val MAX_TILE_BYTES = 512 * 1024
    private const val MAX_FAILURES = 8

    const val MIN_ZOOM = 6
    const val MAX_ZOOM = 18

    // TODO(license): VWorld 이용약관의 출처 표기 문구 최종 확인
    const val ATTRIBUTION = "지도: 브이월드(국토교통부)"

    private val baseUrl = BuildConfig.SERVER_BASE_URL.trimEnd('/')
    private val appKey = BuildConfig.SERVER_API_KEY
    private val failures = AtomicInteger(0)

    /** 실서버에 붙어 있고, 연속 실패(서버 키 없음·오프라인)로 꺼지지 않았을 때만 사용. */
    val enabled: Boolean get() = !BuildConfig.USE_FAKE_SERVER && failures.get() < MAX_FAILURES

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(6)
    private val inFlight = ConcurrentHashMap<Tile, Deferred<Bitmap?>>()

    // RGB_565 256px 타일 = 128KB → 16MB에 약 120장
    private val cache = object : LruCache<Tile, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: Tile, value: Bitmap) = value.byteCount
    }

    fun cached(t: Tile): Bitmap? = cache.get(t)

    fun request(t: Tile): Deferred<Bitmap?> {
        cache.get(t)?.let { return CompletableDeferred(it) }
        if (!enabled) return CompletableDeferred(null)
        return inFlight.computeIfAbsent(t) {
            scope.async {
                try {
                    gate.withPermit { fetch(t) }?.also { cache.put(t, it) }
                } finally {
                    inFlight.remove(t)
                }
            }
        }
    }

    /** 화면 크기와 반경으로 추첨 화면이 쓸 타일을 미리 요청(결과는 캐시에 남음). */
    fun prefetch(cam: MapCamera, widthPx: Int, heightPx: Int) {
        if (!enabled) return
        WebMercator.visibleTiles(cam, widthPx, heightPx, MIN_ZOOM, MAX_ZOOM).forEach { request(it) }
    }

    private fun fetch(t: Tile): Bitmap? = runCatching {
        val url = URL("$baseUrl/api/v1/map/satellite/${t.z}/${t.x}/${t.y}.jpeg")
        val bytes = (url.openConnection() as HttpURLConnection).run {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            if (appKey.isNotBlank()) setRequestProperty("X-App-Key", appKey)
            try {
                if (responseCode !in 200..299) error("HTTP $responseCode")
                if (contentType?.startsWith("image/") != true) error("not an image: $contentType")
                inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        if (out.size() > MAX_TILE_BYTES) error("tile too large")
                    }
                    out.toByteArray()
                }
            } finally {
                disconnect()
            }
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 })
            ?: error("decode failed")
    }.onSuccess { failures.set(0) }
        .onFailure { if (failures.incrementAndGet() <= 2) Log.w(TAG, "tile ${t.z}/${t.x}/${t.y} failed: ${it.message}") }
        .getOrNull()
}
