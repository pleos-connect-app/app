# 초기 개발 버전(v0) 시나리오 검증 보고서

- 대상 시나리오: **서울역 출발 랜덤 드라이브 리캡** (데모용 초기 버전)
- 기준 문서: [randomdrive-mvp-spec.md](randomdrive-mvp-spec.md), `docs/pleos/wiki/*`
- 검증일: 2026-11-07
- 검증 환경: Pleos Connect Emulator **v2.1.0** (Android 14 / API 34, 2560×1440), NaviHelper SDK **3.0.5**, FusedLocation SDK **1.1.3**, AGP 9.5.1 / compileSdk 37 / minSdk 28
- 검증 방법: 문서 검토 + SDK AAR 시그니처 확인(javap) + **에뮬레이터에 검증용 앱(probe)을 설치해 실제 API 호출·콜백 수신 확인**

판정 기호: ✅ 실측 확인 · ⚠️ 동작하나 제약 있음 · ❌ 에뮬레이터에서 재현 안 됨 · ❓ 미검증

---

## 1. 검증 대상 항목

MVP 명세 §5 "선행 검증 필요" 항목과, v0 시나리오가 의존하는 전제를 합친 목록.

| # | 항목 | 출처 |
|---|---|---|
| V1 | NaviHelper SDK 입수·빌드 가능 여부 | MVP §6-1 |
| V2 | NaviHelper 연결(initialize) 및 권한 | MVP F-02 |
| V3 | 목적지 전달(requestRoute) → Pleos 내비 안내 시작 | MVP F-02, 시나리오 5~7 |
| V4 | 내비 화면 전환(주 화면 우선) | MVP F-02, 시나리오 7 |
| V5 | 경로 시작/목적지 변경 이벤트 수신 | MVP F-03, 시나리오 8 |
| V6 | 목적지 도착 이벤트 수신 | MVP F-03, 시나리오 9 |
| V7 | 오류 케이스(너무 가까움/거리 초과) 안내 | MVP F-02 |
| V8 | 실제 도로 거리·예상 시간 사전 계산 | MVP F-01 |
| V9 | FusedLocation 사용 가능 여부·권한 | MVP F-04, §5 |
| V10 | 에뮬레이터 위치 주입(서울역 고정) | 시나리오 사전조건 |
| V11 | 백그라운드 상태에서 이벤트/위치 수신 | MVP F-04, F-06 |
| V12 | 멀티뷰/화면 배치 | MVP F-06 |
| V13 | 주행 중 UI 제약 | MVP F-06 |
| V14 | 앱 내 지도 표시 수단 | 시나리오 4 ("지도 위에 표시") |
| V15 | 서버 통신(인터넷) | 시나리오 10~11 |
| V16 | QR 표시·스마트폰 열람 | 시나리오 12~13 |
| V17 | 심사/등록 시 SDK 권한 신청 필요 여부 | 출시 |

## 2. 검증 결과

