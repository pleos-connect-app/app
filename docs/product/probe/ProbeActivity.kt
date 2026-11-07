package ai.probe

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.*
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView

class ProbeActivity : Activity() {
    private lateinit var navi: NaviHelper
    private lateinit var fused: FusedLocation
    private lateinit var tv: TextView
    private fun log(s: String) { Log.i("PROBE", s); runOnUiThread { tv.append("$s\n") } }

    private val naviListener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) = log("EV onRouteStarted $routeStartInfo")
        override fun onRouteEnded() = log("EV onRouteEnded")
        override fun onRoutingCompleted() = log("EV onRoutingCompleted")
        override fun onRouteCancelled() = log("EV onRouteCancelled")
        override fun onDestinationChanged() = log("EV onDestinationChanged")
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) = log("EV onDestinationArrived $destinationArrivedInfo")
        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) = log("EV onWaypointChanged $waypointChangedInfo")
        override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) = log("EV onWayPointArrived $waypointArrivedInfo")
        override fun onError(errorCode: NaviErrorCode) = log("EV onError $errorCode")
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) = log("EV onCurrentLocationInfo $currentLocationInfo")
        override fun onDestinationInfo(destinationInfo: DestinationInfo) = log("EV onDestinationInfo $destinationInfo")
        override fun onNavigationStatus(navigationStatus: NavigationStatus) = log("EV onNavigationStatus $navigationStatus")
        override fun onDrivingInfo(drivingInfo: DrivingInfo) = log("EV onDrivingInfo $drivingInfo")
        override fun onTripInfo(tripInfo: TripInfo) = log("EV onTripInfo dist=${tripInfo.distance} eta=${tripInfo.eta} points=${tripInfo.summary?.routePoints?.size}")
        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) = log("EV onRouteStateInfo $routeStateInfo")
    }
    private val fusedListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) = log("FUSED onChanged $location")
        override fun onError(exception: Exception) = log("FUSED onError $exception")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tv = TextView(this).apply { textSize = 14f }
        setContentView(tv)
        navi = NaviHelper(applicationContext)
        navi.initialize(onConnected = { log("NAVI connected"); navi.addListener(naviListener) },
            onFailed = { t -> log("NAVI failed $t") })
        fused = FusedLocation(applicationContext)
        val sup = runCatching { fused.isSupported() }
        log("FUSED isSupported=$sup")
        if (sup.getOrNull() == true) runCatching { fused.initialize() }.onFailure { log("FUSED init err $it") }
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handle(intent) }

    private fun handle(i: Intent?) {
        val cmd = i?.getStringExtra("cmd") ?: return
        log("CMD $cmd")
        runCatching {
            when (cmd) {
                "route" -> navi.requestRoute(RouteInfo(latitude = 37.4786, longitude = 127.1817, displayLongitude = null, displayLatitude = null,
                    poiName = "남한산성", poiId = "", poiSubId = "0", address = "경기도 광주시 남한산성면"))
                "near" -> navi.requestRoute(RouteInfo(latitude = 37.4128, longitude = 127.0948, displayLongitude = null, displayLatitude = null,
                    poiName = "too near", poiId = "", poiSubId = "0", address = ""))
                "far" -> navi.requestRoute(RouteInfo(latitude = 35.1796, longitude = 129.0756, displayLongitude = null, displayLatitude = null,
                    poiName = "부산", poiId = "", poiSubId = "0", address = ""))
                "trip" -> navi.requestTrip(TripRequest(latitude = 37.4786, longitude = 127.1817, poiId = "", poiName = "남한산성", poiSubId = "0"))
                "fg" -> navi.requestNavigationScreen(NavigationScreenRequest.Foreground)
                "dest" -> navi.getDestinationInfo()
                "cur" -> navi.getCurrentLocationInfo()
                "state" -> navi.getRouteStateInfo()
                "cancel" -> navi.cancelRoute()
                "last" -> fused.getLastKnownLocation(fusedListener)
                "fused" -> fused.registerFusedLocationCallback(fusedListener)
                "unfused" -> fused.unregisterFusedLocationCallback(fusedListener)
                "blocked" -> fused.showLocationAccessBlockedPopup()
            }
        }.onFailure { log("CMD $cmd threw $it") }
    }

    override fun onDestroy() { runCatching { navi.removeListener(naviListener); navi.release(); fused.release() }; super.onDestroy() }
}
