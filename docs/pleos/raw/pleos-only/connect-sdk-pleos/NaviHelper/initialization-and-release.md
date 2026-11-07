<!-- source: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/initialization-and-release -->

# Initialization

앱 시작 또는 생성 시점에 `NaviHelper` 객체를 생성하고 `initialize()`를 호출해야 NaviHelper API를 사용할 수 있습니다.

## Initialize the SDK

`initialize()`는 두 가지 오버로드를 제공합니다.

| Overload | Description |
| --- | --- |
| `initialize(onConnected, onFailed)` | 서비스 연결 결과를 콜백으로 받습니다. 최초 서비스 연결이 성공하면 `onConnected`가, 초기 연결 재시도가 모두 실패하면 `onFailed`가 호출됩니다. |
| `initialize()` | 인자 없이 SDK를 초기화합니다. |

초기화가 수행되어야 다른 API들이 정상 동작합니다. 연결 상태에 대한 콜백을 제공하는 API 사용을 권장합니다.

- Kotlin
- Java

```
import ai.pleos.playground.navi.helper.NaviHelper

val naviHelper = NaviHelper(applicationContext)

naviHelper.initialize(

onConnected = {

// 최초 서비스 연결이 성공했습니다. 리스너를 등록하고 API를 호출합니다.

},

onFailed = { throwable ->

// 초기 연결 재시도가 모두 실패했습니다.

}

)
```

```
import ai.pleos.playground.navi.helper.NaviHelper;

import kotlin.Unit;

NaviHelper naviHelper = new NaviHelper(getApplicationContext());

naviHelper.initialize(

() -> {

// 최초 서비스 연결이 성공했습니다. 리스너를 등록하고 API를 호출합니다.

return Unit.INSTANCE;

},

throwable -> {

// 초기 연결 재시도가 모두 실패했습니다.

return Unit.INSTANCE;

}

);
```

인자 없는 `initialize()`도 호출할 수 있습니다.

```
naviHelper.initialize()
```

## Release the SDK

SDK가 더 이상 필요하지 않으면 등록한 리스너를 제거한 후 `release()`를 호출합니다.

- Kotlin
- Java

```
override fun onDestroy() {

naviHelper.removeListener(naviEventListener)

naviHelper.release()

super.onDestroy()

}
```

```
@Override

protected void onDestroy() {

naviHelper.removeListener(naviEventListener);

naviHelper.release();

super.onDestroy();

}
```

`release()` 이후 SDK를 다시 사용하려면 객체를 생성하고 초기화 절차를 다시 수행합니다.
