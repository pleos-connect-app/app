# Pleos Connect 디자인 기초 (Layout, Window, Foundations)

원문: https://document.pleos.ai/docs/connect/guide/docs-design/ 의 getting-started(layout, window)와 foundations(colors, typography, shadow, animation, interaction, iconography, app-icon, 3d-contents)를 요약합니다.

> **주의 — 텍스트로 제공되지 않는 값이 많습니다.** 색상 팔레트와 시스템 컬러 토큰(이름과 hex), 폰트 스케일(Headline/Title/Body/Label의 크기와 굵기), easing 곡선과 duration ms 값, 화면별 해상도와 dp 그리드는 원문에서 **모두 이미지(PNG/SVG)** 로만 제공됩니다. 아래에 이미지 URL을 적어 두었습니다. 값이 필요하면 사람이 이미지나 Figma 에셋(`Pleos_Connect_design_assets_215.fig`)을 확인해야 합니다. **이 문서에 없는 값은 추측하지 마세요.**

## TL;DR — 꼭 지킬 규칙
- 앱은 **Landscape와 Portrait 둘 다** 대응해야 합니다. 동시에 **최대 2개 앱**이 1:1 / 2:1 / 1:2 비율로 함께 표시되고, System UI(Status bar, GNB 패널, 키보드, Gleo)가 앱을 가릴 수 있습니다.
- **모든 터치 요소에 press shrink를 적용**합니다: Pressed에서 **scale 95%**와 모드별 배경색 변경. 손가락이 영역 밖으로 나가면 cancel하고, 영역 안에서 뗄 때만 실행합니다.
- 폰트는 **최소 12sp**이고 실제 픽셀 높이는 **18px 이상**(권장 18~20px)입니다. 시스템 폰트는 **Asta Sans**(Korean, English)입니다.
- Shadow는 3단계(Level 1/2/3)의 정확한 값을 씁니다(아래 표). 아이콘은 **48×48dp 그리드**, Filled/Outlined 두 스타일입니다. App icon은 **Android Adaptive Icon**(Foreground/Background 분리)으로 만듭니다.
- 스크롤 끝에는 **bounce 효과**를 주고 overscroll은 최대 스크롤 영역의 약 **2/5**까지입니다. 로딩 애니메이션은 짧은 로딩에 Linear, 긴 로딩에 Tension을 씁니다.

---

## 1. Display Layout

### 화면 구성 요소
1. **Status bar**: Driving status, Gleo AI와 검색, 시계, 네트워크, 마이크 상태, 알림 센터, 프로필 선택 등 System status
2. **Driving view**: Telltale, 속도, 차량 상태 등 주행/안전 정보
3. **GNB (Global Navigation Bar)**: App library, 즐겨찾는 앱, 공조(Climate) 컨트롤
4. **Application window**: 실행 중인 앱 영역. **앱 1개 또는 2개**를 노출합니다. 가로 전용 게임처럼 한 모드로만 실행되는 앱을 빼면 **모든 앱은 Landscape와 Portrait 레이아웃을 모두** 지원해야 합니다.

### 화면 레이어
- Status bar, GNB의 App library와 공조 패널, 키보드 같은 System UI는 앱보다 위 레이어에 있습니다. 이런 기능을 쓰면 앱 window가 **일시적으로 가려질 수 있습니다**.

### 화면 유형
- **Connect-S**(Standard): OS 기본 크기입니다. **17인치, 14.6인치, 12.9인치**(기존 Connect-L은 Connect-S로 재분류)
- **Connect-W**(Wide): Connect-S보다 가로가 긴 형태로, 주로 고급 차종에 탑재됩니다. 차량 상태에 따라 세로가 늘어나며, 늘어난 형태가 **Connect-W-Extended**입니다.
  - **Connect-WF**(Waterfall): Wide보다 작은 해상도이지만 핵심 기능을 제공합니다.
- 화면별 해상도와 영역 크기는 **이미지에만** 있습니다:
  - 구성: https://document.pleos.ai/assets/images/pleos_connect_deisgnguide_displaylayout_01-e9fac8ec763b8ea5e68aeb709aa8668d.svg
  - 유형: https://document.pleos.ai/assets/images/pleos_connect_deisgnguide_displaylayout_02-86ea4e4c69813e5c635d8b501d057271.svg
  - 세부: https://document.pleos.ai/assets/images/pleos_connect_deisgnguide_displaylayout_03-bca8a53d11c851ca8fe3c8ea3fcaf6fa.svg

