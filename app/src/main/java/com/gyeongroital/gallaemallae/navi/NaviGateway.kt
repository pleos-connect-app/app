package com.gyeongroital.gallaemallae.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.WaypointCmdType
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import com.gyeongroital.gallaemallae.domain.Destination
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull

/** 앱이 신경 쓰는 내비 이벤트만 추린 것. */
sealed interface NaviEvent {
    data class RouteStarted(val name: String, val distanceM: Int, val durationS: Int) : NaviEvent
    data object RouteCancelled : NaviEvent
    data class DestinationArrived(val name: String) : NaviEvent
    data class WaypointAdded(val name: String?) : NaviEvent
    /** getWaypointInfo() 응답. 경유지 좌표는 이 이벤트로만 알 수 있다(WaypointChanged/Arrived에는 좌표 없음). */
    data class Waypoints(val items: List<WaypointPoint>) : NaviEvent
    data class WaypointArrived(val name: String) : NaviEvent
    data class Error(val code: NaviErrorCode) : NaviEvent
    data class Trip(val distanceM: Int, val etaS: Int) : NaviEvent
}

data class WaypointPoint(val name: String, val lat: Double, val lng: Double)

enum class NaviConnection { CONNECTING, CONNECTED, FAILED }

sealed interface RouteResult {
    data class Started(val distanceM: Int, val durationS: Int) : RouteResult
    data class Failed(val reason: RouteFailure) : RouteResult
}

enum class RouteFailure { NOT_CONNECTED, TOO_SHORT, TOO_FAR, SEARCH_FAILED, LOW_BATTERY, TIMEOUT, EXCEPTION }

/**
 * Pleos NaviHelper 래퍼 (MVP F-02/F-03). 앱 전체에서 하나만 쓴다.
 * - 연결 완료 전에는 경로 요청을 하지 않는다.
 * - SDK 콜백을 Flow로 바꿔, 화면(ViewModel)과 기록 서비스가 각각 구독한다.
 */
class NaviGateway(context: Context) {
    private val navi = NaviHelper(context.applicationContext)

    private val _connection = MutableStateFlow(NaviConnection.CONNECTING)
    val connection: StateFlow<NaviConnection> = _connection.asStateFlow()

