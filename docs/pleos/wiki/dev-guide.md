# Pleos Connect 앱 개발 가이드 — 구현 규칙 (LLM wiki)

원문: document.pleos.ai. 코드/이름/수치는 원문 그대로. 심사 체크리스트는 `wiki/review-checklist.md` 참조.

## TL;DR — 꼭 지킬 규칙
1. 주행 규제는 `CarUxRestrictionsManager.registerListener()` → `onUxRestrictionsChanged()`의 `CarUxRestrictions`(`isRequiresDistractionOptimization()`, `getActiveRestrictions()`)만 기준으로 적용. 동영상은 `UX_RESTRICTIONS_NO_VIDEO`.
2. 재생 전 `AudioAttributes` + `AudioFocusRequest`로 포커스 요청, 포커스 변경 이벤트 처리 필수. 내비/동시재생은 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`. `AUDIOFOCUS_LOSS` → stop + abandon + MediaSession 비활성.
3. Suspend 진입: 재생 중단 → abandon → 상태 저장 → 리소스 해제. Resume 시 포커스 자동 복원·자동 재생 금지.
4. Music/Audio 앱: `MediaBrowserService` + `MediaSession` 필수, `android:appCategory="audio"`, `androidx.car.app.launchable=true`, 스티어링 휠 키 지원, 앱 연결/프로필 변경 시 자동 재생 금지.
5. 최상위 화면에서 CCP Back(표준 Back) → `moveTaskToBack(true)`, `finish()` 금지. CCP 미지원 앱은 `com.hmg.rotary.ROTARY_SUPPORTED=false` 메타데이터 + 터치만으로 모든 필수 조작 가능.
6. minSdk **API 28 이상**(사이닝 키 로테이션·리니지 요구), Gradle 8.0+, Emulator 2.1.0 (API 34 이미지), AVD는 **Cold boot**(quick boot면 VHAL 불가), RAM 4 GB.
7. 주행 규제 테스트: `adb shell cmd hmg_car_service --service drivingstate driving_regulation on|off`. Virtual Console: `adb forward tcp:8088 tcp:8088` → `http://127.0.0.1:8088/`.
8. Gleo 연동: `assets/schema.json`(actionCalls 최대 3개, function name 대문자_밑줄, description 영어), function마다 manifest intent-filter, 응답은 `ai.pleos.playground.intent.action.APP_ACTION_RESPONSE` 브로드캐스트, 7초 타임아웃.

---

## 1. 주행 규제 (Driving Restriction)
- 앱은 플랫폼이 제공하는 `CarUxRestrictions` 상태를 기준으로 주행 중 UX 제한 적용. AAOS `CarUxRestrictionsManager`로 값 획득.
- `CarUxRestrictionsManager.registerListener()` 등록 → `onUxRestrictionsChanged()` 콜백으로 변경된 `CarUxRestrictions` 수신.
- DO(Distraction Optimized) 미선언 앱의 상황별 사용 가능 여부는 지역별 주행 규제 정책(`pleos-only/vehicle-app-planning-guide/driving-restiction-policy`) 참조. **DO를 선언한 앱도** 주행 규제 활성 시 `CarUxRestrictions`와 `ActiveRestrictions`를 확인해 기능별 UX 제한을 직접 적용해야 함.
- 플랫폼이 지역별 규제 사양에 맞는 `ActiveRestrictions`를 내려주므로 앱은 notify로 받은 값만 반영하면 됨. 키보드·검색 등 지역별 허용/잠금은 위 정책 문서 참조. UX 규제 피그마 사양서는 비공개.
```java
if (mCurrentCarUxRestrictions.isRequiresDistractionOptimization()
    && (mCurrentCarUxRestrictions.getActiveRestrictions()
        & CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO) != 0) {
    stopPlayingVideo();
}
```
- 예: 동영상(DMB)은 `CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO`로 판단.
- 테스트: `adb shell cmd hmg_car_service --service drivingstate driving_regulation on` / `off` (§10). 심사상 미디어 앱(동영상·게임·브라우저)은 주행 중 일시정지 + UI 숨김 필수(checklist §3).

`raw/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide.md`

