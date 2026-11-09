package ai.randomdrive.app

import ai.randomdrive.app.data.FakeRecapApi
import ai.randomdrive.app.data.HttpRecapApi
import ai.randomdrive.app.data.RecapApi
import ai.randomdrive.app.domain.Candidate
import ai.randomdrive.app.location.LocationSource
import ai.randomdrive.app.navi.NaviGateway
import ai.randomdrive.app.trip.TripStore
import android.content.Context
import android.util.Log
import org.json.JSONObject

/** 앱 전역 객체 모음(수동 DI). Cold start 비용을 줄이려고 무거운 객체는 lazy. */
class AppContainer(private val context: Context) {
    val tripStore by lazy { TripStore(context.filesDir) }
    val location by lazy { LocationSource(context) }
    val navi by lazy { NaviGateway(context) }

    val usingFakeServer: Boolean = BuildConfig.USE_FAKE_SERVER
    val demoMode: Boolean = BuildConfig.DEMO_MODE

    val api: RecapApi by lazy {
        if (usingFakeServer) FakeRecapApi(candidates = { candidates }) else HttpRecapApi(BuildConfig.SERVER_BASE_URL)
    }

    private val candidates: List<Candidate> by lazy { loadCandidates() }

    private fun loadCandidates(): List<Candidate> = runCatching {
        val json = context.assets.open("candidates.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val arr = JSONObject(json).getJSONArray("candidates")
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Candidate(o.getString("placeId"), o.getString("name"), o.getDouble("lat"), o.getDouble("lng"), o.getString("address"))
        }
    }.onFailure { Log.e("AppContainer", "candidates.json load failed", it) }.getOrDefault(emptyList())
}