| # | 결과 | 근거 / 관찰 내용 |
|---|---|---|
| V1 | ✅ | `https://nexus-playground.pleos.ai/repository/maven-releases/` 에서 **인증 없이** `ai.pleos.playground:NaviHelper:3.0.5`, `FusedLocation:1.1.3` 다운로드·빌드 성공. 에뮬레이터 이미지에 `ai.pleos.playground.service.navi`, `ai.umos.maps.android.navigation.app`(Pleos 내비), `ai.pleos.playground.service.location.fused` 탑재 확인. |
| V2 | ✅ | `initialize(onConnected, onFailed)` → `onConnected` 즉시 호출. 권한 보호 수준 실측: `NAVI_ROUTE`/`NAVI_ROUTE_SEARCH`/`NAVI_CUSTOM_MAP` = **normal**(설치 시 자동 부여), `FUSED_LOCATION` = **dangerous**(런타임 요청 필요). |
| V3 | ✅ | `requestRoute(RouteInfo(37.4786, 127.1817, poiName="남한산성"))` → `onRouteStarted(duration=1680s, distance=15000m, name=남한산성)` → `onNavigationStatus(ROUTE_DRIVE)` → `onDrivingInfo`. Pleos 내비가 **자동으로 최상단(topResumedActivity)으로 전환**되어 안내 화면 표시(28분·15km). `getDestinationInfo()`로 목적지 재확인 가능. 참고: `RouteInfo`의 `poiId`·`poiSubId`·`address`는 non-null String. |
| V4 | ✅ | `requestNavigationScreen(NavigationScreenRequest.Foreground)` 호출 시 내비가 전면으로 전환됨(`NAVI_CUSTOM_MAP` 필요). requestRoute만으로도 전환되므로 "내비로 돌아가기" 버튼 용도로 사용. |
| V5 | ✅ | 새 목적지 요청 시 순서: `onRouteCancelled`(기존) → `onNavigationStatus(FREE_DRIVE)` → `onRouteStarted` → `onRouteEnded` → `onNavigationStatus(ROUTE_DRIVE)` → `onRouteStateInfo` → `onDrivingInfo`. ⚠️ **`onRouteEnded`는 경로 탐색 직후 바로 오므로 "여행 종료" 의미로 쓰면 안 됨.** 목적지 별도 `onDestinationChanged`는 이번 실측에서 관찰되지 않음 → 경로 시작 판정은 `onRouteStarted` 기준. |
| V6 | ❌ | 차량이 정지 상태라 `onDestinationArrived` 미발생. 10m 거리 목적지도 도착 처리 안 됨. 에뮬레이터에서 내비 위치를 움직이는 방법(모의 주행) 미확인. → v0 시나리오처럼 **`여행 종료` 버튼을 주 경로**로, 도착 이벤트는 "오면 자동 종료" 보조 경로로 설계. |
| V7 | ❌ | 10m 목적지·375km(부산) 목적지 모두 정상 안내 시작, `CAUSE_SEARCH_ROUTE_TOO_SHORT_DISTANCE` / `OVER_DISTANCE` 재현 안 됨. → **앱에서 사전 검증(최소/최대 거리)** 필수, SDK 오류는 보조 처리. |
| V8 | ✅ | `requestTrip(TripRequest(...))` → `onTripInfo(distance=15766m, eta=1637s)`. **안내를 시작하지 않고** 도로 거리·ETA 조회 가능 → 추첨 결과 화면의 "약 27분 · 15.8km" 표시와 후보 필터링에 사용 가능. ⚠️ `summary.routePoints`는 1개뿐이라 경로 polyline 용도로는 부적합. |
| V9 | ✅ | `isSupported()=true`, `initialize()` 후 `getLastKnownLocation`, `registerFusedLocationCallback` 동작. `LocationData(lat, lng, altitude, heading, confidence, timestampMillis)`가 **약 1초 간격**으로 수신. |
| V10 | ⚠️ | `adb emu geo fix 126.9707 37.5547`(서울역) → **FusedLocation은 즉시 서울역 좌표 반영**. 그러나 **Pleos 내비의 현재 위치는 geo fix를 무시하고 고정값(37.4127, 127.0947 / 성남시 수정구 시흥동)** → 경로 출발지가 서울역이 아니라 시흥동이 됨(남한산성까지 15km/28분). 시나리오의 "서울역 출발"과 내비 화면 출발지가 불일치. |
| V11 | ⚠️ | 내비가 전면에 있고 앱이 뒤로 간 상태에서도 NaviHelper 콜백 수신 확인(수 분 단위). 장시간 백그라운드·프로세스 종료 후 복구는 미검증(v0 범위 밖). |
| V12 | ⚠️ | 이 AVD(2560×1440)에서는 Driving view(좌) + 앱 영역(우) 1개. 앱과 내비가 같은 앱 영역을 번갈아 차지(동시 2분할은 미관찰). v0는 멀티뷰 제외이므로 문제 없음. |
| V13 | ✅(문서) | DO 미선언 앱은 주행 중 사용 불가. v0의 모든 조작(반경 선택~여행 종료~QR)은 **P단(주차)** 에서 수행하면 DO 불필요. 데모 시 Virtual Console 기어를 P로 유지할 것. |
| V14 | ⚠️ | 에뮬레이터에 **GMS 없음**(Google Play Services 미탑재) → Google Maps SDK 사용 불가. Pleos WebView(`ai.pleos.android.webview`)는 있음. 또한 내비 앱 자체도 에뮬레이터에서 **배경 지도 타일이 비어 있고 경로선만 표시**됨. |
| V15 | ✅ | 에뮬레이터 네트워크 VALIDATED(LTE), 외부 ping 성공 → 서버 업로드/조회 가능. |
| V16 | ✅(설계) | QR은 앱에서 공유 URL로 로컬 생성(ZXing core 등 순수 Java 라이브러리, GMS 불필요). 표시는 주차 중이므로 규제 대상 아님(규제표의 "QR 로그인"과 다름). 스마트폰 열람은 서버 공유 페이지 구현 범위. |
| V17 | ❓ | 앱 제출 항목에 "SDK 추가 권한: 권한 신청이 필요한 SDK를 사용하는 앱일 경우 프로젝트 ID 입력"이 있음. NaviHelper/FusedLocation이 신청 대상인지 문서상 불명 → Pleos Console(My Project)에서 확인 필요. 에뮬레이터 개발에는 신청 없이 동작. |