## 2. 오디오 포커스
**AAOS 개념**: 요청 CarAudioContext × 현재 보유자 CarAudioContext의 Interaction으로 처리.
- R(Reject): 새 요청 거부. E(Exclusive): 새 요청 획득, 기존 상실. C(Concurrent): 공존(보통 duck). C를 의도하면 요청은 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`; 기존 보유자가 duck 시 pause하도록 설정되면 C가 깨질 수 있음.
- 보유자 여러 명이면 가장 보수적 결과: **R > E > C**. 포커스는 충돌 방지/간접 제어 수단이지 유일한 제어 수단이 아님.
- Optional: 통화 중 내비 C→R 사용자 설정(플래그), 지연 가능 포커스(non-transient 요청이 R일 때 delayed 수용).

**Pleos Connect 정책**: Pleos Connect Interaction Matrix v1.0 (2026-01-28) 준수(이미지; 행=현재 보유자, 열=incoming: MUSIC, NAVIGATION, VOICE_COMMAND, CALL_RING, CALL, ALARM, NOTIFICATION, SYSTEM_SOUND, EMERGENCY, SAFETY, VEHICLE_STATUS, ANNOUNCEMENT; 셀 E/C/R).

**개발사 구현 규칙**
1. 각 스트림은 재생 시작 전 `AudioAttributes` + `AudioFocusRequest`로 포커스 요청.
2. C 기대 컨텍스트(예: NAVIGATION + MUSIC)는 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`.
3. E 필요(CALL, EMERGENCY 등)는 목적에 맞는 gain, 포커스로 모든 것을 강제 제어하지 말 것.
4. **포커스 변경 이벤트 처리 필수**(AAOS 필수는 아니지만 Pleos 정책상 구현): `AUDIOFOCUS_GAIN`→정상 재생, `AUDIOFOCUS_LOSS`→즉시 중단/정지, `AUDIOFOCUS_LOSS_TRANSIENT`→일시정지 후 복귀 가능, `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK`→볼륨 감쇄(-X dB), duck 해제 조건(다른 스트림 종료/포커스 반환)에서 원복. duck 중 pause하면 C가 깨질 수 있음.
5. 다중 홀더: result=C로 시작, 모든 currentHolder에 대해 `cell = MATRIX[currentHolder][incoming]; result = min_by_severity(result, cell)` → grant/steal/fail.

**컨텍스트별**
- EMERGENCY: current일 때 대부분 incoming R; incoming EMERGENCY는 대부분 E 선점; EMERGENCY-EMERGENCY는 C. 긴급 중 음악/알림 요청 실패 테스트 필수.
- SAFETY: MUSIC/NAV/알림과 대체로 C/E, EMERGENCY current면 SAFETY incoming R.
- CALL: MUSIC incoming R(통화 중 음악 금지), NAVIGATION incoming C(통화 중 내비 허용, 플래그로 C→R 가능).
- NAVIGATION ↔ MUSIC: C 공존, NAVIGATION은 MAY_DUCK 요청 사용.
- VOICE_COMMAND(Gleo): 일반 오디오보다 우선(단 EMERGENCY, CALL 등 Reject 정의 컨텍스트에는 우선 안 함). 앱이 `AUDIOFOCUS_LOSS_TRANSIENT` 수신 시 **즉시 재생 중단, 필요 시 abandon**.
- 게임 실행 등으로 인한 포커스 상실 처리는 §4.

**권장**: 매트릭스 하드코딩 금지 → `interaction_matrix.json`/csv 로딩(키 `current_context`, `incoming_context` → E|C|R). 로그 필수: incoming context, gain type, client id, current holders, holder별 결과, 최종 결과·처리(grant/steal/reject), duck 적용/해제(대상, 감쇄값).

**검증 시나리오(필수)**: ① MUSIC 중 NAVIGATION → C(duck+내비) ② CALL 중 MUSIC → R ③ MUSIC 중 CALL → E ④ EMERGENCY 중 MUSIC/NOTIFICATION → R ⑤ MUSIC+NAVIGATION 동시 홀더에 VOICE_COMMAND → 보수적 결과.

**CarAudioContext ↔ AttributeUsages**: MUSIC=`MUSIC, UNKNOWN, GAME, MEDIA` / NAVIGATION=`NAVIGATION, ASSISTANCE_NAVIGATION_GUIDANCE` / VOICE_COMMAND=`VOICE_COMMAND, ASSISTANT` / CALL_RING=`CALL_RING, NOTIFICATION_RINGTONE` / CALL=`CALL, VOICE_COMMUNICATION, VOICE_COMMUNICATION_SIGNALING` / ALARM / NOTIFICATION=`NOTIFICATION, NOTIFICATION_*` / SYSTEM_SOUND=`SYSTEM_SOUND, ASSISTANCE_SONIFICATION, ASSISTANCE_ACCESSIBILITY` / EMERGENCY / SAFETY / VEHICLE_STATUS / ANNOUNCEMENT. 시스템 컨텍스트는 Android 11+.

`raw/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/audio-focus-development.md`

