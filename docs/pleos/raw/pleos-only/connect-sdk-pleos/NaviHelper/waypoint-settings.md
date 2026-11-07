<!-- source: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/waypoint-settings -->

# Waypoints

경유지 추가·조회·제거 방법을 안내합니다. 결과는 `NaviHelperEventListener` 콜백으로 확인합니다.

## 경유지 추가 및 제거

경유지는 최대 두 개까지 지정할 수 있으며, `WaypointIndex.FIRST`와 `WaypointIndex.SECOND`로 구분합니다. `addWaypoint()`로 경유지를 추가하고, `removeWaypoint()`로 삭제합니다.

- Kotlin
- Java

```
// 목적지 설정

naviHelper.requestRoute(

RouteInfo(

latitude = 37.48544722,

longitude = 127.03636666,

displayLongitude = null,

displayLatitude = null,

poiName = "42dot",

poiId = "36khp37hh8gqcm5nqhe5",

poiSubId = "0",

address = "서울시 강남구 남부순환로 2621"

)

)

// 경유지 추가

naviHelper.addWaypoint(

RequestWaypointInfo(

latitude = 37.41275833,

longitude = 127.09481944,

waypointIndex = WaypointIndex.FIRST,

poiId = "36khp2hdj2cfcm5nqhe3",

poiName = "소프트웨어드림센터",

poiSubId = "0",

address = "경기도 성남시 수정구 창업로40번길 6"

)

)

// onWaypointInfo callback으로 데이터 수신

override fun onWaypointInfo(waypointInfo: List<WaypointInfo>) {

Log.d(logTag, "onWaypointInfo $waypointInfo")

}

// 경유지 삭제

naviHelper.removeWaypoint(WaypointIndex.FIRST)

// 목적지 취소

naviHelper.cancelRoute()
```

```
// 목적지 설정

naviHelper.requestRoute(new RouteInfo(

127.03636666,

37.48544722,

null,

null,

"42dot",

"36khp37hh8gqcm5nqhe5",

"서울시 강남구 남부순환로 2621",

"0",

RouteOption.RECOMMENDED

));

// 경유지 추가

naviHelper.addWaypoint(new RequestWaypointInfo(

127.09481944,

37.41275833,

"소프트웨어드림센터",

WaypointIndex.FIRST,

"36khp2hdj2cfcm5nqhe3",

"경기도 성남시 수정구 창업로40번길 6",

"0"

));

// onWaypointInfo callback으로 데이터 수신

@Override

public void onWayPointInfo(List<WaypointInfo> list) {

Log.d(logTag, "onWayPointInfo = " + list);

}

// 경유지 삭제

naviHelper.removeWaypoint(WaypointIndex.FIRST);

// 목적지 취소

naviHelper.cancelRoute();
```

현재 경유지 목록을 요청하려면 `getWaypointInfo()`를 호출합니다. 결과는 `onWayPointInfo()`로 전달됩니다. 모든 경유지를 제거하려면 `removeWaypoint(WaypointIndex.ALL)`을 사용합니다.

```
naviHelper.getWaypointInfo()

// 모든 경유지 제거

naviHelper.removeWaypoint(WaypointIndex.ALL)
```

경유지 정보가 조회되면 onWayPointInfo가 호출될 수 있습니다. 경유지에 도착하면 `onWayPointArrived()`가 호출될 수 있습니다.