### 2.1 Virtual Console 주행 시뮬레이션 실측 (추가 검증)

공식 문서: [Virtual Console 셋업](../pleos/raw/connect/guide/getting-started/application-development/use-vehicle-emulator.md), [사용 가이드](../pleos/raw/pleos-only/app-test-guide/virtual-console-guide.md).
접속: `adb forward tcp:8088 tcp:8088` → `http://127.0.0.1:8088/` (에뮬레이터 v2.1.0 이상).

**화면 구성 (실측)**
- 상단: 주행 시각화(Highway/Forest 테마, 1·3인칭 — 테마 변경은 P에서만), 가속도 표시
- 우측 Drive status: 속도 + 기어 버튼 **P/R/N/D**
- 하단 조작부: **ACCEL / BRAKE 버튼(누르고 있는 동안 동작)**, Steering 슬라이더
- Systems / Climate / Body 요약 패널: 와이퍼, 앰비언트, ETCS, 공조, 도어·창문
- **Emulation Controls**(48 features · 7 domains): Vehicle·Motion·ADAS·Convenience·Safety·Energy·Climate 카드로 VHAL 값 publish — 문서 표와 일치
- 하단 상태: `DDS ONLINE`, AVD 연동 토글

**주행 시험 결과** (목적지 남한산성 안내 중 → D + ACCEL 8초, 최고 135km/h)

| 확인 항목 | 결과 |
|---|---|
| 주행 상태 전환 | ✅ 클러스터/Driving view에 속도·D 표시, Driving view에 TBT("80m 좌회전") 표시 |
| DO 미선언 앱 차단 | ✅ 앱 영역이 **"주행 중에는 사용할 수 없습니다."** 시스템 화면으로 교체됨 (probe 앱 = DO 미선언) |
| 차단 중 앱 프로세스 | ✅ 화면은 막혀도 프로세스는 살아 있고 NaviHelper 콜백 수신 지속 |
| CarUxRestrictions | `dumpsys car_service`: parked → 0x0, unknown → 0x1ff (주행 시 제한 플래그 적용) |
| **내비 위치 이동** | ❌ 속도를 내도 내비 현재 위치는 시흥동 고정 → **Virtual Console로는 도착 이벤트(`onDestinationArrived`) 재현 불가** |
| 위치 관련 카드 | ❌ GPS/위치 주입 카드 없음. 위치는 `adb emu geo fix`(FusedLocation에만 반영)로만 가능 |
| 복귀 | BRAKE 유지 → P 클릭 → 0km/h, Parked |

**시나리오 영향**
- v0 데모는 전 과정을 **P에서 진행**해야 함. 주행 중(D)에 앱을 열면 차단 화면이 뜨므로, `여행 종료`·QR 화면도 P로 돌아온 뒤 보여준다.
- 데모에서 "주행 중엔 앱이 조작을 요구하지 않는다"(MVP F-06)를 보여주고 싶으면, D + ACCEL로 **차단 화면 + Driving view TBT**를 시연하는 것이 오히려 가이드 준수의 좋은 증거가 된다.
- 주행 중 기록 상태 표시(F-06)를 원하면 DO 선언이 필요하며 카테고리 결정이 선행돼야 함 (v1).

### 2.2 내비 현재 위치 변경 방법 조사 (결론: 공개된 방법 없음)