### 2.1 Suspend 모드
- Suspend = IGN OFF 후 완전 종료가 아닌 저전력 상태(디스플레이/UI 비활성, 오디오·미디어 중단, Garage mode 등 일부 작업만). 앱은 "언제든 중단되고 다시 시작될 수 있는 상태"로 간주.
- **진입 시 모든 일반 오디오 출력 금지, 오디오 포커스 반드시 명시적 abandon**. 순서: ① 재생 즉시 중단 ② abandon ③ 상태 저장(재생 여부, 위치) ④ 리소스 해제(player, track). 이 과정에서 새 재생/포커스 요청 불허.
- 예외(최소 범위): 전화 통화·긴급 통신, 안전 경고음, 시스템 필수 음성 안내.
- **Resume**: 포커스 자동 복구 안 됨, 상태 재평가 후 필요 시 재요청, 자동 재생 여부는 OEM/사용자 정책. Resume 대비로 포커스 선점·유지 금지, 백그라운드 재생 지속 금지.
- 예제 핵심(Kotlin): `AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setOnAudioFocusChangeListener(...)`; 리스너에서 `AUDIOFOCUS_LOSS`, `AUDIOFOCUS_LOSS_TRANSIENT` → `pausePlayback(); abandonAudioFocus()`; `onSuspendEntering()` = pause → savePlaybackState → `audioManager.abandonAudioFocusRequest(req)` → releaseTransientResources(타이머, 센서, wake lock); `onResumeFromSuspend()` = restore 후 `shouldAutoResume = false`(기본) 일 때 재생 안 함. (원문은 Suspend 진입 감지 API를 명시하지 않음.)

`raw/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/suspend-mode-audio-focus.md`

## 3. (요약) 카테고리 선언 매니페스트 모음
| 용도 | 선언 |
|---|---|
| Music/Audio 앱 | `<application android:appCategory="audio">` |
| 런처 노출 | `<meta-data android:name="androidx.car.app.launchable" android:value="true"/>` (application 하위) |
| Maps/Navigation 앱 | `<intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.APP_MAPS"/></intent-filter>` |
| CCP 미지원 | `<meta-data android:name="com.hmg.rotary.ROTARY_SUPPORTED" android:value="false"/>` (application 하위) |
| Gleo action 수신 | `<intent-filter><action android:name="ADD_REMINDER"/><category android:name="android.intent.category.DEFAULT"/></intent-filter>` (function별) |

## 4. Music/Audio 앱
**카테고리**: 음악 스트리밍/재생, 팟캐스트, 오디오북, 라디오. 영상 주 기능 앱 제외. 등록 요건: 오디오 재생이 주 기능, 주행 환경 적합 UI/UX, **백그라운드 재생 + 차량 미디어 컨트롤 연동**. 음악을 단순 보조 기능으로 제공하는 앱은 **즉시 반려**.
**하위 카테고리(필수 전달)**: 음악(Music: 아티스트/앨범/트랙), 팟캐스트(에피소드, 구독/최신 회차), 오디오북(챕터 탐색, 재생 위치 저장), 라디오(채널/방송국 탐색). 구분 불명확 시 반려 사유. 콘텐츠 유형별 브라우징 구조 제공, 단일 리스트 혼합 노출은 제한될 수 있음.
**매니페스트**: `android:appCategory="audio"`, `androidx.car.app.launchable=true` (§3).
**필수**: `MediaBrowserService`(MBS) 구현(차량이 MBS로 탐색·상태·제어), `MediaSession`으로 상태·제어 제공. MBS 미구현 오디오 앱은 탑재/배포 제외 가능.
**미디어 센터(MBS 기반 템플릿) 권장**:
| 기능 | 미디어 센터 사용 | 미사용 |
|---|---|---|
| 스티어링 휠 키 | 기본 제공 | 별도 구현 필요(미구현 시 반려 가능) |
| 미디어 앱 라이브러리 등록 | 지원 | 미지원(미노출) |
| Tune Knob(다음/이전) | 지원 | 미지원 |
| 차량 Media 버튼 | 지원 | 미지원 |
| 차량 Mode 버튼 | 미지원 | 미지원 |
**스티어링 휠 키(필수)**: 미디어 센터 연동 또는 MBS 구현. MBS 방식은 `MediaSessionCompat` 등록 + `MediaSessionCompat.Callback` 구현, 최소 **재생, 일시정지, 정지, 미디어 ID 기반 재생, 검색 기반 재생**, 가능하면 이전/다음 트랙. **앱 연결 시 자동 재생 금지**(사용자 명시 동작 또는 시스템 재개 요청 시만). 미지원 시 배포 제외 가능.
**동작 규칙**
- 프로필 변경 시에도 자동 재생 금지.
- 게임 앱 실행 등으로 `AUDIOFOCUS_LOSS` 수신 → `MediaSession`이 active로 남지 않도록 stop 또는 session 비활성화:
```kotlin
AudioManager.AUDIOFOCUS_LOSS -> { stopPlayback(); audioManager.abandonAudioFocusRequest(audioFocusRequest) }
AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> pausePlayback()
AudioManager.AUDIOFOCUS_GAIN -> resumePlayback()
```
- `bind()`/`unbind()`만 하면 `onTaskRemoved()`를 못 받을 수 있음 → `onCreate()`에서 `startService()`로 Started 상태 만들기.
- `onStartCommand()` 및 재생 시점에 `startForeground()`.
- `onTaskRemoved()`: `stopMedia()` → `stopSelf()` → `super.onTaskRemoved()` 순서(super 이전에 정지 로직).
- 쓰리핑거 동작 시 `MediaSessionCompat.Callback.onStop()` 호출될 수 있음 → `ExoPlayer.stop()` 수행.
**Gleo AI 연동(선택)**: 곡 검색/재생 음성 + 핵심 기능 3~5개 추가 음성 기능. 방법 ① Gleo Interface(§12) ② Android 표준 미디어 API(MediaAgent, Media3 기반; 스키마 없이 지원하는 업데이트 준비 중). Media3 최소: `MediaSession`/`MediaLibraryService` 연동, 재생/일시정지/다음/이전, (`MediaLibraryService` 시) 검색 명령. **곡 검색 음성 기능은 Media3 `MediaLibraryService` 검색 구현 필수**(MediaSession만이면 기본 제어만). `MediaBrowserService`도 호환되나 Media3 권장.
**심사/조치**: 카테고리·하위 카테고리 명확성, 라이브러리 연동 구조, 사용성·안전성. 카테고리 불일치 → 수정 요청/반려, 하위 카테고리 미정의 → 보완 요청, 반복 위반 → 노출 제한/등록 중단.