출처: `raw/connect/guide/docs-design/getting-started/layout.md`

## 2. Window Policy

|  | Connect-S | Connect-W |
| --- | --- | --- |
| 화면 구성 | 앱 2개면 1:1, 앱 1개면 2:0 (기본값 1:1) | 2:1 또는 1:2 (기본값 2:1) |
| 기본 화면 | 최초 실행과 재탑승 모두 Primary는 Navigation 앱 | 최초 실행: Primary Navigation, Secondary Media. 재탑승: Primary Navigation, Secondary는 마지막에 쓴 Media 앱 |
| 앱 실행 시 | 새 앱은 이전 앱과 **같은 orientation**으로 표시. landscape 앱 1개가 실행 중이면 새 앱도 landscape. portrait 앱 2개가 표시 중이면 새 앱은 primary window에서 portrait | 새 앱은 **항상 landscape로 primary window**에 표시. 기존 primary는 secondary로 이동하고 기존 secondary는 사라짐 |
| 위치 변경 | 3 핑거 스와이프 또는 윈도 바 탭 | 동일 |
| 크기 변경 | 3 핑거 스와이프, 윈도 바 드래그, 앱 윈도 버튼 탭으로 landscape와 portrait 전환 | 동일 |
| 앱 종료 | 3 핑거 스와이프. 종료하면 오른쪽에서 왼쪽으로 최근 앱 순 표시(landscape가 닫히면 landscape로, portrait가 닫히면 portrait로). Navigation은 종료 불가 | 3 핑거 스와이프. primary가 닫히면 secondary가 orientation을 유지한 채 primary로 이동. secondary가 닫히면 최근 앱이 닫힌 앱의 orientation으로 표시. 내비는 종료 불가 |

- 화면에서 사라진 앱은 **종료된 것이 아닙니다**. 표시 중인 앱이 종료되면 최근 활성 앱 순으로 다시 나타납니다. 따라서 앱은 가시성 변화와 리사이즈(configuration change)에 상태를 잃지 않아야 합니다.
- **Connect-W 사양**: 동시 실행 앱은 최대 2개입니다. App library는 운전석과 조수석 양쪽에 표시됩니다. Status bar는 운전석 GNB와 앱 영역 사이에 있습니다. 조수석 App library에서 실행한 앱은 조수석 window에서만 실행됩니다.
- 알아두기:
  - 프리로딩 앱은 landscape와 portrait view를 모두 지원합니다.
  - Driving view의 landscape view는 삭제되었고 resize handle은 제공되지 않습니다.
  - **Deep link(App to App)**: App A에서 App B를 실행하면 B가 A를 덮고, B의 기능이 끝나면 A로 돌아갑니다.
  - 후방카메라처럼 3:0 window를 full screen으로 보여 주는 예외 케이스가 적용될 예정입니다.

출처: `raw/connect/guide/docs-design/getting-started/window.md`

## 3. Colors
- Color는 브랜드 아이덴티티를 유지하고, 대비로 정보 위계를 명확히 표현해야 합니다(대비 4.5:1 이상, design-principles 참고).
- **Basic Color (Scale Token)**: 특별한 의미나 맥락이 없는 경우에 쓰는 표준 컬러 토큰입니다. 어떤 경우에도 Scale Token은 쓸 수 있습니다.
  - **Grayscale**: 투명도가 없는 기본 흑백 — https://document.pleos.ai/assets/images/grayscale-f6be0c4273c5777035ffd84ea28013c9.png
  - **Alpha Color**: 투명도가 있는 색으로 배경이나 다른 요소와 조합할 때 씁니다.
    - White Alpha — https://document.pleos.ai/assets/images/white-alpha-1a1359524cb98282c87149651de538d8.png
    - Gray Alpha — https://document.pleos.ai/assets/images/gray-alpha-0ce361ac45bdc95d66fc57411cf28ae6.png
  - **Static Color**: 라이트/다크 테마의 영향을 받지 않고 항상 raw 값을 유지합니다. 주로 **Overlay 위 요소**에 씁니다.
    - Light — https://document.pleos.ai/assets/images/light-0b540b02ca3f98e2cd94205da112ef67.png
    - Dark — https://document.pleos.ai/assets/images/dark-f44c562ac0ca05f9ccbda030142cf5e0.png
