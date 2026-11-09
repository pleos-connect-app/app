package ai.randomdrive.app.trip

import ai.randomdrive.app.domain.ArrivalSource
import ai.randomdrive.app.domain.Destination
import ai.randomdrive.app.domain.LatLng
import ai.randomdrive.app.domain.RecapSummary
import ai.randomdrive.app.domain.TrackPoint
import ai.randomdrive.app.domain.TripEvent
import ai.randomdrive.app.domain.TripEventType
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 진행 중인 여행 1건을 앱 내부 저장소에 보관한다(프로세스 종료 후 복원용).
 * - session.json: 메타데이터·이벤트·단계
 * - points.csv:   GPS 점(ts,lat,lng) 한 줄씩 append — 1초마다 전체 파일을 다시 쓰지 않도록 분리
 * 위치 로그는 여행 기록 생성 목적에만 쓰고, 새 여행을 시작하거나 리캡 화면을 닫으면 삭제한다(최소 보관).
 */
class TripStore(dir: File) {
    private val root = File(dir, "trip").apply { mkdirs() }
    private val sessionFile = File(root, "session.json")
    private val pointsFile = File(root, "points.csv")
    private val lock = Any()

    private val _session = MutableStateFlow(load())
    val session: StateFlow<TripSession?> = _session.asStateFlow()

    fun start(session: TripSession) = synchronized(lock) {
        pointsFile.delete()
        write(session.copy(pointCount = 0))
    }

    fun update(transform: (TripSession) -> TripSession): TripSession? = synchronized(lock) {
        val cur = _session.value ?: return null
        val next = transform(cur)
        if (next != cur) write(next)
        next
    }

    fun appendPoint(p: TrackPoint) = synchronized(lock) {
        val cur = _session.value ?: return
        pointsFile.appendText("${p.ts},${p.lat},${p.lng}\n")
        // pointCount는 화면 표시용. 매 점마다 session.json을 쓰지 않도록 메모리만 갱신하고 10점마다 저장.
        val next = cur.copy(pointCount = cur.pointCount + 1)
        if (next.pointCount % 10 == 0) write(next) else _session.value = next
    }

    fun addEvent(e: TripEvent) {
        update { it.copy(events = it.events + e) }
    }

    fun points(): List<TrackPoint> = synchronized(lock) {
        if (!pointsFile.exists()) return emptyList()
        pointsFile.readLines().mapNotNull { line ->
            val parts = line.split(',')
            if (parts.size != 3) return@mapNotNull null // 쓰다 끊긴 마지막 줄 등
            val ts = parts[0].toLongOrNull()
            val lat = parts[1].toDoubleOrNull()
            val lng = parts[2].toDoubleOrNull()
            if (ts == null || lat == null || lng == null) null else TrackPoint(lat, lng, ts)
        }
    }

    fun lastPoint(): TrackPoint? = points().lastOrNull()

    fun clear() = synchronized(lock) {
        sessionFile.delete()
        pointsFile.delete()
        _session.value = null
    }

    private fun write(s: TripSession) {
        val tmp = File(root, "session.json.tmp")
        tmp.writeText(toJson(s).toString())
        if (!tmp.renameTo(sessionFile)) {
            sessionFile.delete()
            tmp.renameTo(sessionFile)
        }
        _session.value = s
    }

    private fun load(): TripSession? = runCatching {
        if (!sessionFile.exists()) return null
        fromJson(JSONObject(sessionFile.readText()))
    }.onFailure { Log.w(TAG, "session load failed, discarding", it) }.getOrNull()

    companion object {
        private const val TAG = "TripStore"

        fun toJson(s: TripSession): JSONObject = JSONObject().apply {
            put("tripId", s.tripId)
            put("destination", JSONObject().apply {
                val d = s.destination
                put("placeId", d.placeId); put("name", d.name); put("lat", d.lat); put("lng", d.lng)
                put("address", d.address); put("imageUrl", d.imageUrl ?: JSONObject.NULL); put("distanceKm", d.distanceKm)
            })
            put("originLat", s.origin.lat); put("originLng", s.origin.lng); put("originLabel", s.originLabel)
            put("radiusKm", s.radiusKm); put("phase", s.phase.name); put("startedAt", s.startedAt)
            put("endedAt", s.endedAt ?: JSONObject.NULL)
            put("arrivalSource", s.arrivalSource?.name ?: JSONObject.NULL)
            put("events", JSONArray().apply {
                s.events.forEach { e ->
                    put(JSONObject().put("type", e.type.name).put("ts", e.ts).put("lat", e.lat).put("lng", e.lng)
                        .put("name", e.name ?: JSONObject.NULL))
                }
            })
            put("finishAccepted", s.finishAccepted)
            put("shareUrl", s.shareUrl ?: JSONObject.NULL)
            put("summary", s.summary?.let(::summaryJson) ?: JSONObject.NULL)
            put("localSummary", s.localSummary?.let(::summaryJson) ?: JSONObject.NULL)
            put("pointCount", s.pointCount)
        }

        fun fromJson(o: JSONObject): TripSession {
            val d = o.getJSONObject("destination")
            val events = o.getJSONArray("events").let { arr ->
                List(arr.length()) { i ->
                    val e = arr.getJSONObject(i)
                    TripEvent(TripEventType.valueOf(e.getString("type")), e.getLong("ts"), e.getDouble("lat"),
                        e.getDouble("lng"), e.optNullableString("name"))
                }
            }
            return TripSession(
                tripId = o.getString("tripId"),
                destination = Destination(d.getString("placeId"), d.getString("name"), d.getDouble("lat"),
                    d.getDouble("lng"), d.getString("address"), d.optNullableString("imageUrl"), d.getDouble("distanceKm")),
                origin = LatLng(o.getDouble("originLat"), o.getDouble("originLng")),
                originLabel = o.getString("originLabel"),
                radiusKm = o.getInt("radiusKm"),
                phase = TripPhase.valueOf(o.getString("phase")),
                startedAt = o.getLong("startedAt"),
                endedAt = if (o.isNull("endedAt")) null else o.getLong("endedAt"),
                arrivalSource = o.optNullableString("arrivalSource")?.let { ArrivalSource.valueOf(it) },
                events = events,
                finishAccepted = o.optBoolean("finishAccepted", false),
                shareUrl = o.optNullableString("shareUrl"),
                summary = o.optJSONObject("summary")?.let(::parseSummary),
                localSummary = o.optJSONObject("localSummary")?.let(::parseSummary),
                pointCount = o.optInt("pointCount", 0),
            )
        }

        private fun summaryJson(s: RecapSummary) =
            JSONObject().put("distanceKm", s.distanceKm).put("durationMin", s.durationMin).put("stopCount", s.stopCount)

        private fun parseSummary(o: JSONObject) =
            RecapSummary(o.getDouble("distanceKm"), o.getInt("durationMin"), o.getInt("stopCount"))

        private fun JSONObject.optNullableString(key: String): String? =
            if (!has(key) || isNull(key)) null else getString(key)
    }
}
