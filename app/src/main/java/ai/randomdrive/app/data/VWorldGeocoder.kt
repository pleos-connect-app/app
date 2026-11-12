package ai.randomdrive.app.data

import ai.randomdrive.app.BuildConfig
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 좌표의 행정구역 이름. [full]="서울특별시 용산구 동자동", [short]="용산구 동자동". */
data class AreaName(val full: String, val short: String)

/**
 * 좌표 → 시·구·동 이름. 공간정보 오픈플랫폼(VWorld) 지오코더 API(역지오코딩)를 쓴다.
 * Pleos 에뮬레이터에는 Android Geocoder가 없어 출발지가 "현재 위치"로만 나오던 문제를 해결한다.
 * 키는 위성 지도와 같은 `randomdrive.vworldKey`. 없거나 실패하면 null(호출 측이 다른 방법·기본 문구 사용).
 */
object VWorldGeocoder {
    private const val TAG = "VWorldGeocoder"
    private const val TIMEOUT_MS = 5_000

    val enabled: Boolean get() = BuildConfig.VWORLD_API_KEY.isNotBlank()

    suspend fun reverse(lat: Double, lng: Double): AreaName? = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext null
        runCatching {
            val url = URL(
                "https://api.vworld.kr/req/address?service=address&request=getAddress&version=2.0&crs=epsg:4326" +
                    "&point=$lng,$lat&format=json&type=PARCEL&zipcode=false&simple=false&key=${BuildConfig.VWORLD_API_KEY}",
            )
            val body = (url.openConnection() as HttpURLConnection).run {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                try {
                    if (responseCode !in 200..299) error("HTTP $responseCode")
                    inputStream.bufferedReader().use { it.readText() }
                } finally {
                    disconnect()
                }
            }
            parse(body)
        }.onFailure { Log.w(TAG, "reverse geocode failed: ${it.message}") }.getOrNull()
    }

    /** 응답 JSON에서 시·구·동을 뽑는다. 바다 등 결과가 없으면 null. */
    internal fun parse(json: String): AreaName? {
        val res = JSONObject(json).getJSONObject("response")
        if (res.optString("status") != "OK") return null
        val s = res.getJSONArray("result").getJSONObject(0).getJSONObject("structure")
        val city = s.optString("level1")      // 서울특별시, 경기도
        val gu = s.optString("level2")        // 용산구, 수원시 영통구
        val sub = s.optString("level3")       // (읍·면 아래 리 등이 있을 때)
        val dong = s.optString("level4L")     // 법정동·읍·면
        val full = listOf(city, gu, sub, dong).filter { it.isNotBlank() }.joinToString(" ")
        val short = listOf(gu, dong).filter { it.isNotBlank() }.joinToString(" ").ifBlank { full }
        return if (full.isBlank()) null else AreaName(full, short)
    }
}
