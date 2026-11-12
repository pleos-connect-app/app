package ai.randomdrive.app.ui

import ai.randomdrive.app.AppContainer
import ai.randomdrive.app.data.ApiException
import ai.randomdrive.app.data.FinishTripRequest
import ai.randomdrive.app.domain.CandidatePicker
import ai.randomdrive.app.domain.Destination
import ai.randomdrive.app.domain.Geo
import ai.randomdrive.app.domain.LatLng
import ai.randomdrive.app.domain.RecapStatus
import ai.randomdrive.app.domain.RecapSummary
import ai.randomdrive.app.domain.StopDetector
import ai.randomdrive.app.domain.TrackPoint
import ai.randomdrive.app.domain.TrackSimplifier
import ai.randomdrive.app.domain.TripEvent
import ai.randomdrive.app.domain.TripEventType
import ai.randomdrive.app.navi.NaviConnection
import ai.randomdrive.app.navi.RouteFailure
import ai.randomdrive.app.navi.RouteResult
import ai.randomdrive.app.trip.TripPhase
import ai.randomdrive.app.trip.TripRecordingService
import ai.randomdrive.app.trip.TripSession
import android.app.Application
import android.location.Geocoder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** 홈 화면의 현재 위치 상태. */
sealed interface LocationState {
    data object Loading : LocationState
    data object NoPermission : LocationState
    data object Disabled : LocationState
    data class Ready(val point: TrackPoint, val label: String) : LocationState
}

data class NaviTripInfo(val distanceM: Int, val etaS: Int)

/** 여행 시작 전(추첨~내비 전달) 단계. 저장하지 않는 일시 상태. */
sealed interface PreTrip {
    data object Home : PreTrip
    data class Drawing(val radiusKm: Int) : PreTrip
    data class Result(
        val tripId: String,
        val destination: Destination,
        val origin: LatLng,
        val originLabel: String,
        val radiusKm: Int,
        val naviTrip: NaviTripInfo? = null,
        val naviTripLoading: Boolean = true,
        val sending: Boolean = false,
        val sendError: String? = null,
        val seenPlaceIds: Set<String> = emptySet(),
    ) : PreTrip
}

/** 리캡 생성 진행 상태(FINISHING 단계에서만 의미). */
sealed interface FinishState {
    data object Uploading : FinishState
    data object Generating : FinishState
    data class Error(val message: String, val canRetry: Boolean) : FinishState
}

data class UiState(
    val location: LocationState = LocationState.Loading,
    val naviConnection: NaviConnection = NaviConnection.CONNECTING,
    val preTrip: PreTrip = PreTrip.Home,
    val session: TripSession? = null,
    val finish: FinishState = FinishState.Uploading,
    val message: String? = null,
    val usingFakeServer: Boolean = false,
)

class MainViewModel(app: Application, private val c: AppContainer) : AndroidViewModel(app) {

    private val foreground = MutableStateFlow(false)
    private val location = MutableStateFlow<LocationState>(LocationState.Loading)
    private val preTrip = MutableStateFlow<PreTrip>(PreTrip.Home)
    private val finish = MutableStateFlow<FinishState>(FinishState.Uploading)
    private val message = MutableStateFlow<String?>(null)

    private var locationJob: Job? = null
    private var finishJob: Job? = null
    private var labelCache: Pair<LatLng, String>? = null

    val state: StateFlow<UiState> = combine(
        combine(location, c.navi.connection, preTrip, ::Triple),
        c.tripStore.session,
        finish,
        message,
    ) { (loc, navi, pre), session, fin, msg ->
        UiState(loc, navi, pre, session, fin, msg, c.usingFakeServer)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState(usingFakeServer = c.usingFakeServer))

    init {
        c.navi.connect()
        resumeSession()
    }

    // ---------- 생명주기 ----------

    /** Activity onStart/onStop에서 호출. 백그라운드에서는 위치 구독·폴링을 멈춘다(가이드: 백그라운드 ≠ 종료, 자원 정리). */
    fun setForeground(value: Boolean) {
        foreground.value = value
        if (value) startHomeLocation() else stopHomeLocation()
    }

    fun onPermissionResult() = startHomeLocation()

    private fun resumeSession() {
        val s = c.tripStore.session.value ?: return
        Log.i(TAG, "resume session ${s.tripId} phase=${s.phase}")
        when (s.phase) {
            TripPhase.RECORDING -> TripRecordingService.start(getApplication()) // 프로세스 재시작 후 기록 재개
            TripPhase.FINISHING -> runFinish()
            TripPhase.ARRIVED, TripPhase.DONE -> Unit
        }
    }

    // ---------- 홈: 현재 위치 ----------