`raw/pleos-only/vehicle-app-developer-guide/music-audio-app-developement-guide.md`

## 5. Maps/Navigation 앱
- 기본 정책: 운전자 안전 최우선, 단일 경로·단일 내비, 주행 중 조작 최소화, 카테고리 기반 기능 제한, Pleos/AAOS 표준 API + UX 가이드.
- **카테고리 선언**: 내비컨테이너(내비앱 고정 표시영역) 사용을 위해 `android.intent.category.APP_MAPS` intent-filter 선언(§3) + App Market 등록 카테고리 일치. 불일치 시 **즉시 반려**, 심사 중 기능 변경 시 재심사 요청.
- **단일 경로 정책**: 하나의 Navigation 앱만 Active Route. Active Route=음성·TBT 허용 / Inactive Route(지도 탐색)=안내 금지 / Background=모든 안내 금지 (OEM 내비 안전운행정보 예외). Phone Projection과도 배타. `CarAppFocusManager.requestAppFocus(int, OnAppFocusOwnershipCallback)` 및 `CarAppFocusManager.OnAppFocusChangedListener` 사용.
- 경로 전환은 사용자 명시적 선택 또는 기존 내비 오류/종료 시만; 사용자 인지 안내 제공, 동의 없는 자동 전환 금지.
- 주행 중 금지: 3 depth 초과 메뉴, 긴 리스트 스크롤, 영상·애니메이션·광고. 허용: 음성 입력, 단일 터치 주요 기능, 즐겨찾기 목적지 선택.
- UX 심사: 터치 영역 최소 크기, 주행 핵심 정보 상시 노출, **주행 중 전체 화면 팝업 금지**.
- 권한: 위치 필수, 마이크는 음성 입력 시만, 불필요 권한 → 반려. 비공식 센서/API 금지.
- 성능: 실행·전환 지연 최소, 장시간 주행 안정, crash/freeze → 반려.
- 보안: 위치 데이터 암호화, HTTPS, 개인정보 처리방침, 불필요 위치 저장 금지.
- **Immediate Reject**: 주행 중 광고, 영상·게임 요소, 복수 내비 동시 경로 안내, 카테고리 위반 기능, OEM/AAOS 정책 위반.

`raw/pleos-only/vehicle-app-developer-guide/navigation-app-development-guide.md`