| 조사 대상 | 결과 |
|---|---|
| Pleos Connect 문서 전체(가이드·심사·개발·테스트·디자인, 수집분 200여 페이지) | 내비 위치 변경/모의 주행 관련 기재 **없음**. 에뮬레이터 Extended Controls는 마이크 설정 용도로만 언급 |
| Virtual Console (Emulation Controls 48개 카드) | 위치/GPS 카드 **없음**. D+ACCEL 주행해도 내비 위치 고정 |
| `adb emu geo fix` | FusedLocation에는 반영, **내비에는 미반영** |
| NaviHelper SDK 3.0.5 (javap) | 위치 설정 API 없음. 단 `NaviMode.SIMULATION_GUIDING` 상수 존재 → 내비 엔진에 모의 안내 모드는 있으나 **시작하는 공개 API 없음** (`InternalApi`는 `updateOccupiedRegion(Rect)`만) |
| Pleos 내비 앱 UI (v3.3.2-rc5-hotfix.3) | 설정(경로·충전소·사운드·알림·데이터 관리·버전), 목적지 상세(경유지로/목적지로), 경로 미리보기(대안 3개 + 안내 시작) 모두 확인 — **모의 주행 버튼 없음** |
| Pleos Platform SDK `ai.pleos.location` (Replay·DLT API 보유) | `signature|privileged` 권한 → 3rd-party Connect 앱 사용 불가. 별도 제품군 문서 |

**결론**: 내비 출발지는 에뮬레이터에서 37.4127, 127.0947(성남 수정구 시흥동)로 고정이며, 이를 바꾸거나 모의 주행으로 도착 이벤트를 만드는 공개 방법은 없다. → Pleos 기술지원(partnership@pleos.ai)에 문의하거나, v0는 §3의 대응(서사는 서울역 유지 + `여행 종료` 버튼)으로 진행.

**주의 (Virtual Console 조작)**: 기어 P 복귀는 반드시 **Drive status 패널의 P 버튼**으로 할 것. 다른 P 버튼(Emulation Controls 카드 등)을 누르면 콘솔 표시는 P로 바뀌어도 차량은 D로 계속 주행하는 상태가 남는 것을 확인함. BRAKE로 0km/h 만든 뒤 Drive status의 P 클릭 → 클러스터에 P·0km/h 확인.

## 3. 시나리오 단계별 실현 가능성

| 단계 | 판정 | 비고 |
|---|---|---|
| 1. 정차 상태에서 앱 실행 | ✅ | 기어 P 유지 |
| 2. 현재 위치 + `50km` 버튼 | ✅ | 위치는 FusedLocation(서울역 geo fix) 또는 앱 내 고정 테스트 좌표 |
| 3. `50km` 선택 | ✅ | |
| 4. 로컬 후보 3개 중 추첨 → "오늘의 랜덤 목적지: 남한산성" | ⚠️ | 추첨은 문제 없음. **"지도 위에 표시"는 GMS가 없어 대안 필요** (§5 참고). `requestTrip`으로 거리·ETA 함께 표시 가능 |
| 5~7. `내비게이션 시작` → NaviHelper → 내비 안내 화면 | ✅ | 실측 완료. 단, 내비 화면의 출발지는 시흥동(V10) |
| 8. 경로 시작 이벤트 기록 | ✅ | `onRouteStarted` 기준 |
| 9. `여행 종료` 버튼 / 도착 이벤트 | ⚠️ | 버튼 방식 ✅, 도착 이벤트 ❌(에뮬레이터 재현 불가) → 버튼으로 진행 |
| 10. 샘플 경로·출발지·목적지·시간 업로드 | ✅ | 네트워크 정상. 샘플 좌표 파일은 앱 assets |
| 11. 서버 10초 9:16 영상 | ✅(서버 범위) | Pleos 무관, 백엔드 구현 |
| 12. `리캡 영상 완성` + QR | ✅ | |
| 13. 휴대폰 QR 스캔 → 재생·저장 | ✅(서버 범위) | 공유 페이지 필요 |
| 성공기준: 반경 선택→결과 10초 이내 | ✅ | 로컬 추첨이라 즉시. `requestTrip` 응답도 1초 내 |

**결론: 시나리오 초안은 완성 가능.** 아래 3가지만 시나리오를 조정하면 된다.

1. **출발지 서사 정리** — 내비 출발지가 시흥동으로 고정되므로 (a) 시나리오 출발지를 "판교 인근"으로 바꾸거나, (b) 앱 화면·영상은 서울역 기준 샘플 데이터로 그대로 가고 "내비 화면의 출발지는 에뮬레이터 고정값"이라고 데모 대본에 명시. **권장: (b)** — 영상 서사(서울역→남한산성)를 유지하고, 남한산성은 시흥동 기준으로도 15km라 안내 화면이 자연스러움.
2. **도착은 버튼으로** — `여행 종료` 버튼을 공식 단계로 고정. 도착 이벤트 수신 시 자동 종료는 보조.
3. **결과 화면 지도는 대체 수단** — 서버가 만든 정적 지도 이미지(권장) 또는 지도 없는 카드형 UI.