- **System Color** (시맨틱 토큰 그룹). 각 그룹의 토큰 이름과 값은 이미지에만 있습니다. URL 패턴은 `https://document.pleos.ai/assets/images/system-color-<group>-<hash>.png`입니다.

| 그룹 | 설명/용도 | 이미지 |
| --- | --- | --- |
| Background | 콘텐츠를 감싸는 배경, Z축 최하위 | system-color-background-7bee5722e2912af73a71dd8267a463f0.png |
| Button | 버튼 | system-color-button-7fe4b0136b1e45f7264ce44b7297c896.png |
| Controller | 슬라이더/스테퍼 | system-color-controller-35efcfe0a72a4e0023b8c9240dc8e35c.png |
| Climate | 공조 | system-color-climate-3cfaf745bdffaed6b7ec6eb5c1316450.png |
| Dropdown | 드롭다운 | system-color-dropdown-620bef747dd4576c638f685729b6a118.png |
| Dim | 딤 | system-color-dim-a3ad1a1be06b6c8ceac154f03d3979b9.png |
| Driving | 드라이빙 뷰 | system-color-driving-aadbdd18cf6fe678339d3a733572d685.png |
| Fields | 입력 필드 | system-color-fields-31d4597576ec64442fe48e232078b8b8.png |
| Gleo AI | Gleo | system-color-gleo-ai-47aaf3b0e321a56c557c6a98088a165c.png |
| Icon | 아이콘 | system-color-icon-e9b6e7d15a6a5927758f457e39c68eda.png |
| Informative | 정보/상태 | system-color-informative-ef19e6a5596f00f102603ca3413577e3.png |
| Keyboard (Normal) | 키보드 | system-color-keyboard1-24ccff8da02ecbeef3797527d39a50e8.png |
| Keyboard (Static) | 키보드 고정색 | system-color-keyboard2-cf104d403970ba502279fee50a4165a5.png |
| Media | 미디어 | system-color-media-30d93aa4acfffa1ed3a5400e41308ea8.png |
| Overlay | 오버레이 | system-color-overlay-493e63cdf04dd8383fd8cf80a0356ab4.png |
| Phone | 전화 | system-color-phone-6b626a71744074d5178034b411925c7c.png |
| Regulation | 법규 | system-color-regulation-0d00f934044ffc500b2dfabaa1e51fc7.png |
| Surface | 표면(카드/패널) | system-color-surface-d06b9b9d696cedf82963437c12832af3.png |
| Switch | 스위치 | system-color-switch-33bed2234a2b0da3b2467160e60d09c6.png |
| Text | 텍스트 | system-color-text-e7366a153c7953a16d9d7939a9ce2b25.png |

- 구현 규칙: 시맨틱(System) 토큰을 우선 쓰고 Light와 Dark 값을 모두 정의합니다. Overlay 위 요소에는 Static 토큰을 씁니다. 토큰 이름은 `ivi_` prefix와 snake_case를 씁니다(design-principles 참고).

출처: `raw/connect/guide/docs-design/foundations/colors.md`

