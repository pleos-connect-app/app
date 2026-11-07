<!-- source: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/location-data-access -->

# 위치 데이터 접근

## 마지막 위치 1회 조회

`getLastKnownLocation()`은 현재 사용할 수 있는 마지막 위치를 콜백으로 전달합니다. 새 위치를 계산하는 API가 아니기 때문에 계산해 놓은 위치 데이터가 없을 경우 `LocationData?`가 `null`일 수 있습니다.

## 위치 업데이트 수신

`registerFusedLocationCallback()`에 `FusedLocationListener`를 전달하면 위치가 변경될 때 `onChanged(LocationData?)`가 호출됩니다. 오류가 발생하면 `onError(Exception)`이 호출됩니다.

```
val listener = object : FusedLocationListener {

override fun onChanged(location: LocationData?) {

location ?: return

// Use the location data.

}

override fun onError(exception: Exception) {

// Handle the error.

}

}

fusedLocation.registerFusedLocationCallback(listener)
```

## 위치 업데이트 중지

더 이상 업데이트가 필요하지 않으면 등록할 때 사용한 동일한 리스너를 `unregisterFusedLocationCallback()`에 전달합니다.

```
fusedLocation.unregisterFusedLocationCallback(listener)
```

각 API의 요구 권한은 [권한 선언](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/declaring-permissions)을 참고하세요.