## 4. 수정된 시나리오 초안 (v0)

**사전 조건**
- Pleos Connect Emulator v2.1.0, Cold boot, Virtual Console 기어 **P**
- `adb emu geo fix 126.9707 37.5547` (서울역) — FusedLocation 표시용
- 앱 assets: `candidates.json`(3곳: 남한산성 37.4786/127.1817 외 2곳), `sample_track_seoulstation_namhansanseong.json`
- 데모 시드 고정(또는 디버그 빌드의 "남한산성 고정" 토글)
- 서버: 리캡 생성 API + 공유 페이지 배포 완료

**흐름**
1. (P단) 앱 실행 → 홈: 현재 위치 "서울역 인근" + `50km` 버튼 (큰 터치 타깃, 단일 선택)
2. `50km` 탭 → 0.5~1.5초 추첨 연출 → **결과: "오늘의 랜덤 목적지: 남한산성"** + 도로 거리·예상 시간(`requestTrip` 결과) + 장소 이미지/정적 지도
3. `내비게이션 시작` 탭 → `requestRoute` → 화면에 "목적지 전달 중…" → `onRouteStarted` 수신 시 "내비게이션 안내 시작" / `onError`·타임아웃(예: 5초) 시 "전달 실패 · 다시 시도"
4. Pleos 내비 안내 화면으로 자동 전환 (앱은 백그라운드에서 이벤트 기록)
5. 진행자: 앱 아이콘으로 복귀 → **`여행 종료`** 탭 (P단)
6. 앱: 샘플 경로 + 메타데이터 업로드 → "리캡 영상 만드는 중…" (상태 폴링)
7. 완료 → "리캡 영상 완성" + 썸네일 + QR
8. 휴대폰으로 QR 스캔 → 공유 페이지에서 재생·저장

**시연 화면 6개**: 반경 선택 / 추첨 결과 / 내비 시작(전달 중·성공·실패 상태 포함) / Pleos 내비 안내 / 영상 생성 중 / 완성 썸네일+QR

## 5. 앱 / 서버 역할 분리

### 차량 앱 (Android, Pleos Connect)

| 기능 | 내용 | 사용 기술 |
|---|---|---|
| 위치 표시 | 현재 위치 조회·표시, 미지원/권한 거부 시 고정 테스트 좌표로 대체 | FusedLocation `isSupported`/`getLastKnownLocation`, `FUSED_LOCATION` 런타임 권한 |
| 반경 선택 | `50km` (v0), 이후 50/100/150 | |
| 후보 로딩·추첨 | assets JSON 3곳 → 직선거리 필터 → 랜덤(시드 고정 옵션) | |
| 거리·ETA 표시 | 추첨 결과의 도로 기준 거리/시간 | NaviHelper `requestTrip` → `onTripInfo` |
| 사전 검증 | 최소/최대 거리 앱단 체크 (SDK 오류 코드 미재현 대비) | |
| 목적지 전달 | 연결 완료 후에만 요청, 전달 중/성공/실패 상태 UI, 타임아웃 | `initialize(onConnected,onFailed)`, `requestRoute`, `onRouteStarted`, `onError` |
| 내비 복귀 | "내비로 돌아가기" | `requestNavigationScreen(Foreground)` |
| 이벤트 기록 | 경로 시작·취소·도착 이벤트를 세션에 저장 | `NaviHelperEventListener` |
| 여행 세션 | 시작/종료 시각, 출발지·목적지, 이벤트 로그; 프로세스 재생성 대비 영속화 | Room/DataStore |
| 주행 기록(v0) | 샘플 좌표 파일 로드 (실 GPS 기록은 v1) | assets |
| 업로드 | 세션 + 경로 업로드, 재시도·타임아웃 | HTTPS (TLS 1.2+) |
| 상태 조회 | 생성 대기/중/완료/실패 폴링 | |
| QR 표시 | 공유 URL → QR 로컬 생성, 썸네일 표시 | ZXing core |
| 가이드 준수 | P단 전용 UI, 큰 터치 타깃, 오프라인 안내, 백그라운드 시 폴링 중단 | `docs/pleos/wiki` |