## 4. Typography
- 중립적인 형태와 균일한 시인성을 가진 서체를 씁니다. 브랜드별로 폰트를 지정할 수 있지만, 안정성을 위해 캐릭터가 강한 폰트는 피합니다.
- 정보 위계에 따라 컴포넌트 간 **일관된 크기**를 적용하고, 서브 텍스트는 위계를 구분해 씁니다.
- **최소 크기**: Android 접근성 기준 **12sp 이상**(12sp ≈ 12dp, 160dpi에서 12px). 가독성을 위해 보통 **1.5배 이상**을 권장하며, **약 18~20px 높이**를 확보하는 것이 안전합니다. 실제 픽셀 기준 최소 높이는 **18px 이상**입니다.
- 주행 관련 물리 높이 기준은 중요 텍스트 5 mm 이상, 보조 텍스트 3 mm 이상입니다(design-principles 참고).
- **시스템 폰트**: Asta Sans(Korean, English) — https://fonts.google.com/specimen/Asta+Sans
- **Font Hierarchy**: Headline / Title / Body / Label 4단계입니다. 크기, 굵기, 행간은 **이미지에만** 있습니다:
  - Headline https://document.pleos.ai/assets/images/headline-cdddc157495a452d70cb6fc2cc2d7359.svg
  - Title https://document.pleos.ai/assets/images/title-4ba825297b802194558f4ac3a33de511.svg
  - Body https://document.pleos.ai/assets/images/body-a60c3c3a40cf6fcc0b4b2c4e39eeccf1.svg
  - Label https://document.pleos.ai/assets/images/label-573dca295b60eefe86fabf8f0f0ba734.svg

출처: `raw/connect/guide/docs-design/foundations/typography.md`

## 5. Shadow (Elevation)
값 단위는 원문에 명시되어 있지 않습니다(Figma 기준이며 dp로 해석하는 것이 일반적입니다). Color는 ARGB hex입니다.

| Level | 용도 | Layer | X | Y | Blur | Spread | Color |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Level 1 | 가장 낮은 elevation | Shadow 1-2 | 0 | 6 | 24 | 0 | `#14000000` (8%) |
| Level 2 | Level 1보다 높은 elevation | Shadow 1-1 | 0 | 8 | 32 | 0 | `#29000000` (16%) |
|  |  | Shadow 1-2 | 0 | 2 | 2 | 0 | `#14000000` (8%) |
| Level 3 | 가장 높은 elevation | Shadow 1-1 | 0 | 20 | 40 | 0 | `#3D000000` (24%) |
|  |  | Shadow 1-2 | 0 | 2 | 4 | 0 | `#33000000` (20%) |

- Level 2와 3은 그림자 2개를 겹친 것입니다. Compose 기본 `Modifier.shadow(elevation)`로는 정확히 재현되지 않으므로, 필요하면 커스텀 drawBehind(blur, offset)로 구현합니다.

출처: `raw/connect/guide/docs-design/foundations/shadow.md`

## 6. Animation (로딩)
- Animation은 **사용자에게 상태를 설명하기 위한 목적**으로만 씁니다. 주행 중 시선을 끄는 장식 애니메이션은 금지입니다(design-principles 참고).
- 로딩 애니메이션 리소스(zip): https://document.pleos.ai/assets/files/Loading-a564bf9ddfbe2fa3c9640ec7708d9e1d.zip , 미리보기: https://document.pleos.ai/assets/images/Loading-6c839d1724fe66f5d9f2eada884aa3a7.svg

| Option | 사용 상황 |
| --- | --- |
| Linear | 정적인 로딩이 필요한 상황 |
| Tension | 페이지 로딩처럼 로딩 시간이 다소 길어지는 상황 |

출처: `raw/connect/guide/docs-design/foundations/animation.md`

## 7. Interaction

