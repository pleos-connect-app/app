---
name: pleos-connect-app-design
description: Use when planning, designing, or implementing any screen, feature, or UI component of this Pleos Connect in-vehicle Android app (Hyundai/Kia AAOS infotainment) — before writing specs or Compose/XML UI, choosing interactions, handling driving restrictions (CarUxRestrictions/DO), audio focus, multi-window layout, vehicle APIs, or preparing for Pleos App Market review.
---

# Pleos Connect App Design

이 프로젝트는 **Pleos Connect**(Hyundai/Kia AAOS 기반 인포테인먼트)에 올라갈 앱이다. 모바일 앱 감각으로 설계하면 심사에서 떨어진다. 설계·구현 전에 아래 wiki를 해당 주제만 골라 읽는다.

## 지식 베이스 위치

`docs/pleos/` — 시작점은 **`docs/pleos/index.md`**.

| 지금 하는 일 | 먼저 읽을 파일 |
| --- | --- |
| 기능 기획, 화면 흐름, 주행 중 동작 정의 | `docs/pleos/wiki/planning-principles.md` |
| 레이아웃·색·타이포·모션·아이콘 | `docs/pleos/wiki/design-foundations.md` |
| 디자인 철학, 토큰, Driving view, Gleo AI | `docs/pleos/wiki/design-principles.md` |
| 버튼·리스트·팝업 등 컴포넌트 선택/스펙 | `docs/pleos/wiki/design-components.md` |
| 코드 레벨 (CarUxRestrictions, 오디오 포커스, CCP, IME, 내비/음악 앱, 에뮬레이터) | `docs/pleos/wiki/dev-guide.md` |
| 출시 전 점검, 심사 반려 회피, 등록·보안 | `docs/pleos/wiki/review-checklist.md` |
| wiki에 없는 세부 수치·원문 확인 | `docs/pleos/raw/**` (각 wiki 섹션 끝에 원문 경로 표기) |

## 절대 규칙 (어떤 화면을 만들든)

1. **주행 중 동작은 `CarUxRestrictions`로 판단.** 지역 하드코딩 금지, 플래그 비트마스크 기준. DO 미선언 Activity는 주행 중 사용 불가. 주행 중 영상 재생 시작/재개 금지. 로그인·설정·QR·매뉴얼·영상은 전 지역 주행 중 차단 → 주차 중 완료하는 플로우로.
2. **가장 엄격한 북미 기준으로 기본 설계** (주행 중 키보드·다이얼패드 ⛔, 검색은 음성 한정).
3. **portrait(1 window) / landscape(2 window) 두 레이아웃.** Driving view는 가릴 수 없음. window 크기 가변 → 반응형.
4. **백그라운드 ≠ 종료.** 재진입 시 상태 복원, 백그라운드에서 애니메이션·센서·폴링 중단.
5. **하드웨어 직접 접근 금지.** 표준 데이터는 AAOS Car API, 특화 기능은 Pleos Connect SDK. 지원 여부는 `checkXXXCapabilities` / `isSupported` / `CarPropertyManager`로 런타임 확인, 모델명 분기 금지.
6. **리소스:** CPU 주행 중 비내비 <5% (내비 ≤15%), 정차 중 ≤25%, RAM ≤400MB. Cold start 최소 초기화.
7. **입력 최소화:** 프리셋·최근 항목·음성 우선, 큰 터치 타깃·고대비, 다단 메뉴/드롭다운 남용 금지, 장식 애니메이션 지양. 수치는 design wiki의 값을 그대로 사용(임의 값 금지).
8. **오디오 앱이면 오디오 포커스 정책 준수** (획득/상실 대응 필수).
9. **보안:** 토큰은 Keystore, 쉬운 로그아웃/계정 전환, PII 최소 수집·로그 마스킹, TLS 1.2+, 릴리스 빌드 디버깅·WebView 원격 디버깅 off.
10. **심사 반응성 기준:** 앱 실행·콘텐츠 로딩 각 10초 이내, 버튼 반응 2초 이내(넘으면 로딩 UI), 터치 피드백 0.25초 이내. 크래시·프리징·더미 데이터 금지. 레터박스/호환 모드 금지.
11. **CCP(로터리) Back:** 최상위 화면에서는 앱 종료가 아니라 `moveTaskToBack(true)`. 자동 재생 재개 금지(서스펜드 복귀·앱 연결·프로필 변경 후 모두).
12. **빌드/테스트:** minSdk ≥ 28. 에뮬레이터 AVD는 cold boot(quick boot면 VHAL 비활성). 주행 규제 테스트: `adb shell cmd hmg_car_service --service drivingstate driving_regulation on|off`. Vehicle SDK 권한은 App Market 사전 승인된 것만.

13. **디자인 수치(텍스트로 확인된 것):** 대비 ≥4.5:1, 폰트 ≥12sp(실제 ≥18px, 권장 18–20px), 시스템 폰트 Asta Sans, 정보 인지 1–2초, 한 손 조작. 메시지 ≤120자(영문)/24단어, 줄 ≤80자, 문단 ≤3줄. 모든 터치 요소 press 피드백(95% 스케일 + pressed 배경), 손가락이 벗어나면 취소. 아이콘 48×48dp 그리드, 앱 아이콘은 Adaptive Icon. 토큰은 `ivi` 접두사 + snake_case(축약 금지), Light/Dark + 브랜드 테마.

## 디자인 가이드의 공백 — 값을 지어내지 말 것

컬러 팔레트·컬러 토큰 hex, 타입 스케일, easing/duration(ms), 화면별 해상도·dp 그리드, 컴포넌트 dp 크기·패딩·radius는 원문에 **이미지로만** 있다. 추측 금지. 필요하면 사용자에게 Figma 에셋을 요청하거나 이미지를 확인한다:
- https://document.pleos.ai/img/Pleos_Connect_design_assets_215.fig
- https://document.pleos.ai/img/Pleos_Connect_display_guide_215.fig
- https://document.pleos.ai/img/policy/Pleos-Connect-CCP-UX-guideline-20260220.fig
임시 값이 필요하면 `TODO(pleos-token)` 주석과 함께 한곳(테마 파일)에 모아 나중에 교체 가능하게 한다.

## 작업 방식

- 새 화면/기능 설계 시: 해당 기능이 **주행 중**에 어떻게 보이는지(제한 상태 UI)를 항상 함께 정의한다.
- 스펙/코드 리뷰 시: `review-checklist.md`의 해당 영역 체크박스를 대조한다.
- wiki와 원문이 다르면 원문(`raw/`)이 우선. 원문도 오래됐을 수 있으니 의심되면 `docs/pleos/tools/refresh.sh`로 갱신하거나 https://document.pleos.ai 확인.
- wiki에 없는 영역(SDK API 레퍼런스: Vehicle, NaviHelper, STT/TTS, FusedLocation, LLM)은 `https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/` 를 WebFetch.
