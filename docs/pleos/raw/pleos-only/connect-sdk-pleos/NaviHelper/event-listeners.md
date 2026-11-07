<!-- source: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/event-listeners -->

# Event listeners

`NaviHelperEventListener`를 등록하면 API 요청 결과와 내비게이션 상태 변화를 받을 수 있습니다.

## 이벤트 종류

| Category | Callback |
| --- | --- |
| 경로 탐색 | `onRouteStarted()`, `onRoutingCompleted()`, `onRouteCancelled()`, `onReRouteEnded()`, `onRouteOptionChanged()` |
| 목적지와 경유지 | `onDestinationChanged()`, `onDestinationArrived()`, `onWaypointChanged()`, `onWayPointArrived()` |
| 정보 조회 | `onBookmarkInfo()`, `onBookMarkChanged()`, `onRecentDestinationInfo()`, `onCurrentLocationInfo()`, `onDestinationInfo()`, `onWayPointInfo()`, `onRouteStateInfo()`, `onTBTInfo()`, `onChargerOperatorInfo()`, `onTripInfo()`, `onNavigationStatus()`, `onDrivingInfo()`, `onCurrentRoadSpeedLimit()`, `onCameraAlert()`, `onCautionPointInfo()` |
| 오류 | `onError()` |

Navigation 앱의 구현 시나리오에 따라 전달되는 이벤트가 달라질 수 있습니다.

## Add a listener

필요한 콜백만 재정의하고, SDK 초기화 후 리스너를 등록합니다.

- Kotlin
- Java

```
import ai.pleos.playground.navi.constants.NaviErrorCode

import ai.pleos.playground.navi.data.RouteStartInfo

import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener

private val naviEventListener = object : NaviHelperEventListener {

override fun onRouteStarted(routeStartInfo: RouteStartInfo) {

// 경로 탐색 시작을 처리합니다.

}

override fun onRoutingCompleted() {

// 경로 탐색 완료를 처리합니다.

}

override fun onError(errorCode: NaviErrorCode) {

// 내비게이션 오류를 처리합니다.

}

}

naviHelper.addListener(naviEventListener)
```

```
private final NaviHelperEventListener naviEventListener =

new NaviHelperEventListener() {

@Override

public void onRouteStarted(RouteStartInfo routeStartInfo) {

// 경로 탐색 시작을 처리합니다.

}

@Override

public void onRoutingCompleted() {

// 경로 탐색 완료를 처리합니다.

}

@Override

public void onError(NaviErrorCode errorCode) {

// 내비게이션 오류를 처리합니다.

}

};

naviHelper.addListener(naviEventListener);
```

## Remove a listener

리스너가 더 이상 필요하지 않거나 SDK를 해제하기 전에, 등록할 때 사용한 동일한 리스너 인스턴스를 제거합니다.

```
naviHelper.removeListener(naviEventListener)
```

오류별 처리 방법은 [Error handling](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/error-handling)을 참조합니다.
