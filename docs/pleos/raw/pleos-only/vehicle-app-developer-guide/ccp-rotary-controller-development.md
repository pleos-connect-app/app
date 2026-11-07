<!-- source: https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/ccp-rotary-controller-development -->

# CCP 개발 가이드

CCP(Center Control Panel)는 차량 내 물리 버튼 및 하드키를 통해 시스템 기능을 제어하는 입력 장치를 말합니다. CCP 입력 장치에는 Rotary Controller, 다이얼, 물리 버튼 등이 포함됩니다. CCP Back 하드키는 현재 화면의 이전 단계로 이동하거나, 더 이상 이전 화면이 없는 경우 앱 또는 시스템 레벨의 Back 동작을 수행하는 물리 버튼입니다.

## CCP 지원 정책

Pleos Connect 앱은 아래와 같이 분류되며, 분류별로 CCP 지원 요건이 다르게 적용됩니다.

- User-installed 앱 (사용자 설치 앱): Pleos App Market을 통해 배포되며 사용자가 차량에 설치하는 앱입니다.
- Pre-installed 앱 (기본 탑재 앱): 차량 출시 시 기본으로 탑재되어 있는 앱입니다.
- System 앱 (시스템 앱): 플랫폼 또는 OEM이 시스템 이미지에 포함하여 제공하며, 사용자가 설치·삭제할 수 없는 앱입니다.

### User-installed 앱

User-installed 앱의 경우 CCP 지원 여부는 개발사가 선택할 수 있으며, CCP 미지원 앱도 Pleos App Market 등록 및 배포에 제한이 없습니다. 다만 CCP 미지원 앱은 필수 조작을 터치만으로 완결할 수 있어야 하며, 이는 CCP 미지원 환경에서 사용 불가 상태가 발생하지 않도록 하기 위한 요건입니다.

### Pre-installed 앱