## 6. CCP / Rotary Controller
- CCP(Center Control Panel) = Rotary Controller, 다이얼, 물리 버튼. CCP Back 하드키 = 이전 단계 / 없으면 앱·시스템 Back.
- **User-installed 앱(App Market 배포)**: CCP 지원 선택. 미지원 앱도 등록 제한 없음, 단 **필수 조작을 터치만으로 완결**해야 함. 미지원 시 `com.hmg.rotary.ROTARY_SUPPORTED=false` 메타데이터 선언(§3).
- Pre-installed 앱: CCP 필수(계약서 명시, 초기 릴리즈부터 회전/클릭/방향 이동, 업데이트에서 누락 금지, 미지원 시 승인 불가). 예외: 정적 콘텐츠 앱, 브라우저/특정 미디어(WebView 포커스 제약), 게임. Pre-installed 브라우저: 회전→스크롤, 클릭→선택, 좌/우→포커스 이동(주소창, 북마크/탭/홈, 뒤로/앞으로, 설정, 기본 메뉴), 웹페이지 내부 포커스는 보장 불요.
- **CCP Back 숨김 정책(필수, 모든 앱)**: 최상단 포커스(이전 화면 없음)에서 CCP Back → 종료하지 말고 **숨김**. 미디어 사운드 등 백그라운드 허용 기능은 계속 동작. 하위 화면/팝업이 있으면 기존 Back 정책(이전 화면/팝업 닫기).
  - CCP Back이 Android 표준 Back으로 전달되는지 먼저 확인 → `OnBackInvokedDispatcher` 또는 기존 Back API 사용.
  - 최상위에서만 `moveTaskToBack(true)`. **`finish()`/`finishAffinity()` 금지**. 프로세스가 죽을 수 있으므로 상태 저장·복원.
  - 앱 종료 또는 `moveTaskToBack(true)` 숨김 미구현 시 **심사 반려 대상**.
- CCP 심사(Pre-installed): 모든 주요 UI 포커스 이동, 회전 스크롤 자연스러움, 클릭 선택, 역방향(뒤로/취소), 터치 전용 영역 없음, 운전 중 차단 요소 준수. UX 가이드라인: `Pleos-Connect-CCP-UX-guideline-20260220.fig`.

`raw/pleos-only/vehicle-app-developer-guide/ccp-rotary-controller-development.md`

## 7. IME window resize
- IME 표시 시 IME insets만큼 view 크기 조정은 AOSP 기본 동작(Pleos 정책 아님).
- 사양: **EditText/TextField 있는 window** → IME에 의해 위로 밀려 올라가는 것이 사양. **없는 window** → 앵커링되지 않아야 함(앱 수정 필요).
- Dialog insets 대상을 `statusBars`+`navigationBars`로 한정 → IME 영향 제거:
```kotlin
// App Dialog
val dialog = Dialog(this)
dialog.window?.let { win ->
    val params = win.attributes
    params.setFitInsetsTypes(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
    win.attributes = params
}
// System Dialog: WindowManager.LayoutParams에 동일하게 setFitInsetsTypes 후 windowManager.addView(overlay, params)
```
- SystemBars 등 IME 외 insets는 `View.OnApplyWindowInsetsListener`에서 개별 처리. Activity 유형은 Google "Handle input method visibility" 가이드 기준 검토.

`raw/pleos-only/vehicle-app-developer-guide/ime-window-resize-handling-guide.md`

## 8. TTS / 음성 안내 선행 묵음
- 적용: TTS, 녹음 안내 멘트, 내비/알림/상태 안내 등 모든 음성 출력.
- **재생 시작 전 선행 묵음 150–200 ms 권장**(최소 150 ms; 150 미만은 첫 음절 손실 가능, 200 초과는 체감 지연 → 예외적으로만). 묵음 없이 즉시 재생 금지, 일관 적용.
- 적용 위치 우선순위: ① PCM/WAV 앞단 silence padding ② TTS 생성 후 후처리 ③ 플레이어 재생 지연(스케줄링 편차로 비권장).
- 연속 발화 단위에는 추가 묵음 X, 스트림 끊김/새 세션이면 다시 적용.
- 별도 확인: 출력 경로 사전 초기화, 오디오 포커스/세션 확보 완료, 디코더/플레이어/출력 디바이스 준비. 선행 묵음은 출력 경로 초기화 문제를 대체하지 않음.

`raw/pleos-only/vehicle-app-developer-guide/voice-guidance-preroll-silence-guide.md`

