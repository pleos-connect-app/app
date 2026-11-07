<!-- source: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/routes -->

# Routes

`NaviHelper` API로 경로 옵션을 변경하고 재탐색을 요청합니다. 결과는 `NaviHelperEventListener` 콜백으로 확인합니다. 목적지 설정은 [Destinations](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations)를 참조합니다.

## 경로 옵션 변경

`RouteOption`은 다음 값을 제공합니다.

| Value | Description |
| --- | --- |
| `RECOMMENDED` | 추천 경로 |
| `SHORT_DISTANCE` | 최단 거리 경로 |
| `SHORT_TIME` | 최단 시간 경로 |
| `FREE` | 무료 도로 우선 경로 |
| `HIGHWAY` | 고속도로 우선 경로 |
| `COMFORTABLE` | 편안한 경로 |
| `SCHOOL_ZONE` | 스쿨존 고려 경로 |

목적지를 처음 설정할 때 `RouteInfo.routeOption`으로 지정하거나, 안내 중 `changeRouteOption()`으로 변경할 수 있습니다.

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

// 경로옵션: 고속우선으로 변경

naviHelper.changeRouteOption(RouteOption.HIGHWAY)

// onRouteOptionChanged callback으로 변경 데이터 수신

override fun onRouteOptionChanged(routeOption: RouteOption) {

Log.d(logTag, "onRouteOptionChanged $routeOption")

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

// 경로옵션: 고속우선으로 변경

naviHelper.changeRouteOption(RouteOption.HIGHWAY);

// onRouteOptionChanged callback으로 변경 데이터 수신

@Override

public void onRouteOptionChanged(@NonNull RouteOption routeOption) {

Log.d(logTag, "onRouteOptionChanged = " + routeOption);

}

// 목적지 취소

naviHelper.cancelRoute();
```

## 경로 재탐색

현재 위치와 목적지를 기준으로 경로 재탐색을 요청하려면 `requestReRoute()`를 호출합니다.

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

// 경로 재탐색 요청

naviHelper.requestReRoute()

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

// 경로 재탐색 요청

naviHelper.requestReRoute();

// 목적지 취소

naviHelper.cancelRoute();
```

정보

경로 옵션 변경과 재탐색은 활성 경로가 있는 상태에서 요청합니다. 처리 결과와 실제로 전달되는 콜백은 Navigation 앱의 상태에 따라 달라질 수 있습니다.