    private fun startHomeLocation() {
        if (!foreground.value || c.tripStore.session.value != null) return
        if (!c.location.hasPermission()) {
            location.value = LocationState.NoPermission
            return
        }
        if (!c.location.isEnabled()) {
            location.value = LocationState.Disabled
            return
        }
        c.location.lastKnown()?.let { p -> viewModelScope.launch { location.value = LocationState.Ready(p, label(p)) } }
        if (locationJob?.isActive == true) return
        locationJob = viewModelScope.launch {
            c.location.updates(intervalMs = 5_000L).collect { p -> location.value = LocationState.Ready(p, label(p)) }
        }
    }

    private fun stopHomeLocation() {
        locationJob?.cancel()
        locationJob = null
    }

    /** 위치 이름. 기기에 Geocoder가 있으면 동 이름, 없으면(에뮬레이터·GMS 없음) "현재 위치". 1km 이내 재사용. */
    private suspend fun label(p: TrackPoint): String {
        labelCache?.let { (at, name) -> if (Geo.distanceMeters(at, p.latLng) < 1_000) return name }
        val name = withContext(Dispatchers.IO) {
            runCatching {
                if (!Geocoder.isPresent()) return@runCatching null
                @Suppress("DEPRECATION")
                Geocoder(getApplication(), Locale.KOREA).getFromLocation(p.lat, p.lng, 1)?.firstOrNull()
                    ?.let { a -> listOfNotNull(a.locality ?: a.subAdminArea, a.subLocality ?: a.thoroughfare).joinToString(" ") }
                    ?.takeIf { it.isNotBlank() }
            }.getOrNull()
        } ?: "현재 위치"
        labelCache = p.latLng to name
        return name
    }

    // ---------- 추첨 (F-01) ----------