## 9. FAQ — 실행 환경/플랫폼 리소스
- 플랫폼 식별: `android.os.Build.DEVICE`(=`ro.product.device`) → `CONNECT_S` / `CONNECT_L` / `CONNECT_W`. **실차에서만 유효**, 에뮬레이터에서 구분 불가.
- 지역 코드: `product.sw.target_region` → `KOR`, `EUR`, `USA`, `CAN`. 지역별 UX 정책 적용.
- 내비 탑재 여부 확인 방법은 비공개(partnership@pleos.ai 문의).
- 클러스터와 같은 속도: `CarPropertyManager`로 `PERF_VEHICLE_SPEED` 조회(권한 선언 후 현재값 조회 또는 변경 이벤트 구독). GPS 계산 속도는 클러스터와 어긋날 수 있음.
- EV 배터리 충전 상태: Vehicle SDK `getEvBatteryStateOfCharge` API (`ai.pleos.playground.vehicle.api/ev-battery`).
- 코덱: Connect-S/L은 FLAC, AAC, MP3, OPUS, WAV, DTS 등 / H.264, HEVC, VP9, AV1 등 광범위; Connect-W 오디오 PCM, AAC, MPEG, AMR, WMA, Opus, Vorbis, FLAC, ALAC, APE, Dolby, DTS, ADPCM, AIFF, TTA, BSAC / 비디오 MPEG-1/2/4 Part 2, H.263, H.264, H.265, AV1, VP8, VP9. **에뮬레이터는 c2.android.* 소프트웨어 코덱(AAC, MP3, Opus, Vorbis, FLAC, AMR, G.711, PCM / H.263, AV1, AVC, HEVC, MPEG-4, VP8, VP9)만 — 에뮬레이터 결과를 실차에 그대로 적용 금지**.

`raw/pleos-only/vehicle-app-developer-guide/vehicle-app-faq.md`

## 10. ADB 명령 레퍼런스
| 목적 | 명령 |
|---|---|
| 설치(mac) | `brew install android-platform-tools` (Win: `choco install adb` / `winget install Google.AndroidSDKPlatformTools`, Ubuntu: `sudo apt install android-tools-adb android-tools-fastboot`) |
| 기기 확인 | `adb devices` |
| CCS 연결(**CCS 실차 전용**, Devbox/Emulator 미지원) | `adb shell settings put secure hmg_is_ccs_account_linked 1` ; `adb shell settings put global hmg_provisioning_status 4` |
| 스크린샷 | `adb exec-out screencap -p > ~/Desktop/screen_$(date +"%Y%m%d_%H%M%S").png` |
| 화면 녹화 | `adb exec-out screenrecord --output-format=h264 - > ~/Desktop/screen.h264` (Ctrl+C 종료) → `ffmpeg -framerate 60 -i ~/Desktop/screen.h264 -c copy ~/Desktop/screen_$(date +"%Y%m%d_%H%M%S").mp4` |
| 설치 | `adb install -d -r app.apk` (-d 다운그레이드 허용); 실패 시 `adb push app.apk /data/local/tmp/` → `adb shell pm install -d -r /data/local/tmp/app.apk` |
| 패키지 찾기 | `adb shell pm list packages \| grep <키워드>` |
| 런처 실행(GNB 실행과 유사) | `adb shell monkey -p <pkg> -c android.intent.category.LAUNCHER 1` |
| 액티비티 실행 | `adb shell am start -n <pkg>/<Activity>` |
| 삭제 / 데이터 비우기 | `adb uninstall <pkg>` / `adb shell pm clear <pkg>` |
| 강제 종료 | `adb shell am force-stop <pkg>` |
| 텍스트 입력 | `adb shell input text "test"` |
| **주행 규제 ON/OFF**(모든 차종) | `adb shell cmd hmg_car_service --service drivingstate driving_regulation on` / `... off` |
| 기어 변경 | Virtual Console 사용(§11) |
| 유틸리티 모드(먼저 차속 0 / Parking Brake On / Gear P) | `adb shell echo -e -n "\x83\x50\x10\x00\x00\x02\xAA\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x02\x00\x00\x00" > /dev/vcs_simulator_rx` |

App Market 패키지 예: `ai.umos.appmarket`.

`raw/pleos-only/vehicle-app-developer-guide/adb-command-reference-guide.md`

## 11. 에뮬레이터 / SDK 셋업 (getting-started)
- Pleos Connect = AAOS 기반 SDV 인포테인먼트 플랫폼(vehicle property, VHAL 제공), Gleo AI, Navigation, App Market으로 유통.
- **개발 환경**: minSdk **API 28 이상**(차량 사이버보안: 사이닝 키 로테이션·리니지), Gradle 8.0+, Java/Kotlin, Pleos Connect Emulator 2.1.0(Virtual Console 최소 2.1.0).
- **설치**: Android Studio > Tools > SDK Manager > SDK Update Sites > + → Name `Pleos Connect System Image`, URL Mac `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`, Win/Linux `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml` → SDK Platforms에서 API 34의 Pleos Connect system Image 체크(Show Package Details). 이전 버전은 삭제 후 설치.
- **AVD**: Device Manager > Create device > Form Factor `Automotive` > `Pleos Connect v2.1.0` > Advanced Settings: Startup > Default boot **`Cold`**(구버전: Graphics `Hardware` + `Cold boot`), RAM **4 GB**, system image API 34 `Pleos Connect v2.1.0 APIs`.
- **Cold boot 필수**: quick boot면 VHAL 사용 불가.
- GPU 필수: 권장 RTX 4060/5060 Laptop 8GB+ 또는 RX 7700S/7600M XT 8GB급; 최소 RTX 3060 Laptop 6GB 또는 RX 6600M/6700S 8GB.
- 기본 언어 한글 → 차량 설정 > 일반 설정 > 언어/Language > English. STT 테스트: Extended Controls > Microphone 오디오 입력 허용. AVD Bluetooth 동글 미지원.
- **Virtual Console**: `adb forward tcp:8088 tcp:8088` → 브라우저 `http://127.0.0.1:8088/`. 안 되면 에뮬레이터 실행/이미지 버전/`adb devices`/forward 재실행/8088 포트 점유 확인.

