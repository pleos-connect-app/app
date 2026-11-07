<!-- source: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/error-handling -->

# Error handling

NaviHelper에서는 초기 서비스 연결 실패와 Navigation 앱의 요청 처리 오류가 서로 다른 경로로 전달됩니다.

## 초기화 에러

초기 연결 결과가 필요하면 `initialize()`에 콜백을 전달합니다.

```
naviHelper.initialize(

onConnected = {

// 리스너를 등록하고 API를 호출합니다.

},

onFailed = { throwable ->

// 연결 실패 원인을 기록하거나 사용자에게 안내합니다.

}

)
```

## 내비게이션 에러

Navigation 앱에서 발생한 오류는 `NaviHelperEventListener.onError()`로 전달됩니다.

```
override fun onError(errorCode: NaviErrorCode) {

when (errorCode) {

NaviErrorCode.CAUSE_LOW_BATTERY -> {

// 배터리 부족 상태를 안내합니다.

}

NaviErrorCode.CAUSE_SEARCH_ROUTE_OVER_DISTANCE -> {

// 탐색 가능 거리를 초과했음을 안내합니다.

}

NaviErrorCode.CAUSE_SEARCH_ROUTE_TOO_SHORT_DISTANCE -> {

// 목적지가 현재 위치와 너무 가까움을 안내합니다.

}

NaviErrorCode.CAUSE_SEARCH_ROUTE -> {

// 경로 탐색 서버 오류를 처리합니다.

}

}

}
```

## Error codes

| Error code | Description |
| --- | --- |
| `CAUSE_LOW_BATTERY` | 배터리가 부족합니다. |
| `CAUSE_SEARCH_ROUTE_OVER_DISTANCE` | 경로 탐색 가능 거리를 초과했습니다. |
| `CAUSE_SEARCH_ROUTE_TOO_SHORT_DISTANCE` | 현재 위치와 목적지 사이의 거리가 너무 짧습니다. |
| `CAUSE_SEARCH_ROUTE` | 경로 탐색 서버에서 오류가 발생했습니다. |

오류가 발생하면 다음 항목도 확인합니다.

- SDK 연결이 완료된 후 API를 호출했는지
- 사용하는 API에 필요한 퍼미션을 선언했는지
- 좌표, POI ID 및 경유지 인덱스가 올바른지
- 현재 Navigation 앱 상태에서 요청을 처리할 수 있는지

오류 코드의 최신 목록은 [NaviHelper API Reference](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/entry)에서 확인합니다.
