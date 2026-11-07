<!-- source: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/route-with-waypoints -->

# Route requests with waypoints

경유지를 포함한 경로 요청 방법을 안내합니다. `requestRouteWithWaypoints()`는 최종 목적지와 하나 이상의 경유지를 포함한 경로를 요청합니다. 목적지는 `RouteInfo`, 경유지는 `RequestWaypointInfo`로 구성하고, 두 값을 `RouteWithWaypointsRequest`에 전달합니다.

## 경유지를 포함한 경로 요청

- Kotlin
- Java

```
naviHelper.requestRouteWithWaypoints(

RouteWithWaypointsRequest(

destination = RouteInfo(

latitude = 37.48544722,

longitude = 127.03636666,

poiName = "42dot",

poiId = "36khp37hh8gqcm5nqhe5",

address = "서울시 강남구 남부순환로 2621",

poiSubId = "0"

),

waypoints = listOf(

RequestWaypointInfo(

latitude = 37.41275833,

longitude = 127.09481944,

poiName = "판교 현대백화점",

waypointIndex = WaypointIndex.FIRST,

poiId = "36khp2hdj2cfcm5nqhe3",

address = "경기도 성남시 수정구 창업로 0번길 6",

poiSubId = "0"

)

)

)

)
```

```
import java.util.Arrays;

naviHelper.requestRouteWithWaypoints(new RouteWithWaypointsRequest(

new RouteInfo(

37.48544722,

127.03636666,

null,

null,

"42dot",

"36khp37hh8gqcm5nqhe5",

"서울시 강남구 남부순환로 2621",

"0"

),

Arrays.asList(new RequestWaypointInfo(

37.41275833,

127.09481944,

"판교 현대백화점",

WaypointIndex.FIRST,

"36khp2hdj2cfcm5nqhe3",

"경기도 성남시 수정구 창업로 0번길 6",

"0"

))

));
```

`requestRouteWithWaypoints()`에는 `pleos.car.permission.NAVI_ROUTE_SEARCH` 권한이 필요합니다. 경로 요청의 처리 결과는 등록된 `NaviHelperEventListener`의 `onRouteStarted`, `onRoutingCompleted`, `onError` 등의 이벤트로 확인합니다.

관련 API: [`requestRouteWithWaypoints`](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/request-route-with-waypoints), [`RouteWithWaypointsRequest`](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-with-waypoints-request/-route-with-waypoints-request), [`RouteInfo`](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-info/-route-info), [`RequestWaypointInfo`](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/request-waypoint-info/-request-waypoint-info)