`raw/connect/guide/connect.md`, `raw/connect/guide/getting-started/application-development/setup-connect-sdk-emulator.md`, `raw/connect/guide/getting-started/application-development/configure-pleos-connect-emulator.md`, `raw/connect/guide/getting-started/application-development/use-vehicle-emulator.md`

## 12. 테스트
### 12.1 Virtual Console Emulation Controls (차량 상태 시뮬레이션)
- 사용: Emulation Controls → Domain 선택/카드 검색 → 영역 선택 → 값 변경 → 앱 반응 확인.
- 주요 카드: **Motion: Gear Selection P/R/N/D**(기어 변경에 따른 앱 상태 전환 확인 — 주행/정차 테스트용), Brake Pedal 0–100%, Odometer, Steering Angle -540~540°, Steering Wheel Heat. ADAS: Cruise Control State, Cruise Target Speed 5.556–27.778 m/s. Vehicle: Notification 1. Convenience: ETCS, Door Position/Lock, Trunk/Frunk, 실내등/독서등/앰비언트, 외부 조명, Hazard, Turn Signal, Window, Seat Occupancy, Wiper, Glove Box. Safety: Telltales(Off/On/Blink). Energy: Remaining Range, Battery Energy 0–200kWh, Battery SOC 0–100%, Capacity, Charge Current Limit 10–48A, Charge Percent Limit 50–100%(5%), Charge Switch/Port. Climate: HVAC Power, A/C, Auto, Dual, Recirculation, Fan Speed 0–8, Fan Direction, Defroster, Target Temperature 16–30°C(0.5), Seat Temperature, Air Cleaner.
- 테마(Highway/Forest)는 기어 P에서 변경, 테스트 기능에 영향 없음.

`raw/pleos-only/app-test-guide/virtual-console-guide.md`

### 12.2 폼 팩터 테스트 (디스플레이 호환성 심사 대비)
- 준비: Android Studio + Platform-Tools, **Go 1.22+**, 앱 설치된 Emulator, 포트 `8095`, `8096` 사용 가능. 에뮬레이터는 **별도 창**으로 실행(Running Devices 내장/Headless 금지).
- 절차: `adb devices` → `adb emu avd name`(예: `Pleos_Connect_v2.0`) → host-display-bridge.zip(`https://nexus-playground.pleos.ai/repository/raw-releases/host-display-bridge/1.0.0/host-display-bridge.zip`) 압축 해제 디렉터리에서 `go run . --avd "Pleos Connect v2.0"` (로그 `listening on http://127.0.0.1:8096` 확인, 터미널 유지) → 에뮬레이터에서 FormFactorConsole 앱 실행(`UNAVAILABLE`이면 Bridge 확인 후 Refresh; `DISCONNECTED` + "DISPLAY2가 없습니다."면 준비 완료).
- 테스트: Display preset 선택(예: 12.9인치 Portrait, 14.6인치 Landscape) → Orientation Portrait/Landscape → dp/DPI/px 확인 → **가상 디스플레이 생성** → `DISPLAY2` 창에서 앱 실행 → 확인: 요소 잘림/겹침 없음, 텍스트·이미지·아이콘 크기/정렬, 방향별 레이아웃, 터치·화면 전환 정상. 다른 폼팩터는 제거 후 반복(제거 시 앱 강제 종료).
- 종료: 가상 디스플레이 제거 → Bridge Ctrl+C → `adb forward --remove tcp:8095`.

`raw/pleos-only/app-test-guide/form-factor-test-guide.md`

## 13. App Integration (Gleo Interface)
### 13.1 Core concepts
- Gleo Interface = Gleo AI ↔ 타사 앱 음성 명령 상호작용 인터페이스. OpenAI function calling과 유사(Android용 확장). 앱이 **App Schema**(JSON) 등록 → Gleo가 **Gleo Action**(Intent)으로 호출 → 앱이 **Callback**(broadcast)으로 응답 → Gleo가 최종 답변 생성.

