# Pleos Connect 디자인 원칙 (Design Principles)

원문: Pleos Connect UI Design Guide (https://document.pleos.ai/docs/connect/guide/docs-design/). 이 페이지는 Introduction, Theme, Design Token, Experiences(Driving view, Gleo AI)를 요약합니다.
레이아웃/색상/타이포/모션 수치는 `design-foundations.md`, 컴포넌트는 `design-components.md`를 보세요.

## TL;DR — 꼭 지킬 규칙
- **주행 중에는 시선을 끄는 요소 금지**: 동영상, 애니메이션 이미지, 텍스트 자동 스크롤을 쓰지 않습니다. 한 손으로 조작할 수 있어야 하고 양손 조작은 금지입니다.
- **1~2초 안에 파악할 수 있어야 함**: 핵심 정보만 보여 줍니다. 입력 후 시스템 응답은 **0.25초 이내**여야 하고, 더 걸리면 로딩/응답 중 상태를 시각적으로 표시합니다.
- **대비 4.5:1 이상**(아이콘, 텍스트, 이미지). 중요 텍스트는 **높이 5 mm 이상**, 보조 텍스트는 **3 mm 이상**입니다.
- **텍스트 길이 제한**: 메시지 하나는 영문 120자 또는 24단어 이하, 일본어는 30자 이하, 한 줄은 80자 이하, 한 문단은 3줄 이하입니다.
- 알림은 운전자에게 필요한 경우에만 보냅니다. **알림으로 광고 금지**(광고주명/제품명 외의 텍스트 광고 금지).
- **Landscape와 Portrait 모두 지원**하고(가로 전용 게임 등은 예외), 주간/야간(라이트/다크)과 다국어, 여러 해상도에 대응합니다. 토큰 이름은 `ivi` prefix에 snake_case를 쓰고 축약하지 않습니다.

---

## 1. 기본 디자인 원칙

### Easy & Safe (사용성과 안전성)
- 운전 중 시각적, 인지적 부담을 최소화하고 정보를 쉽고 명확하게 전달합니다.
- 동영상, 애니메이션 이미지, 텍스트 자동 스크롤처럼 운전자의 시선을 끄는 요소는 쓰지 않습니다.
- 앱은 **한 손으로 조작**할 수 있어야 하고 양손을 쓰게 해서는 안 됩니다. 조작은 단순해야 합니다. 운전자의 손이나 발을 움직이게 하는 동작을 유도해서는 안 됩니다.
- Connect-W(와이드)는 사용자의 좌석에 따라 특화된 UI와 인터랙션을 제공합니다(운전석과 조수석 구분).

### Consistency (일관성)
- 아이콘, 용어, 상호작용 패턴을 일관되게 유지합니다.
- Pleos Connect 기본 탑재 UI와 일관성을 유지해 학습 부담을 줄입니다. 가능하면 가이드의 컴포넌트를 그대로 씁니다.

### Glanceability (즉시성)
- 한눈에 확인할 수 있는 정보와 단순한 조작만 요구합니다.
- 운전자가 **1~2초** 안에 정보를 파악할 수 있도록 핵심 정보만 노출하고 불필요한 요소는 뺍니다.
- 사용자 입력 후 시스템 응답은 **0.25초를 넘지 않아야** 합니다. 지연되면 응답 중인 상태를 시각적으로 알립니다(Spinner/Loading, Button `Loading` state 참고).

주행 안전 설계 원칙의 상세 내용(Driver distraction)은 별도 문서에 있습니다: `raw/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline` (https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline).

출처: `raw/connect/guide/docs-design/Introduction/intro.md`, `raw/connect/guide/docs-design/Introduction/designing-apps-for-driving.md`

## 2. 인터페이스 디자인 가이드

### 색상 및 대비
- 중요한 정보는 강한 대비로 표현하되, 색을 과하게 쓰지 않습니다.
- 아이콘, 텍스트, 기타 이미지의 **대비율은 4.5:1 이상**이어야 합니다.

### 글꼴 및 가독성
- 긴 텍스트는 최소화하고 단순한 아이콘과 짧은 문장으로 전달합니다.

| 항목 | 한도 |
| --- | --- |
| 영어 단일 메시지/텍스트 | 120자 또는 24단어 이하 |
| 일본어 단일 메시지/텍스트 | 30자(영문 기준) 이하 |
| 한 줄 | 80자(영문 기준) 이하 |
| 문장/단락 | 3줄 이하 |
| 중요 정보 텍스트(속도, 내비 안내, 경고 등) | 높이 5 mm 이상 |
| 보조 텍스트 | 높이 3 mm 이상 |

- 화면 크기와 사용자와의 거리를 고려해 글꼴 크기를 정합니다(sp/px 기준은 foundations의 Typography 참고: 최소 12sp, 실제 픽셀 높이 18px 이상).

### 아이콘 및 터치 피드백
- 국제적으로 통용되는 직관적인 아이콘을 쓰고 **텍스트 라벨과 함께** 제공합니다.
- 터치하면 햅틱 피드백이나 음성 피드백을 줍니다. 시각적으로는 모든 터치 요소에 shrink 효과(95%)를 적용합니다(foundations 참고).

### 알림 (Notification)
- 중요한 정보를 간결하게 전달합니다.
- 운전자의 필요와 관련 있을 때만 알림을 표시합니다.
- 알림으로 광고를 표시하거나, 광고주명 또는 제품명 외의 텍스트 광고를 표시해서는 안 됩니다.

출처: `raw/connect/guide/docs-design/Introduction/intro.md`

## 3. 유연성 및 확장성
- **주간/야간 모드**: 조명 환경에 따라 UI 밝기와 대비를 자동으로 조정합니다. Light/Dark 테마를 모두 구현하고 색상은 토큰으로 지정합니다.
- **음성 인터랙션**: Gleo AI로 음성을 지원합니다. 음성을 활용해 사용성과 안전을 높일 수 있는지 검토합니다. 음성 통신은 핸즈프리로 말하고 들을 수 있어야 하며, 대화 시작/종료/중단은 수동으로도 실행할 수 있어야 합니다.
- **OTA**: OTA 업데이트를 고려해 설계합니다.
- **확장성**: 다국어를 고려한 UI를 만듭니다(텍스트 길이 변화에 대응). 여러 차종의 해상도와 화면 비율에 유연하게 대응합니다.
- 디자인 리소스(Figma):
  - 디스플레이 가이드: https://document.pleos.ai/img/Pleos_Connect_display_guide_215.fig
  - 디자인 에셋(App icon 그리드/마스킹 템플릿 포함): https://document.pleos.ai/img/Pleos_Connect_design_assets_215.fig

출처: `raw/connect/guide/docs-design/Introduction/intro.md`

## 4. Theme
- 테마는 **아이콘, 폰트, 컬러**를 중심으로 브랜드 아이덴티티를 유지하기 위해 씁니다. 차량 브랜드별 테마가 있습니다.
- 이 페이지는 **이미지로만 구성**되어 있습니다(Types, Theme selected, Theme samples):
  - https://document.pleos.ai/assets/images/Theme1-94b68bcaa9bf3ec6375b50c17ed4c632.svg
  - https://document.pleos.ai/assets/images/Theme2-31f62403aa1b280f43338ea01c75a812.svg
  - https://document.pleos.ai/assets/images/Theme3-1af5bae7a41d392b13d471511bd5b6b3.svg
- 구현 시사점: 색상, 폰트, 아이콘을 하드코딩하지 말고 테마 계층(Compose의 `MaterialTheme`/CompositionLocal 등)으로 주입해 브랜드별로 바꿀 수 있게 만듭니다.

출처: `raw/connect/guide/docs-design/Introduction/theme.md`

## 5. Design Token
- 디자인 토큰은 스타일 속성 값(색상, 폰트, 간격, 그림자 등)을 코드로 추상화한 단위입니다. 플랫폼 간 Single Source of Truth로 씁니다.
- 목적: 일관된 UI, 디자이너와 개발자 간 공통 언어, 유지보수 비용 절감, **다크/라이트/브랜드 테마 전환**.
- **이름 규칙**:
  1. Prefix는 `ivi`, 형식은 **snake_case**입니다.
  2. 토큰 이름만 보고 어디에 쓰는지 알 수 있어야 합니다.
  3. 축약하지 않고 전체 이름을 씁니다. 예: Do `vertical` / Don't `v`, Do `vehicle_setting` / Don't `vs`.
- 구조와 네이밍 다이어그램은 이미지입니다(실제 토큰 목록은 텍스트로 제공되지 않음):
  - 구조: https://document.pleos.ai/assets/images/design-token-structure-dcf7e522d3c5553973414951c03dcf37.png
  - 네이밍: https://document.pleos.ai/assets/images/design-token-naming-rule-3332b17a8cab1084659ec9387203c8c7.png
- 구현 시사점: 자체 토큰도 `ivi_...` snake_case 이름을 따르면 플랫폼 리소스와 맞추기 쉽습니다. 이 가이드의 텍스트에는 실제 토큰 이름이나 hex 값이 없으므로 지어내지 말고, SDK/디자인 에셋(.fig)에서 확인합니다.

출처: `raw/connect/guide/docs-design/Introduction/design-token.md`

## 6. Experiences — Driving view
- Driving view는 운전자를 위한 차량 상태와 주행 정보 영역입니다. **시스템 UI이며 서드파티 앱 영역이 아닙니다.** 앱 window 레이아웃을 계산할 때 이 영역을 피해야 합니다.
- Anatomy:
  1. **Indicator**: Telltale, 도로표지판 정보, 에너지 정보, 자율주행 indicator, 회생제동 indicator(상황에 따라 노출), 도어 잠김/잠금해제 switch(상황에 따라 미노출) 등
  2. **Driving information**: 변속단(상황에 따라 노출), 주행 속도, 에너지/퍼포먼스 게이지, 주행거리계, miniTBT(상황에 따라 노출), 드라이빙모드 등
  3. **View contents**: 차량 외부 컨트롤, 근접 센서와 주변 물체 표시, 차선과 도로 노면 표시 등
- Driving view의 landscape view는 삭제되었고 resize handle은 제공되지 않습니다(window 정책).
- Case variation은 이미지로만 제공됩니다: `Driving1~4.svg`, 예: https://document.pleos.ai/assets/images/Driving1-6fd7b0c7b1a2c14bd93803ddca38d43f.svg

출처: `raw/connect/guide/docs-design/Experiences/driving-view.md`

## 7. Experiences — Gleo AI (음성 비서)
- Gleo AI는 개인형 모빌리티 어시스턴트입니다. 중요 사항을 안내하고, 대화로 답변하거나 기능을 실행합니다.
- **Chrome**: 대화 상태를 빛으로 구분해 보여 주는 시각 요소입니다. **Lottie 파일**로 만들어 status별로 애니메이션됩니다.

### Status (대화 상태)

| Status | 의미 |
| --- | --- |
| Sleep | 대화 중이 아니고 Gleo가 대기 중인 상태 |
| Listening_Passive | 음성 입력을 받기 시작했고 발화를 기다리는 상태. **음성이 2초간 없으면** "운전석에서 듣는 중..."처럼 입력 좌석 메시지를 표시 |
| Listening_Active | 사용자가 발화하고 있고 음성을 입력받는 중 |
| Thinking | 발화가 끝나고 음성 데이터를 처리하는 중 |
| Asking | 응답 전에 추가 입력을 받으려고 되묻는 상태 |
| Speaking | 최종 응답을 제공하는 상태 |
| Standby | 대화 종료 후 Sleep 직전 상태. 응답 화면이 있으면 **5초**(없으면 0초) 유지한 뒤 자동으로 Sleep이 되고 화면이 내려감 |

- 듣는 상태와 Gleo가 응답하는 상태가 명확히 구분되어야 합니다.

### Response display / Elevation
- Gleo는 OS layer에서 **GNB 바로 아래 layer**에 있습니다. 앱보다 위에 있으므로 Gleo가 표시되면 **앱 화면이 일시적으로 가려질 수 있습니다**. 앱은 이 상황을 견딜 수 있어야 합니다.
- 대화는 (1) 사용자가 PTT나 wake word 등으로 의사를 밝히거나 (2) proactive suggestion이 있을 때 시작됩니다. 특정 조건이나 외부 이벤트로 Gleo가 닫힐 수도 있습니다.
- Layout: **Chrome**(상태 표시), **Bubble**(사용자 발화/Gleo 응답), **Gleo Panel**. Panel은 **Template**(list, card 타입)과 **Chips**(상황별 선택지)로 구성됩니다.
- 레이아웃 케이스(Speaker Bubble + Panel, Panel, Event Conflict)는 이미지입니다: `amb_display3~5.svg`.

### Panel interaction
- LIST의 item이나 Chips 버튼을 터치하면 지정된 기능을 실행합니다(예: 그래/아니, 답장해줘, 다음 메시지 읽어줘).
- Panel을 **위로 드래그**하거나 Panel 밖을 터치하면 대화를 종료합니다.
- Panel을 **아래로 드래그**하면 Gleo AI App으로 전환됩니다.

### CCP (Center Control Panel, 조그 다이얼)
- CCP는 IVI의 메뉴 탐색과 선택을 위한 조그 다이얼 하드웨어로, **rotate, tilt, push** 조작을 합니다. CCP를 쓸 수 있는 상황에서는 화면에 **CCP focus**를 표시합니다.
- Gleo 대화는 OS CCP 정책에서 **'팝업'과 동일하게 처리**됩니다. tilt는 제한되고 Gleo가 다른 앱보다 먼저 포커스를 받습니다. focus는 패널에 터치 가능한 대상이 있을 때만 표시됩니다.
- Gleo AI 앱은 OS CCP 정책을 따르며, 상단 타이틀 영역과 하단 히스토리 영역으로 나뉩니다. 가장 최근 대화 버블이 default focus입니다.
- 구현 시사점: 일반 앱도 CCP(키/로터리 입력) 포커스 이동을 지원해야 할 수 있습니다. Compose에서는 `focusable`/`FocusRequester`와 포커스 표시를 고려합니다.

### Multi screen size
- Standard 14.6, Standard 17, Wide Standard, Wide Extended, Waterfall 별 배치는 이미지로만 제공됩니다(`amb_multiscreen_1~5.svg`). Wide에서는 발화 좌석을 인식해 가까운 쪽에 Gleo를 표시합니다.

출처: `raw/connect/guide/docs-design/Experiences/gleo-ai.md`

## 8. 용어 메모 (glossary 발췌)
- **Distraction Optimized / CarUxRestrictions**: 주행 상태(parked/idling/moving/unknown)에 따라 앱 조작을 제한합니다. 주행 중에도 쓰는 앱은 `distractionOptimized` 선언과 UX 제한(`no_setup`, `no_video` 등) 대응이 필요합니다.
- **Entertainment mode**: Parking 상태에서 game/image/video 앱을 Full screen으로 쓰는 모드입니다. timeout이나 차량 상태가 바뀌면 자동 해제됩니다.
- **OSD**: 차량 조작 결과를 짧게 보여 주는 overlay입니다(볼륨, 공조 등).
- **Notification 유형**: Vehicle notification, HUN, Toast, Dialog popup. Notification Center는 Status bar에서 엽니다.
- 참고: glossary는 Connect-S를 "17, 14.5, 12.9인치"로 적고 있지만 Design Guide는 "17, 14.6, 12.9인치"로 적습니다(원문끼리 불일치).

출처: `raw/connect/guide/glossary.md`