    private val _events = MutableSharedFlow<NaviEvent>(extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<NaviEvent> = _events.asSharedFlow()

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) =
            emit(NaviEvent.RouteStarted(routeStartInfo.name, routeStartInfo.distance, routeStartInfo.duration))
        override fun onRouteCancelled() = emit(NaviEvent.RouteCancelled)
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) =
            emit(NaviEvent.DestinationArrived(destinationArrivedInfo.name))
        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) {
            if (waypointChangedInfo.cmdType == WaypointCmdType.ADD) {
                emit(NaviEvent.WaypointAdded(waypointChangedInfo.routeStartInfo?.name))
            }
        }
        override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) =
            emit(NaviEvent.WaypointArrived(waypointArrivedInfo.name))
        override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) =
            emit(NaviEvent.Waypoints(waypointInfoList.map { WaypointPoint(it.name, it.latitude, it.longitude) }))
        override fun onError(errorCode: NaviErrorCode) = emit(NaviEvent.Error(errorCode))
        override fun onTripInfo(tripInfo: TripInfo) = emit(NaviEvent.Trip(tripInfo.distance, tripInfo.eta))

        // 아래 콜백은 v0에서 쓰지 않는다.
        override fun onRouteEnded() = Unit // 경로 탐색 직후에도 오므로 "여행 종료"로 쓰면 안 됨(실측)
        override fun onRoutingCompleted() = Unit
        override fun onDestinationChanged() = Unit
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) = Unit
        override fun onDestinationInfo(destinationInfo: DestinationInfo) = Unit
        override fun onNavigationStatus(navigationStatus: NavigationStatus) = Unit
        override fun onDrivingInfo(drivingInfo: DrivingInfo) = Unit
        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) = Unit
    }

    private fun emit(e: NaviEvent) {
        Log.i(TAG, "event $e")
        _events.tryEmit(e)
    }

    fun connect() {
        if (_connection.value == NaviConnection.CONNECTED) return
        _connection.value = NaviConnection.CONNECTING
        runCatching {
            navi.initialize(
                onConnected = {
                    Log.i(TAG, "connected")
                    runCatching { navi.addListener(listener) }
                    _connection.value = NaviConnection.CONNECTED
                },
                onFailed = { t ->
                    Log.w(TAG, "connect failed", t)
                    _connection.value = NaviConnection.FAILED
                },
            )
        }.onFailure {
            Log.w(TAG, "initialize threw", it)
            _connection.value = NaviConnection.FAILED
        }
    }

    private suspend fun awaitConnected(timeoutMs: Long): Boolean =
        withTimeoutOrNull(timeoutMs) { connection.first { it != NaviConnection.CONNECTING } } == NaviConnection.CONNECTED

    /** 안내를 시작하지 않고 내비 기준 도로 거리·ETA만 조회. 실패하면 null. */
    suspend fun queryTrip(d: Destination, timeoutMs: Long = 5_000): NaviEvent.Trip? {
        if (!awaitConnected(timeoutMs)) return null
        return withTimeoutOrNull(timeoutMs) {
            // 요청 전에 먼저 구독해야 빠른 응답을 놓치지 않는다(UNDISPATCHED).
            val waiter = async(start = CoroutineStart.UNDISPATCHED) {
                events.mapNotNull { e ->
                    when {
                        e is NaviEvent.Trip -> e
                        e is NaviEvent.Error && e.code == NaviErrorCode.CAUSE_SEARCH_TRIP -> TRIP_FAILED
                        else -> null
                    }
                }.first()
            }
            val sent = runCatching {
                navi.requestTrip(TripRequest(latitude = d.lat, longitude = d.lng, poiId = "", poiName = d.name, poiSubId = "0"))
            }
            if (sent.isFailure) {
                waiter.cancel()
                null
            } else {
                waiter.await().takeIf { it !== TRIP_FAILED }
            }
        }
    }

    /**
     * 목적지 전달. onRouteStarted 또는 onError를 기다린다.
     * 에뮬레이터 실측: requestRoute → onRouteStarted까지 약 7초(내비 trip 생성 포함) → 여유 있게 20초.
     */
    suspend fun requestRoute(d: Destination, timeoutMs: Long = 20_000): RouteResult {
        if (!awaitConnected(3_000)) return RouteResult.Failed(RouteFailure.NOT_CONNECTED)
        val outcome = withTimeoutOrNull(timeoutMs) {
            val waiter = async(start = CoroutineStart.UNDISPATCHED) {
                events.mapNotNull { e ->
                    when (e) {
                        is NaviEvent.RouteStarted -> RouteResult.Started(e.distanceM, e.durationS)
                        is NaviEvent.Error -> e.code.toRouteResult()
                        else -> null
                    }
                }.first()
            }
            val sent = runCatching {
                navi.requestRoute(
                    RouteInfo(latitude = d.lat, longitude = d.lng, displayLongitude = null, displayLatitude = null,
                        poiName = d.name, poiId = "", poiSubId = "0", address = d.address),
                )
            }
            if (sent.isFailure) {
                Log.w(TAG, "requestRoute threw", sent.exceptionOrNull())
                waiter.cancel()
                RouteResult.Failed(RouteFailure.EXCEPTION)
            } else {
                waiter.await()
            }
        }
        if (outcome == null) {
            // 시간 초과 뒤 늦게 안내가 시작되면 기록 없이 안내만 도는 상태가 되므로 요청을 취소해 둔다.
            cancelRoute()
            return RouteResult.Failed(RouteFailure.TIMEOUT)
        }
        return outcome
    }

    private fun NaviErrorCode.toRouteResult(): RouteResult? = when (this) {
        NaviErrorCode.CAUSE_SEARCH_ROUTE_TOO_SHORT_DISTANCE -> RouteResult.Failed(RouteFailure.TOO_SHORT)
        NaviErrorCode.CAUSE_SEARCH_ROUTE_OVER_DISTANCE -> RouteResult.Failed(RouteFailure.TOO_FAR)
        NaviErrorCode.CAUSE_SEARCH_ROUTE -> RouteResult.Failed(RouteFailure.SEARCH_FAILED)
        NaviErrorCode.CAUSE_LOW_BATTERY -> RouteResult.Failed(RouteFailure.LOW_BATTERY)
        NaviErrorCode.CAUSE_SEARCH_TRIP -> null // requestTrip 실패는 경로 요청과 무관
    }

    /** 내비 화면을 전면으로. */
    fun showNavigation() {
        runCatching { navi.requestNavigationScreen(NavigationScreenRequest.Foreground) }
            .onFailure { Log.w(TAG, "requestNavigationScreen failed", it) }
    }

    /** 경유지 목록(좌표 포함) 요청 → NaviEvent.Waypoints로 응답. */
    fun requestWaypoints() {
        runCatching { navi.getWaypointInfo() }.onFailure { Log.w(TAG, "getWaypointInfo failed", it) }
    }

    fun cancelRoute() {
        runCatching { navi.cancelRoute() }.onFailure { Log.w(TAG, "cancelRoute failed", it) }
    }

    companion object {
        private const val TAG = "NaviGateway"
        private val TRIP_FAILED = NaviEvent.Trip(-1, -1)
    }
}