### Tension (Easing과 Duration)
- Easing 곡선은 **inline SVG 이미지로만** 제공되며 수치(cubic-bezier)가 텍스트로 없습니다(원문 페이지의 "Motion1.svg").
- Durations(구체적인 ms 값은 이미지에만 있음: https://document.pleos.ai/assets/images/Motion2-386ede7b47e2ec12f9f02fcfdb4dda82.svg):
  - **Short**: 작은 유틸리티성 전환
  - **Medium**: 화면의 중간 정도 영역을 가로지르는 전환
  - **Long**: 주로 Emphasized easing과 함께 쓰며, 큰 표현적 전환

### Gestures

| Type | 동작 |
| --- | --- |
| Pressed | Vehicle Setting, Climate, Apps를 선택하면 연결된 영역을 실행/종료 |
| Long Pressed | Vehicle Setting, Climate, Apps를 **2~3초** 누르고 있으면 편집 활성화 |
| Swipe | Pressed 후 상하좌우 이동 값이 있으면 지정된 동작 호출. 그 외는 Cancel. **일정 속도 이하이면 Drag**로 처리. 예: Window Bar Swipe Down은 앱 종료, Climate 숫자 Swipe Left/Right는 온도 조절 |
| Drag & Drop | Pressed 후 상하좌우 이동 값이 있으면 지정된 동작 호출. 그 외는 Cancel. **일정 속도 이상이면 Swipe**로 처리 |

### Button interaction (모든 터치 요소 공통)
- 버튼과 터치 가능한 요소에는 모두 **shrink effect**를 적용합니다.
  1. **Enabled**: 터치 전 기본 상태
  2. **Pressed**: **scale down 95%**와 모드(Light/Dark)별 배경색 변경. 배경이 없는 요소는 효과용 배경을 추가합니다(버튼마다 다른 배경색).
     - **Cancel**: 손가락이 터치 영역 밖으로 나가면 release하지 않고 취소합니다.
  3. **Release**: 터치 영역 안에서 손을 뗄 때 동작(앱 실행, 상태 변경 등)을 실행합니다.
- 그림: https://document.pleos.ai/assets/images/Interaction1-e19df91a37f0771555ae748eafd2bb6c.svg
- Compose 구현 힌트: `interactionSource.collectIsPressedAsState()`로 `graphicsLayer { scaleX = scaleY = if (pressed) 0.95f else 1f }`와 pressed 배경색을 적용합니다. 실행은 `clickable`의 onClick에서만 하면 Release-in-bounds와 cancel 규칙이 자연스럽게 지켜집니다.

### Window interaction (시스템 동작, 참고용)
- Drag start: Window bar를 누르기 시작하면 window가 수축합니다. Drag end: 손을 떼면 수축이 사라집니다.
- Reorder(앱 이동): 다른 window 너비의 **translateX 50% 이상** 이동하면 그 window 자리로 이동합니다.
- Close(앱 종료): window 높이 100% 기준 Y축 drag. 0~50% 구간과 51~100% 구간의 시각 표현이 다릅니다. **0~59%(기존 74%) 안에서 drag를 끝내면 원위치**, **60%(기존 75%) 이후에 끝내면 window 종료**입니다.

### End of scroll
- **Bounce effect**: 끝 영역(Page, Menu, Carousel, List 등)에서 끝에 닿았음을 알 수 있도록 bounce를 줍니다.
- **Range**: overscroll 최대 허용 범위는 가로나 세로 전체 스크롤 영역의 약 **2/5**입니다.

출처: `raw/connect/guide/docs-design/foundations/interaction.md`

## 8. Iconography
- 아이콘은 누구나 직관적으로 인지할 수 있어야 합니다(텍스트 라벨과 함께 쓰는 것을 권장, design-principles 참고).
- **Grid**: 기본 크기 **48 × 48dp** 그리드로 만듭니다.
- **Style**: 시인성을 위해 상황에 따라 **Filled / Outlined** 두 타입을 씁니다.
- **Variant**: 한 벌을 만든 뒤 필요한 크기로 조정합니다.
- 그리드와 타입 그림: icon1/icon2/icon3 — https://document.pleos.ai/assets/images/icon1-d2e1773bdd7d1a22c792e6161780a444.svg , https://document.pleos.ai/assets/images/icon2-a7cc729e14b9e9b17c26bf14ad96df04.svg , https://document.pleos.ai/assets/images/icon3-8e0194b720be8f53a88c6290b45f62fa.svg

출처: `raw/connect/guide/docs-design/foundations/contents-creation/iconography.md`

## 9. App Icon
- GNB, App library, App Market 등에서 쓰는 앱의 대표 이미지입니다.
- 원칙: (1) 그리드 시스템을 따릅니다. (2) **Foreground(로고, 주요 그래픽)와 Background(배경)를 분리**합니다. (3) 차량 브랜드별 디자인 정책에 따른 스타일을 적용합니다.
- **Android Adaptive Icon 형식**으로 만듭니다. 시스템 마스킹으로 브랜드별 모양이 적용됩니다. 규칙: https://developer.android.com/develop/ui/views/launch/icon_design_adaptive
- 그리드 https://document.pleos.ai/assets/images/app_icon_grid_system-94692ce52419d6e44bc4b0568b526f4b.svg , 마스킹 예시 https://document.pleos.ai/assets/images/app_icon_masking_sample-16272581abc51d9e5f0cb7167f14e86b.svg
- Figma 템플릿은 'Pleos Connect Design Assets'(https://document.pleos.ai/img/Pleos_Connect_design_assets_215.fig)에 있습니다.
- 구현: `mipmap-anydpi-v26/ic_launcher.xml`에 `<adaptive-icon>`으로 foreground/background(필요하면 monochrome)를 지정합니다.

출처: `raw/connect/guide/docs-design/foundations/contents-creation/app-icon.md`

## 10. 3D Assets
- 직관적인 사용성을 위해 **3D model data 활용을 권장**합니다. 샘플(Figma): https://www.figma.com/design/FYc6zTha14GLPvqsb1zmkD/%5BInternal%5D-Connect-2.0-Design-Kit(WIP)?node-id=29874-25562
- **모델**: Lowpoly 차량 데이터를 씁니다. 내외장 포함 **3Ds Max 기준 30 MB, FBX 기준 6 MB**입니다. Catia나 Alias에서 추출한 Highpoly는 Retopology를 거칩니다. Material과 object를 최적화해 **Drawcall을 최소화**합니다.
- **포맷**: **glTF**(Khronos, JSON 기반 3D 포맷)
- **엔진**: **Google Filament**(PBR, 모바일 우선, 멀티 플랫폼) — https://github.com/google/filament
- **Properties**: glTF animation은 이름과 속성 값으로 제어합니다(이름은 차량 특성에 따라 바뀔 수 있음). 3D 툴에서 animation key를 설정한 뒤 export하고, glTF animation 값이나 rotation 값을 바꿔 애니메이션합니다. Part별 제어에 영향이 없도록 정확한 part 이름을 붙입니다.
- **Use cases**: Climate(버튼 대신 실내 모델로 좌석별 공조 조작, Light/Dark 버전), Steering wheel(필요 영역 클로즈업), Seat Position(3D와 버튼 조작 연동)

### glTF Part Naming
- **Exterior**: Base `Body` / Door `Door_F_L`, `Door_F_R`, `Door_R_L`, `Door_R_R`, `Door_Sidemirror_L`(원문 표기 "Sidemirror Right"), `Door_Sidemirror_R`(원문 표기 "Sidemirror Left"), `Door_F_L_Window`, `Door_F_R_Window`, `Door_R_L_Window`, `Door_R_R_Window` / Trunk `Trunk`, `Trunk_shock_absorber` / Frunk `Frunk`, `Frunk_shock_absorber` / Wheel `Wheel_F_R`, `Wheel_F_L`, `Wheel_R_RL`(Rear All) / Charge `Charging_Port` / Roof `Sunroof_Glass`, `Sunroof_Shade`, `Roofglass_Shade` / Wiper `Wiper_F_R`, `Wiper_F_L`, `Wiper_R`
- **Interior**: `Body`, `Steering_Wheel`, `Seat_F_L_Top`, `Seat_F_L_Low`, `Seat_F_R_Top`, `Seat_F_R_Low`, `Seat_R_Top`, `Seat_R_Low`
- **Lamp** (Material name / Emissive / Alpha / etc):

| 상태 | Material | Emissive | Alpha | etc |
| --- | --- | --- | --- | --- |
| Head lamp All Off(Default) | `Head_L_All` | 0 | 1 | `Head_L_Light` glTF Hide |
| Head lamp All On | `Head_L_All` | 10 | 1 | `Head_L_Light` glTF Show |
| Rear lamp Positioning Off(Default) | `Rear_L_Pos` | 0 | 1 | cell |
| Rear lamp Positioning On | `Rear_L_Pos` | 2 | 1 | cell |
| Rear lamp Stop Off(Default) | `Rear_L_Stop` | 0 | 0.5 | cell |
| Rear lamp Stop On | `Rear_L_Stop` | 5 | 1 | cell |
| Turn signal All Off(Default) | `Turn_S_All` | 0 | 0 | cell |
| Turn signal All On | `Turn_S_All` | 5 | 1 | cell |

출처: `raw/connect/guide/docs-design/foundations/contents-creation/3d-contents.md`
