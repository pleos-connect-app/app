<!-- source: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions -->

# Declaring permissions

NaviHelper API는 기능에 따라 Android 퍼미션을 요구합니다. 사용하는 API에 해당하는 퍼미션을 앱의 `AndroidManifest.xml`에 선언합니다.

## 퍼미션 종류

| Permission | 주요 API |
| --- | --- |
| `pleos.car.permission.NAVI_ROUTE` | `get*Info()`, `requestTrip()` |
| `pleos.car.permission.NAVI_ROUTE_SEARCH` | `requestRoute()`, `requestRouteWithWaypoints()`, `cancelRoute()`, `requestReRoute()`, `addWaypoint()`, `removeWaypoint()`, `changeRouteOption()` |
| `pleos.car.permission.NAVI_CUSTOM_MAP` | `requestNavigationScreen()` |

API별 정확한 요구 퍼미션은 API Reference의 `@RequiresPermission`에서 확인할 수 있습니다.

## 퍼미션 선언 예시

앱에서 사용하는 기능에 필요한 퍼미션을 선언합니다.

AndroidManifest.xml

```
<?xml version="1.0" encoding="utf-8"?>

<manifest xmlns:android="http://schemas.android.com/apk/res/android">

<uses-permission android:name="pleos.car.permission.NAVI_ROUTE" />

<uses-permission android:name="pleos.car.permission.NAVI_ROUTE_SEARCH" />

<uses-permission android:name="pleos.car.permission.NAVI_CUSTOM_MAP" />

<application>

<!-- ... -->

</application>

</manifest>
```
