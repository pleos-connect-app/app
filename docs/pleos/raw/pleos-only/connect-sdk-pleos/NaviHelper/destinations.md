<!-- source: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations -->

# Destinations

목적지 설정·확인·취소 방법을 안내합니다. 결과는 `NaviHelperEventListener` 콜백으로 확인합니다.

## 목적지 설정, 확인 및 취소

`requestRoute()`로 목적지를 설정하고, `getDestinationInfo()`로 확인한 뒤, `cancelRoute()`로 취소합니다. 목적지를 다시 설정할 때도 `requestRoute()`를 호출합니다.

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

// 목적지 확인

naviHelper.getDestinationInfo()

// onDestinationInfo callback으로 데이터 수신

override fun onDestinationInfo(destinationInfo: DestinationInfo) {

Log.d(logTag, "onDestinationInfo $destinationInfo")

}

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

// 목적지 확인

naviHelper.getDestinationInfo();

// onDestinationInfo callback으로 데이터 수신

@Override

public void onDestinationInfo(DestinationInfo destinationInfo) {

Log.d(logTag, "onDestinationInfo = " + destinationInfo);

}

// 목적지 취소

naviHelper.cancelRoute();
```

경로 탐색 과정은 다음 콜백으로 확인할 수 있습니다.

| Callback | Description |
| --- | --- |
| `onRouteStarted(routeStartInfo)` | Navigation 앱이 경로 탐색을 시작하면 호출됩니다. |
| `onRoutingCompleted()` | 경로 탐색이 완료되면 호출됩니다. |
| `onDestinationChanged()` | 목적지가 설정되거나 제거되면 호출됩니다. |
| `onError(errorCode)` | 경로 요청을 처리하는 중 오류가 발생하면 호출됩니다. |

정보

`requestRoute()` 호출이 정상적으로 수행되더라도 경로 탐색 완료를 의미하지 않습니다. 최종 결과는 이벤트 콜백으로 확인합니다. 정보 조회 API는 값을 직접 반환하지 않으며, 대응하는 이벤트 콜백에서 결과를 처리합니다.

경로 옵션 변경과 재탐색은 [Routes](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/routes)를 참조합니다.