### 서버 (백엔드)

| 기능 | 내용 |
|---|---|
| 리캡 생성 API | 업로드 수신 → 비동기 작업 생성 → jobId 반환 |
| 작업 상태 API | `QUEUED / PROCESSING / DONE / FAILED`, 완료 시 영상·썸네일·공유 URL |
| 영상 생성 | 경로 좌표 → 지도 2D 경로 애니메이션, 장소명·거리·시간 오버레이, 9:16, ~10초, MP4 (FFmpeg). v0는 고정 템플릿 허용 |
| 정적 지도 이미지 | 결과 화면용 지도 썸네일 제공 (앱에 GMS가 없으므로) — 선택 |
| 공유 링크 | 추측 불가능한 토큰 URL 발급, 개인정보·차량 ID·원본 위치 로그 비노출, 만료 정책 |
| 공유 페이지 | 모바일 웹: 재생·다운로드·공유 |
| 저장소 | 여행 세션, 경로, 영상(객체 스토리지), 공유 토큰 |
| (v1) 후보 DB | 관광지 후보 관리·반경 조회 API — v0는 앱 로컬 JSON |

### 앱 ↔ 서버 API 초안 (v0)

```
POST /v0/recaps
  body: {
    clientTripId, startedAt, endedAt,
    origin:      { name: "서울역", lat, lng },
    destination: { name: "남한산성", lat, lng },
    distanceMeters, durationSeconds,
    track: [ { t, lat, lng }, ... ],      // 샘플 좌표
    events: [ { t, type: "ROUTE_STARTED" | "ARRIVED" | "TRIP_ENDED", ... } ]
  }
  → 202 { jobId }

GET /v0/recaps/{jobId}
  → { status: "QUEUED"|"PROCESSING"|"DONE"|"FAILED",
      shareUrl?, thumbnailUrl?, videoUrl?, error? }

GET /s/{shareToken}   // 휴대폰용 공유 페이지 (HTML)
```

차량/사용자 식별자는 v0에서 보내지 않는다(익명 clientTripId만). 인증 방식은 v1에서 정의.

## 6. 남은 검증 과제

| 항목 | 다음 행동 |
|---|---|
| 도착 이벤트(`onDestinationArrived`)·경유지 이벤트 | Virtual Console 주행으로도 내비 위치가 안 움직임(실측) → 내비 앱 자체 모의 주행 기능 확인, Pleos 기술지원 문의, 또는 실차 검증 |
| 내비 현재 위치 변경 방법 | 공개 방법 없음(§2.2 조사 완료) → Pleos 기술지원 문의 |
| SDK 오류 코드 재현 | 실차 또는 Pleos 문의. 앱은 사전 검증으로 대체 |
| SDK 권한 신청(V17) | Pleos Console My Project에서 NaviHelper/FusedLocation 신청 필요 여부 확인 |
| 장시간 백그라운드 위치 기록 | v1 범위. 백그라운드 수집 정책은 Pleos 문서가 보장하지 않음(FusedLocation 문서 명시) |
| 앱 카테고리 결정 | 주행 중 기록 상태 표시가 필요해지면 DO 선언 + Mobility Services/Lifestyle 등 카테고리 결정 |
| 에뮬레이터 내비 지도 타일 공백 | 데모 화면 품질 이슈 — 네트워크/지도 데이터 설정 확인 |

## 부록: 실측 로그 발췌

```
FUSED isSupported=Success(true)
NAVI connected
EV onCurrentLocationInfo CurrentLocationInfo(latitude=37.4127212, longitude=127.094681, address=수정구 시흥동, ...)
FUSED onChanged LocationData(latitude=37.5546983, longitude=126.9706983, confidence=5.0, ...)   // geo fix 서울역
CMD route
EV onRouteStarted RouteStartInfo(duration=1680, distance=15000, tollFare=0, name=남한산성, ...)
EV onRouteEnded
EV onNavigationStatus ROUTE_DRIVE
EV onDestinationInfo DestinationInfo(latitude=37.4786, longitude=127.1817, name=남한산성, remainDistance=15000, remainTime=1680, ...)
CMD trip
EV onTripInfo dist=15766 eta=1637 points=1
```

검증용 앱 소스는 `docs/product/probe/ProbeActivity.kt` 참고 (명령: `adb shell am start -n ai.probe/.ProbeActivity --es cmd route|trip|fg|dest|cur|cancel|last|fused`).
