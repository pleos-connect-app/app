# Pleos Connect 앱 설계(기획) 원칙

> 출처: Pleos Connect 앱 설계 가이드 (https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/intro), 2026-11-07 수집.
> 원문: `raw/pleos-only/vehicle-app-planning-guide/*.md`

## TL;DR — 꼭 지킬 규칙

1. **주행 중 동작은 `CarUxRestrictions`가 결정한다.** 제한 플래그를 구독해 해당 UI/기능을 축소·비활성화. 주행 규제 상태에서 **영상 재생 시작/재개 금지**.
2. **DO(Distraction Optimization) 미선언 앱은 주행 중 사용 불가.** 주행 중 쓰일 화면이라면 DO 선언 + 제한 대응 필수.
3. **카테고리를 설계 첫날 확정.** 주행 중 예외 허용은 Music/Audio, Maps/Navigation, Mobility Services만 (그것도 제한적).
4. **화면은 portrait(1 window) / landscape(2 window) 두 벌 설계.** Driving view는 가릴 수 없음. window 크기·위치는 사용자가 바꿀 수 있으니 반응형 필수.
5. **화면에서 사라져도 종료가 아니다.** 포그라운드 재진입 시 상태 복원, 백그라운드에서 애니메이션·센서·폴링 즉시 정리.
6. **하드웨어 직접 접근 금지.** 표준 데이터 → AAOS Car API, 특화 기능 → Pleos Connect SDK. 지원 여부는 모델명 분기가 아니라 `checkXXXCapabilities` / `isSupported` / `CarPropertyManager`로 런타임 확인.
7. **리소스 상한:** CPU — 내비 주행 중 ≤15%, 비내비 주행 중 <5%, 정차 중 모든 앱 ≤25%. **RAM ≤ 400MB.**
8. **오프라인 대비:** 캐시, retry+timeout, 오프라인 안내 UI.

---

## 1. 주행 안전 (Driver Distraction)