    fun draw(radiusKm: Int, reroll: Boolean = false) {
        val loc = location.value as? LocationState.Ready ?: run {
            message.value = "현재 위치를 확인한 뒤 다시 시도해 주세요"
            return
        }
        message.value = null
        val seen = (preTrip.value as? PreTrip.Result)?.let { if (reroll) it.seenPlaceIds + it.destination.placeId else emptySet() }
            ?: emptySet()
        preTrip.value = PreTrip.Drawing(radiusKm)
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            val result = runCatching {
                // 실서버 계약에는 제외 목록이 없어 같은 곳이 다시 나올 수 있다 → 최대 3번까지 다시 요청.
                var r = c.api.randomDestination(loc.point.lat, loc.point.lng, radiusKm, demo = c.demoMode, excludePlaceIds = seen)
                var tries = 1
                while (r.destination.placeId in seen && tries < MAX_REROLL_TRIES) {
                    r = c.api.randomDestination(loc.point.lat, loc.point.lng, radiusKm, demo = c.demoMode, excludePlaceIds = seen)
                    tries++
                }
                r
            }
            // 추첨 연출은 최소 1.2초 (너무 짧으면 "랜덤" 느낌이 안 남, 길면 지루함)
            delay((MIN_DRAW_MS - (System.currentTimeMillis() - started)).coerceAtLeast(0))
            result.onSuccess { r ->
                preTrip.value = PreTrip.Result(r.tripId, r.destination, loc.point.latLng, loc.label, radiusKm, seenPlaceIds = seen)
                queryNaviTrip(r.destination)
            }.onFailure { e ->
                Log.w(TAG, "randomDestination failed", e)
                preTrip.value = PreTrip.Home
                message.value = when {
                    e is ApiException && e.code == ApiException.CODE_NO_CANDIDATE -> e.message
                    e is ApiException && e.code == ApiException.CODE_NETWORK -> "인터넷 연결을 확인해 주세요"
                    else -> "목적지를 불러오지 못했어요. 다시 시도해 주세요"
                }
            }
        }
    }

    private fun queryNaviTrip(d: Destination) {
        viewModelScope.launch {
            val trip = c.navi.queryTrip(d)
            preTrip.update { p ->
                if (p is PreTrip.Result && p.destination.placeId == d.placeId) {
                    p.copy(naviTrip = trip?.let { NaviTripInfo(it.distanceM, it.etaS) }, naviTripLoading = false)
                } else p
            }
        }
    }

    fun backToHome() {
        preTrip.value = PreTrip.Home
    }

    // ---------- 내비 전달 (F-02) ----------

    fun startNavigation() {
        val r = preTrip.value as? PreTrip.Result ?: return
        if (r.sending) return
        // SDK 오류 코드(너무 가까움/거리 초과)가 에뮬레이터에서 재현되지 않아 앱에서 먼저 검사한다.
        val km = Geo.distanceMeters(r.origin, r.destination.latLng) / 1000.0
        val precheck = when {
            km < MIN_ROUTE_KM -> "목적지가 너무 가까워요. 다시 뽑아 주세요"
            km > r.radiusKm * 1.2 -> "선택한 반경보다 먼 목적지예요. 다시 뽑아 주세요"
            c.navi.connection.value == NaviConnection.FAILED -> "내비게이션에 연결할 수 없어요"
            else -> null
        }
        if (precheck != null) {
            preTrip.value = r.copy(sendError = precheck)
            return
        }
        preTrip.value = r.copy(sending = true, sendError = null)
        val now = System.currentTimeMillis()
        // 위치 FGS는 화면이 보일 때만 시작 가능 → 내비가 화면을 가져가기 전에 세션·서비스를 먼저 시작하고, 실패하면 되돌린다.
        c.tripStore.start(
            TripSession(
                tripId = r.tripId, destination = r.destination, origin = r.origin, originLabel = r.originLabel,
                radiusKm = r.radiusKm, phase = TripPhase.RECORDING, startedAt = now,
            ),
        )
        TripRecordingService.start(getApplication())
        stopHomeLocation()
        viewModelScope.launch {
            when (val res = c.navi.requestRoute(r.destination)) {
                is RouteResult.Started -> {
                    c.tripStore.addEvent(
                        TripEvent(TripEventType.DESTINATION_SET, System.currentTimeMillis(), r.origin.lat, r.origin.lng, r.destination.name),
                    )
                    preTrip.value = PreTrip.Home
                    location.value = LocationState.Loading // 여행 후 홈으로 돌아오면 출발지를 새로 확인
                    Log.i(TAG, "route started ${res.distanceM}m ${res.durationS}s")
                }
                is RouteResult.Failed -> {
                    c.tripStore.clear() // 기록 서비스는 세션이 사라진 것을 보고 스스로 멈춘다
                    startHomeLocation()
                    preTrip.value = r.copy(sending = false, sendError = res.reason.message())
                }
            }
        }
    }

    private fun RouteFailure.message() = when (this) {
        RouteFailure.NOT_CONNECTED -> "내비게이션에 연결되지 않았어요. 잠시 후 다시 시도해 주세요"
        RouteFailure.TOO_SHORT -> "목적지가 너무 가까워요. 다시 뽑아 주세요"
        RouteFailure.TOO_FAR -> "경로 안내 가능 거리를 넘었어요. 다시 뽑아 주세요"
        RouteFailure.SEARCH_FAILED -> "경로를 찾지 못했어요. 다시 뽑아 주세요"
        RouteFailure.LOW_BATTERY -> "배터리가 부족해 이 목적지로 안내할 수 없어요"
        RouteFailure.TIMEOUT -> "내비게이션 응답이 없어요. 다시 시도해 주세요"
        RouteFailure.EXCEPTION -> "목적지를 전달하지 못했어요. 다시 시도해 주세요"
    }

    fun retryNaviConnect() = c.navi.connect()

    fun showNavigation() = c.navi.showNavigation()

    // ---------- 여행 종료 → 리캡 (F-07) ----------

    fun endTrip() {
        val s = c.tripStore.session.value ?: return
        if (s.phase != TripPhase.RECORDING && s.phase != TripPhase.ARRIVED) return
        // 단계가 FINISHING으로 바뀌면 기록 서비스가 스스로 멈춘다.
        c.tripStore.update { it.copy(phase = TripPhase.FINISHING, endedAt = it.endedAt ?: System.currentTimeMillis()) }
        runFinish()
    }

    /** 기록을 버리고 처음으로(리캡 만들지 않음). 내비 안내는 건드리지 않는다. */
    fun discardTrip() {
        finishJob?.cancel()
        c.tripStore.clear()
        finish.value = FinishState.Uploading
        location.value = LocationState.Loading
        preTrip.value = PreTrip.Home
        startHomeLocation()
    }

    fun retryFinish() = runFinish()

    private fun runFinish() {
        if (finishJob?.isActive == true) return
        finishJob = viewModelScope.launch {
            try {
                finishFlow()
            } catch (e: ApiException) {
                Log.w(TAG, "finish failed: ${e.code} ${e.message}")
                finish.value = FinishState.Error(
                    if (e.code == ApiException.CODE_NETWORK) "인터넷 연결을 확인해 주세요" else "서버 오류: ${e.message}",
                    // 4xx라도 기록은 기기에 남아 있으니 다시 시도는 항상 열어 둔다.
                    canRetry = true,
                )
            }
        }
    }

    private suspend fun finishFlow() {
        val s = c.tripStore.session.value ?: return
        finish.value = FinishState.Uploading
        val points = withContext(Dispatchers.IO) { buildUploadPoints(s) }
        if (points.size < 2) {
            finish.value = FinishState.Error("기록된 경로가 없어 리캡을 만들 수 없어요. 위치 권한과 GPS를 확인해 주세요", canRetry = false)
            return
        }
        val endedAt = s.endedAt ?: System.currentTimeMillis()
        c.tripStore.update { it.copy(localSummary = localSummary(s, points, endedAt)) }
        val request = FinishTripRequest(s.startedAt, endedAt, points, s.events)

        suspend fun upload() {
            finish.value = FinishState.Uploading
            withRetry { c.api.finishTrip(s.tripId, request) }
            c.tripStore.update { it.copy(finishAccepted = true) }
            finish.value = FinishState.Generating
        }
        if (c.tripStore.session.value?.finishAccepted != true) upload()

        finish.value = FinishState.Generating
        var reuploaded = false
        var polls = 0
        // 최대 대기 시간은 "화면이 보이는 동안의 조회 횟수"로 센다(백그라운드에 있던 시간은 빼고).
        while (polls < MAX_RECAP_POLLS) {
            foreground.first { it } // 백그라운드에서는 폴링 중단
            val recap = try {
                withRetry { c.api.getRecap(s.tripId) }
            } catch (e: ApiException) {
                // 서버(또는 앱 내장 가짜 서버)가 재시작돼 trip을 잃었으면 한 번은 다시 올려 본다.
                if (e.code == CODE_TRIP_NOT_FOUND && !reuploaded) {
                    reuploaded = true
                    upload()
                    continue
                }
                throw e
            }
            polls++
            when (recap.status) {
                RecapStatus.DONE -> {
                    c.tripStore.update { it.copy(phase = TripPhase.DONE, shareUrl = recap.shareUrl, summary = recap.summary) }
                    return
                }
                RecapStatus.FAILED -> {
                    // 다시 시도하면 finish를 다시 보낸다. 서버가 FAILED 상태에서 재생성을 지원해야 함(서버 작업 요청서 3-5).
                    c.tripStore.update { it.copy(finishAccepted = false) }
                    finish.value = FinishState.Error("리캡 영상을 만들지 못했어요", canRetry = true)
                    return
                }
                RecapStatus.PENDING, RecapStatus.RUNNING -> delay(POLL_INTERVAL_MS)
            }
        }
        finish.value = FinishState.Error("리캡 생성이 오래 걸리고 있어요. 잠시 후 다시 확인해 주세요", canRetry = true)
    }

    /** 재시도 가능한 오류(네트워크·5xx)는 1s, 2s 간격으로 최대 3번 시도. 마지막 오류는 그대로 던진다. */
    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var wait = 1_000L
        var attempt = 1
        while (true) {
            try {
                return block()
            } catch (e: ApiException) {
                Log.w(TAG, "api failed (attempt $attempt): ${e.code} ${e.message}")
                if (!e.retryable || attempt >= 3) throw e
                delay(wait)
                wait *= 2
                attempt++
            }
        }
    }

    private fun buildUploadPoints(s: TripSession): List<TrackPoint> {
        val recorded = c.tripStore.points()
        // 출발 지점(여행 시작 시 확인한 실제 위치)을 맨 앞에 둔다 — 기록 시작 직후 첫 GPS가 늦게 오는 경우 대비.
        val origin = TrackPoint(s.origin.lat, s.origin.lng, s.startedAt)
        val all = if (recorded.firstOrNull()?.let { it.ts <= s.startedAt } == true) recorded else listOf(origin) + recorded
        return TrackSimplifier.simplify(all.sortedBy { it.ts })
    }

    private fun localSummary(s: TripSession, points: List<TrackPoint>, endedAt: Long): RecapSummary {
        val planned = listOf(s.destination.latLng) + s.events
            .filter { it.type == TripEventType.WAYPOINT_ADDED || it.type == TripEventType.WAYPOINT_ARRIVED }
            .map { LatLng(it.lat, it.lng) }
        return RecapSummary(
            distanceKm = Geo.pathLengthMeters(points.map { it.latLng }) / 1000.0,
            durationMin = ((endedAt - s.startedAt) / 60_000L).toInt(),
            stopCount = StopDetector().detect(points, planned).size,
        )
    }

    fun newTrip() {
        finishJob?.cancel()
        c.tripStore.clear()
        finish.value = FinishState.Uploading
        location.value = LocationState.Loading
        preTrip.value = PreTrip.Home
        startHomeLocation()
    }

    fun consumeMessage() {
        message.value = null
    }

    companion object {
        private const val TAG = "MainViewModel"
        private const val MIN_DRAW_MS = 1_200L
        private const val MIN_ROUTE_KM = 0.5
        private const val POLL_INTERVAL_MS = 2_000L
        private const val MAX_RECAP_POLLS = 90 // 2초 × 90 = 화면이 보이는 동안 약 3분
        private const val MAX_REROLL_TRIES = 3
        private const val CODE_TRIP_NOT_FOUND = "TRIP_NOT_FOUND"
        val RADIUS_OPTIONS = CandidatePicker.RADIUS_OPTIONS_KM
    }
}