Pre-installed 앱은 필수적으로 CCP를 지원해야 합니다. [Pleos Connect CCP UX 가이드라인](#pleos-connect-ccp-ux-%EA%B0%80%EC%9D%B4%EB%93%9C%EB%9D%BC%EC%9D%B8)에 따라 CCP 연동을 구현합니다.

Pre-installed 앱 CCP 적용 정책

#### Pre-installed 앱 CCP 정책

- Pre-installed 앱 계약서에 "CCP 지원"을 의무 조건으로 명시합니다.
- 앱은 초기 릴리즈 시점부터 CCP 기반 조작(회전, 클릭, 방향 이동 등)을 지원해야 합니다.
- CCP 입력과 화면 포커스 이동은 [Pleos Connect CCP UX 가이드라인](#pleos-connect-ccp-ux-%EA%B0%80%EC%9D%B4%EB%93%9C%EB%9D%BC%EC%9D%B8)을 준수해야 합니다.
- 향후 업데이트에서 CCP 기능이 누락되거나 비활성화되는 것을 허용하지 않습니다.
- CCP 미지원 또는 불완전 지원 시 Pre-installed 앱 승인 불가 처리됩니다.

#### Pre-installed 앱 CCP 적용 예외 정책

Pre-installed 앱이 다음 조건 중 하나 이상을 충족하는 경우 CCP 지원을 필수로 요구하지 않습니다. 이 경우 해당 내용은 계약서에 명시되어야 합니다.

1. 앱의 기능이 정적 콘텐츠 중심으로 되어 있어 CCP 입력이 불필요한 경우
   - 화면 이동이나 포커스 기반 조작 요구가 없음
   - 단순 정보 표시형 앱(예: 고정 정보 패널, 단일 화면 구조 등)
2. 브라우저 앱 또는 특정 미디어 앱처럼 CCP 구현이 기술적으로 제한적인 경우
   - WebView 기반 포커스 조작 제약
   - [Pleos Connect CCP UX 가이드라인](#pleos-connect-ccp-ux-%EA%B0%80%EC%9D%B4%EB%93%9C%EB%9D%BC%EC%9D%B8)에서 CCP 지원을 권고 수준으로 규정한 영역
3. 게임처럼 CCP 사용이 UX나 안전성 측면에서 적합하지 않은 경우

### 게임 앱 CCP 지원 정책

Pre-installed 앱은 원칙적으로 CCP 지원이 필수이나, 게임 앱은 특성상 CCP 지원이 의미 없거나 불가능한 경우 예외로 인정할 수 있습니다.

### 브라우저 앱 CCP 지원 정책

#### 사용성 보장 요건

Pre-installed 브라우저 앱은 CCP가 있는 차량에서 사용성을 보장하기 위해 다음을 준수해야 합니다.

- CCP 회전 → 스크롤(상/하)
- CCP 클릭 → 링크/버튼 선택
- CCP 좌/우 이동 → 포커스 이동 (적용 범위: 주소창, 북마크/탭/홈, 뒤로/앞으로, 설정, 기본 메뉴)

#### CCP 지원 영역

Pre-installed 브라우저 앱은 CCP 지원을 브라우저 UI 영역에 한정하며, 웹페이지 내부 콘텐츠의 CCP 포커스는 전면 보장을 요구하지 않습니다. HTML 구조에 따라 포커스가 맞지 않거나 복잡한 웹페이지에서 예측 불가 동작이 발생할 수 있기 때문입니다.

### CCP 미지원 선언 방법

CCP를 지원하지 않는 앱은 `<application>` 요소에 다음 메타데이터를 추가하여 CCP 미지원 앱임을 선언합니다.

AndroidManifest.xml

```
<application

...>

<meta-data

android:name="com.hmg.rotary.ROTARY_SUPPORTED"

android:value="false" />

</application>
```

## CCP Back 하드키 입력 시 앱 숨김 처리 정책 (필수)

앱 내 최상단 포커스(더 이상 이전 화면으로 이동할 수 없는 상태)에서 CCP Back 하드키가 입력되면 앱을 종료하지 않고 숨김(Hide) 처리해야 합니다. 숨김 이후에도 미디어 사운드 출력 등 백그라운드에서 유지되어야 하는 기능은 계속 동작해야 합니다. 백그라운드 동작이 허용된 기능은 해당 기능의 서비스 생명주기와 오디오 포커스 정책에 따라 계속 동작할 수 있습니다.

앱 내 하위 화면 또는 팝업이 있는 경우에는 기존 Back 정책에 따라 이전 화면 이동 또는 팝업 종료를 수행합니다.

### 앱 숨김 처리 구현 방법

CCP Back 하드키 입력을 처리하기 전에 다음 플랫폼 동작을 확인해야 합니다.

- CCP Back 하드키 입력이 Android 표준 Back 이벤트로 전달되는지 확인합니다.
- 표준 Back 이벤트로 전달되는 경우 `OnBackInvokedDispatcher` 또는 플랫폼에서 지원하는 기존 Back API 중 적절한 API를 사용합니다.
- 팝업, 하위 화면, 최상위 화면을 구분하여 하위 화면에서는 이전 화면 이동 또는 팝업 종료를 수행하고, 최상위 화면에서만 앱을 숨깁니다.

확인 결과 CCP Back 하드키 입력이 Android 표준 Back 이벤트로 전달된다면, 다음과 같이 앱을 숨김 처리합니다

- 앱 숨김 처리는 Android Framework에서 제공하는 [`moveTaskToBack(boolean)`](https://developer.android.com/reference/android/app/Activity#moveTaskToBack(boolean)) API를 사용하여 구현합니다.
- 앱 내 최상단 포커스 상태에서 CCP Back 하드키 입력 시 `moveTaskToBack(true)`를 호출하여 Task를 백그라운드로 이동합니다.
- `finish()` 또는 `finishAffinity()`를 통한 Activity 종료는 수행하지 않습니다.
- Task를 백그라운드로 이동한 경우에도 앱 프로세스가 종료될 수 있으므로 필요한 상태는 별도로 저장하고 복원해야 합니다.

최상단 포커스에서 CCP Back 하드키 입력 시 앱이 종료되거나 `moveTaskToBack(true)` 기반 숨김이 구현되지 않은 경우 심사 반려 대상이 될 수 있습니다.

## CCP 심사 정책

CCP 지원이 필수인 Pre-installed 앱은 Pleos App Market 등록 전 CCP 심사를 거칩니다. 아래는 심사 시 확인하는 항목 예시와 결과 기준입니다.

### 심사 항목 예시

심사 시 다음 항목을 확인합니다.

- 포커스 이동이 모든 주요 UI 구성요소에서 정상 동작하는지
- CCP 회전 시 리스트/메뉴 스크롤이 자연스러운지
- CCP 클릭 시 선택/확인 입력이 정상 수행되는지
- 역방향 입력(뒤로, 취소)이 정상 처리되는지
- 터치 전용 UI만 있어 CCP 조작이 불가한 영역이 없는지
- CCP 입력 시 안전 관련 동작(운전 중 차단 요소 등)이 준수되는지

### 심사 결과 기준

- CCP 테스트에 합격하면 Pre-installed 앱으로 등록할 수 있습니다.
- CCP 테스트에 불합격한 경우 수정 요청 항목을 반영한 뒤 재심사를 요청합니다.

## Pleos Connect CCP UX 가이드라인

![Download](data:image/png;base64,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)[Pleos Connect CCP UX Guideline](https://document.pleos.ai/img/policy/Pleos-Connect-CCP-UX-guideline-20260220.fig)

CCP 정책 준수 유의사항

- CCP 지원 정책은 AAOS 버전, 차량 플랫폼, 차종별 사양에 따라 업데이트될 수 있습니다.
- CCP는 차량 안전성과 직결되므로 HMI/안전 기준 미준수 시 배포가 제한되거나 기본 탑재 앱에서 제외될 수 있습니다.
- Pleos Connect CCP UX 가이드라인이 업데이트 될 경우, Pre-installed 앱 개발사에게 앱 업데이트를 요청할 수 있습니다.