[AAOS 운전자 주의 분산 가이드라인](https://source.android.com/docs/automotive/driver_distraction/guidelines?hl=ko) 준수가 전제.

**시선 분산 최소화**
- 운전 정보는 짧은 시간 안에 이해되게 간결·명확. HUD·클러스터·OSD 영역 사용 시 해당 정책 준수.
- 주행 중 동영상·애니메이션·움직이는 이미지 최소화/제한. 장식적·반복 모션 지양. 이미지는 정보 전달에 필요한 만큼만.

**정보 구조·인터랙션**
- 주행 중 필요한 기능에 우선순위 → 최소 조작으로 도달. 메뉴 구조 단순화.
- 자주 쓰는 기능은 첫 화면·상단. 드롭다운·다단 메뉴 남용 금지.
- 자동 애니메이션·반복 화면 전환 최소화.
- 운전에 중요한 오디오 안내 시 오디오 포커스 정책에 따라 일시정지/덕킹.

**터치 조작**
- Large touch target, high contrast, 텍스트 최소화 (수치는 [design-foundations.md](design-foundations.md), [design-components.md](design-components.md)).
- 터치 최소화, 음성·물리 버튼(CCP/로터리) 등 차량 입력과 연계.
- 게임·인터넷 서핑·피트니스 등 주의 분산 활동 유도 금지.

**텍스트 입력 최소화**
- 검색·로그인도 키보드보다 **프리셋 / 최근 항목 / 음성** 우선.
- 음악 앱: 프리셋 플레이리스트, 최근 재생, 즐겨찾기를 첫 화면에.

원문: `raw/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline.md`

## 2. 지역별 주행 규제 정책

규제는 속도·기어·브레이크 신호 + 지역 규정으로 결정. 플랫폼 버전·양산 차량마다 다를 수 있음.

**DO 미선언 시**

| 상황 | 앱 사용 |
| --- | --- |
| Parking | 가능 |
| Drive | **불가** |

**DO 선언 + 주행 규제 상태의 기능별 허용 (Parking은 전 지역 전부 ✅)**

| 기능 (관련 플래그) | 북미 Driving | 유럽 Driving | 그 외 Driving |
| --- | --- | --- | --- |
| 키보드 `NO_KEYBOARD(8)` | ⛔ | ✅ | ✅ |
| 다이얼패드 `NO_DIALPAD(1)` | ⛔ | ✅ | ✅ |
| 통합 검색(IVI 내 검색, 웹검색 아님) `NO_KEYBOARD(8)`+`NO_FILTERING(2)` | 음성한정 | ✅ | ✅ |
| 미디어 앱 로그인·설정·검색 `NO_SETUP(64)`+`NO_KEYBOARD`+`NO_FILTERING` | ⛔ | ⛔ | ✅ |
| 디지털키·도어·시트·차량 설정 `NO_SETUP(64)` | ⛔ | ⛔ | ⛔ |
| 동영상 시청 `NO_VIDEO(16)` | ⛔ | ⛔ | ⛔ |
| 내비 검색 `NO_KEYBOARD`+`NO_FILTERING` | ⛔ | ✅ | ✅ |
| QR 로그인 `NO_SETUP(64)` | ⛔ | ⛔ | ⛔ |
| 매뉴얼(대용량 텍스트) `LIMIT_CONTENT(32)`+`LIMIT_STRING_LENGTH(4)` | ⛔ | ⛔ | ⛔ |

(플래그 접두사 `UX_RESTRICTIONS_` 생략)

설계 함의:
- **가장 엄격한 북미 기준으로 설계**하고 플래그 기반으로 풀어주는 게 안전. 지역 하드코딩 대신 `CarUxRestrictions.getActiveRestrictions()` 비트마스크로 판단.
- 로그인/설정/QR/매뉴얼/영상은 어느 지역이든 주행 중 막힘 → 주차 중에 끝내도록 플로우 설계.
- InfoLINK용 앱은 지역별 키보드 분기 필요할 수 있음 (partnership@pleos.ai 문의).
- 구현: [dev-guide.md](dev-guide.md) 주행 규제 섹션.

원문: `raw/pleos-only/vehicle-app-planning-guide/driving-restiction-policy.md`

## 3. 앱 카테고리

| 카테고리 | 핵심 목적 |
| --- | --- |
| Maps/Navigation | 위치 기반 길 안내 |
| Mobility Services | 이동 수단/이동 서비스 |
| Lifestyle | 일상생활 관리·향상 |
| Entertainment | 오락·여가 |
| Business | 업무·생산성 |
| Music/Audio | 오디오 콘텐츠 중심 |
| Kids | 아동 대상 |
| Social/Communication | 사람 간 소통 |

**주행 중 제한적 허용 가능 카테고리 (예시, 실제는 주행 상태·지역 정책에 따름)**
- **Music/Audio:** 앨범 아트 등 단순 시각 정보, 재생/일시정지/곡 넘기기, 스트리밍 청취, 라디오 채널 변경, 오디오북.
- **Maps/Navigation:** 경로 안내·실시간 교통, 간결한 지도, TBT 음성 안내, 목적지 경로 탐색, 빠른 길, 주변 교통.
- **Mobility Services:** 주행 관련 차량 관리, 현재 위치 기반 즉시 정보, EV 충전소 찾기·경유지, 주유소 탐색, 주차장 위치.
- 그 외 카테고리는 주행 중 사실상 사용 불가로 간주하고 설계.

원문: `raw/pleos-only/vehicle-app-planning-guide/app-category.md`

## 4. 앱 구조 및 화면 모델

- 화면 = **Driving view**(운전석 쪽 고정, 앱이 가리거나 종료 불가) + **앱 영역**(최대 2 window).
- `portrait`: 앱이 window 1개 사용 (2앱 동시 표시 시 기본). → **핵심 기능·주요 정보**.
- `landscape`: 앱 하나가 window 2개 확장 사용. → **부가 정보·확장 기능**.
- Manifest `screenOrientation` 미지정 → portrait(1 window). `landscape` 지정 → 2 window 사용 가능.
- 새 앱 실행 시: landscape 앱 표시 중이면 새 앱도 landscape, portrait 2개 표시 중이면 새 앱은 primary window에 portrait.
- 사용자가 window bar·제스처로 위치/크기 변경 가능 → 어떤 크기에서도 핵심 기능 사용 가능하게 반응형.

**포그라운드/백그라운드**
- 화면에서 사라진 앱은 백그라운드 잔류 가능, 표시 앱 종료 시 최근 사용 순으로 다시 foreground.
- 포그라운드 진입: 상태 복원 + 현재 크기/방향으로 UI 재구성.
- 백그라운드 전환: 화면 갱신·애니메이션·불필요 센서 수집 즉시 중단.
- 오디오·통신·다운로드 등 지속 작업은 실행 조건과 중단/재개 정책 명시.
- 백그라운드 상주·타이머·폴링·네트워크 최소화.
- 프로세스 재생성에도 사용자 데이터·진행 상태 복원 (SavedState/영속 저장).

원문: `raw/pleos-only/vehicle-app-planning-guide/app-structure-screen-model.md` (화면 배치 다이어그램은 원문 이미지 참고)

## 5. 하드웨어 접근 정책 & 네트워크

- 차량 물리 제어·센서 **직접 접근 금지**. 표준 플랫폼 API 경유.
- 모바일 API 중 차량에서 제한될 수 있는 것: **Telephony, SMS, 일부 센서(자이로, 근접 등), 캘린더, 주소록**. → 제거하거나 지원 여부 확인 후 동작하는 Optional 구조.
- 주행 중 셀룰러/Wi-Fi 끊김·대역폭 급락 대비: 최근 콘텐츠 로컬 캐시, retry + 적절한 timeout, 오프라인 모드 안내 UI.

원문: `raw/pleos-only/vehicle-app-planning-guide/platform-api-architecture.md`

## 6. SDK API 정책 (AAOS Alignment)

- AAOS 표준에 같은/유사 API가 있으면 **표준 API 사용**. SDK는 중복 제공 안 함.
- SDK 전용 API는 AAOS로 불가능하거나 제조사 고유 보안·특화 제어 영역만.
- SDK API가 AAOS 표준으로 편입돼도 기존 SDK API는 **최소 2 메이저 버전** 하위 호환 유지.

| 구분 | 접근 경로 | 예시 |
| --- | --- | --- |
| 표준 데이터 | AAOS Car API | 속도, 기어, 외기 온도, 연료 레벨 |
| 특화 기능 | Pleos Connect SDK | 제조사 전용 시트 제어, 특화 라이팅, 고유 보안 인증 |

원문: `raw/pleos-only/vehicle-app-planning-guide/pleos-connect-sdk-api.md`

## 7. API 호환성 및 가용성

| SDK | 차량 모델별 호환성 | 확인 방법 |
| --- | --- | --- |
| Vehicle SDK | 모델마다 다름 | 도메인별 `checkXXXCapabilities` |
| NaviHelper SDK | 내비 탑재 차량만 전체 호환 | — |
| TextToSpeech / SpeechToText SDK | 전체 호환 | — |
| FusedLocation SDK | 모델마다 다름 | `isSupported` |

- AAOS Vehicle Property: [VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds)로 정의 확인, [CarPropertyManager](https://developer.android.com/reference/android/car/hardware/property/CarPropertyManager)로 실제 지원·접근 가능 여부 확인.
- **모델명 기반 분기 금지.**
- SDK에 없는 특수 프로퍼티가 필요하면 직접 구현 전 기술 지원팀 검토 요청.
- SDK 레퍼런스(Vehicle, NaviHelper, STT, TTS, FusedLocation, LLM)는 이 wiki에 미수집 — 필요 시 `https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/<SDK>` 참조.

원문: `raw/pleos-only/vehicle-app-planning-guide/api-compatibility-availability.md`

## 8. 보안·개인정보

- 차량 = 공유 기기. 소유자 변경·운전자 교체 시 **쉬운 로그아웃/계정 전환**.
- 소셜 로그인·OTT 토큰은 **Keystore** 등 안전한 저장소.
- 위치·음성·계정 요청 시 "왜 필요한지" 명확히, 동의 플로우는 간단하게.
- 불필요한 PII 저장 금지, 로그의 개인정보 마스킹.
- Telemetry: 차량 ID·사용자 ID 익명화/가명화 정책 수립, 운전 중 행동 데이터 마케팅 사용 금지.
- 등록 시 상세 기준: [review-checklist.md](review-checklist.md) (앱 보안 가이드).

원문: `raw/pleos-only/vehicle-app-planning-guide/security-privacy.md`

## 9. 성능·리소스

- Cold start: 스플래시 오래 머물지 않게 최소 초기화, 나머지 lazy loading.
- CPU: 내비(주행 중) **≤15%**, 비내비(주행 중) **<5%**, 모든 앱(정차 중) **≤25%**.
- RAM **≤400MB**.
- 백그라운드 장기 작업·타이머·폴링 최소화. 리스트는 재활용(LazyColumn/RecyclerView) + 이미지 캐시.
- 오디오 앱: Pleos 오디오 포커스 정책 준수, 포커스 획득/상실 대응 로직 필수 → [dev-guide.md](dev-guide.md).

원문: `raw/pleos-only/vehicle-app-planning-guide/performance-resource-mgmt.md`
