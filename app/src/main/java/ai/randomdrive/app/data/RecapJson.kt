package ai.randomdrive.app.data

import ai.randomdrive.app.domain.Destination
import ai.randomdrive.app.domain.Recap
import ai.randomdrive.app.domain.RecapStatus
import ai.randomdrive.app.domain.RecapSummary
import org.json.JSONArray
import org.json.JSONObject

/** 서버 계약 JSON ↔ 모델 변환. org.json만 사용(추가 의존성 없음). */
object RecapJson {

    fun randomDestinationRequest(lat: Double, lng: Double, radiusKm: Int, demo: Boolean): String =
        JSONObject().put("lat", lat).put("lng", lng).put("radiusKm", radiusKm).put("demo", demo).toString()

    fun finishRequest(r: FinishTripRequest): String {
        val points = JSONArray()
        r.points.forEach { points.put(JSONObject().put("lat", it.lat).put("lng", it.lng).put("ts", it.ts)) }
        val events = JSONArray()
        r.events.forEach { e ->
            val o = JSONObject().put("type", e.type.name).put("ts", e.ts).put("lat", e.lat).put("lng", e.lng)
            if (e.name != null) o.put("name", e.name)
            events.put(o)
        }
        return JSONObject().put("startedAt", r.startedAt).put("endedAt", r.endedAt)
            .put("points", points).put("events", events).toString()
    }

    fun parseRandomDestination(body: String): RandomDestinationResult {
        val o = JSONObject(body)
        val d = o.getJSONObject("destination")
        return RandomDestinationResult(
            tripId = o.getString("tripId"),
            destination = Destination(
                placeId = d.getString("placeId"),
                name = d.getString("name"),
                lat = d.getDouble("lat"),
                lng = d.getDouble("lng"),
                address = d.optString("address", ""),
                imageUrl = d.optStringOrNull("imageUrl"),
                distanceKm = d.optDouble("distanceKm", 0.0),
            ),
        )
    }

    fun parseStatus(body: String): RecapStatusResult {
        val o = JSONObject(body)
        return RecapStatusResult(o.getString("tripId"), parseStatusValue(o.getString("status")))
    }

    fun parseRecap(body: String): Recap {
        val o = JSONObject(body)
        val s = o.optJSONObject("summary")
        return Recap(
            tripId = o.getString("tripId"),
            status = parseStatusValue(o.getString("status")),
            shareUrl = o.optStringOrNull("shareUrl"),
            videoUrl = o.optStringOrNull("videoUrl"),
            summary = s?.let { RecapSummary(it.getDouble("distanceKm"), it.getInt("durationMin"), it.getInt("stopCount")) },
        )
    }

    /** {"code","message"} 오류 바디. 파싱 실패 시 null. */
    fun parseError(body: String?): Pair<String, String>? = runCatching {
        val o = JSONObject(body ?: return null)
        o.getString("code") to o.optString("message", "")
    }.getOrNull()

    /** 알 수 없는 상태값은 FAILED로 취급하지 않고 RUNNING으로 보아 계속 조회한다(서버가 상태를 추가해도 앱이 깨지지 않게). */
    private fun parseStatusValue(v: String): RecapStatus =
        RecapStatus.entries.firstOrNull { it.name == v } ?: RecapStatus.RUNNING

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key)
}