`raw/pleos-only/app-integration-guide/guide/core-concepts.md`

### 13.2 App Schema (capability)
- JSON Schema 기반. 필드: `packageName`, `appDescription`, `version`(App Market 등록 버전), `actionCalls`(**최대 3개**). (예시에는 `invokeWord`도 등장: `"invokeWord" : "리마인더"`, 명세 필드 목록엔 없음.)
- `actionCalls[]`: `type: "function"`(고정), `function`: `component`(기본 `"activity"` → `context.startActivity()`, UI 필요 시 / `"service"` → `context.sendBroadcast()`, 백그라운드), `name`(**전부 대문자 + 밑줄**, 고유, 2~3단어로 대상 포함 권장 예 `ADD_REMINDER`), `description`, `parameters`: `type: "object"`, `properties`(각각 `type`, `description`, 선택 `enum`), `required`.
- 가이드라인: description은 **모두 영어**, 1문장(최대 2), 구체 예시 금지, Gleo 동작 조작/타 앱 억제 문구 금지. parameter type은 `string|number|boolean|array` 중 **하나만**(`["string","null"]` 불가). `enum`은 문자열만(null 불가). `required`에는 필수 parameter만. 각 function은 단독으로 명령 수행 가능하게.

`raw/pleos-only/app-integration-guide/guide/app-capability.md`

### 13.3 Schema 전달 / Manifest
- 파일명 반드시 **`schema.json`**, 위치 **`assets/schema.json`**. Pleos가 가이드라인 준수 검토 → 승인 후 통합 자동 활성화.
- function마다 manifest action filter(§3, `android.intent.category.DEFAULT`).
- Gleo 앱은 **부팅 시 한 번** `AssetManager`로 모든 앱의 `assets/schema.json`을 스캔해 DB 저장; 앱 설치(ADB install 또는 마켓)/삭제 시에도 갱신.

`raw/pleos-only/app-integration-guide/guide/deliver-schema.md`

### 13.4 Gleo Action 수신 (examples)
- Gleo JSON `{packageName, action, actionParameters}` → Intent: `action`=Intent action, `setPackage(packageName)`, 파라미터는 extra **`"ACTION_PARAM"`**에 JSON 문자열로(원문 설명은 key별 putExtra라 하지만 예제 코드는 `ACTION_PARAM` 단일 문자열).
- activity: `intent?.extras?.getString("ACTION_PARAM")` (onCreate; 실행 중이면 handleIntent에서도 처리). service: `BroadcastReceiver.onReceive`에서 `intent.action == "ADD_REMINDER"` 확인 후 `JSONObject(intent.getStringExtra("ACTION_PARAM") ?: "{}")`, `optString(...)`로 파싱(예외 처리).

`raw/pleos-only/app-integration-guide/guide/examples.md`

### 13.5 Callback
- 필드: `functionName`(필수, schema의 function name과 일치), `status`(필수: `"success"` | `"asking"` | `"fail"`), `message`(필수, **영어 완전한 문장**, 검색어·지명 등은 타 언어 포함 가능), `mode`(선택, 기본 `"voice"` | `"beep"` | `"silent"` — 앱이 직접 TTS/음악 재생 시 `silent`), `data`(선택이지만 **강력 권장**, JSON 객체 배열 **문자열**, 필요한 정보만).
- success: 결과 없어도 정상 처리면 사용. asking: 필수 파라미터 누락/사용자 확인 필요(다수 일치 등). fail: 비즈니스 로직 실패 + crash/network/timeout. **Gleo는 7초 후 Action 취소**.
- 전송:
```kotlin
val json = JSONObject().apply {
    put("functionName", functionName); put("status", status); put("message", message)
    data?.let { put("data", it) }
}
Intent("ai.pleos.playground.intent.action.APP_ACTION_RESPONSE").apply {
    putExtra("packageName", packageName)
    putExtra("response", json.toString())
}.also { sendBroadcast(it) }
```
- 예: `{"functionName":"PLAY_FREQUENCY","status":"success","message":"Starting the requested station now.","data":"[ { \"stationName\": \"SBS 파워FM\", \"frequency\": \"107.7MHz\" } ]","mode":"silent"}`.
- 라디오 use case: `SWITCH_BAND`(enum FM/AM, service), `SEARCH_STATION`(searchTerm, activity), `PLAY_FREQUENCY`(number, service).

`raw/pleos-only/app-integration-guide/guide/action_callback.md`, `raw/pleos-only/app-integration-guide/use-cases/radio-app.md`
