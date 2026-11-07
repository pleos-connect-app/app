# Pleos Connect 컴포넌트 가이드

원문: https://document.pleos.ai/docs/connect/guide/docs-design/components/
각 컴포넌트의 목적, Anatomy, States/Properties(원문 표 그대로), 동작 규칙을 정리합니다. 이미지 URL의 prefix는 `https://document.pleos.ai/assets/images/`이며 표에서는 파일명만 적었습니다.

> **주의**: 컴포넌트의 **dp 크기(높이, 패딩, 반경), 색상**은 거의 전부 이미지(SVG)로만 제공됩니다. 텍스트로 확인된 수치는 Toast(패딩 24, max width 792, 텍스트 max 712), Status bar(SP0/SP1 사이 safe bounds 40dp), App library(한 줄 최대 7개), OSD(2초 후 자동 숨김)뿐입니다. 크기가 필요하면 아래의 size/property 이미지나 Figma 에셋을 확인하고, **추측하지 마세요.**

## TL;DR — 꼭 지킬 규칙
- **Status bar, GNB, App library, Driving view, Vehicle notification, OSD, Gleo는 시스템 UI**이므로 앱이 다시 만들지 않습니다. 앱이 직접 쓰는 컴포넌트는 Button류, Field, Keypad, Selection(Checkbox/Radio/Switch), Controller(Slider/Stepper), Tabs, Dropdown, Page Navigation, Popup, Toast, Spinner, Scroll bar, Indicator입니다.
- 모든 인터랙티브 컴포넌트는 공통 상태 **Pressed / Disabled**를 갖고, 버튼은 **Loading**(로더 표시, 조작 불가)을 지원합니다. Pressed는 scale 95%와 배경색 변경(foundations 참고)입니다.
- 앱 상단 내비게이션은 **Page Navigation**(App icon, Primary/Secondary Dropdown, Suffix 아이콘)을 쓰고, 하위 화면은 Depth 타입(Back 버튼)을 씁니다.
- **Toast**: 앱 패널 안에서 side 24, bottom 24 패딩, 전체 max width 792, 메시지 max width 712입니다. 짧은 일시 알림에만 씁니다.
- **Controller**는 Min/Max에 도달하면 해당 방향을 Disabled로 바꿉니다. 밝은 배경이나 구분이 필요한 곳에서는 Floating 스타일을 씁니다.

---

## 0. 어떤 컴포넌트를 쓸까 (Quick map)

| 필요 | 컴포넌트 | 비고 |
| --- | --- | --- |
| 주요 행동 유도(텍스트 버튼) | Basic button | 명확한 영역과 라벨, 위계(hierarchy)와 크기 변형 |
| 누구나 아는 아이콘 하나로 행동 | Icon button | Style Basic/Plain/Circle, Size L/M/S |
| 부가 정보 링크 | Text button | Size M/S, 앞뒤 아이콘 |
| 켜고 끄는 옵션 버튼(여러 옵션 중 상태 인지) | Toggle button | Floating 옵션 |
| 단일 설정 On/Off | Switch | 즉시 상태 전환 |
| 다중 선택 | Checkbox | |
| 여러 옵션 중 하나(배타적) | Radio button | |
| 범위 값(볼륨, 온도) 연속 조절 | Continuous slider | Stepper 버튼 포함 가능 |
| 중앙 기준 ± 조절 | Centered slider | |
| 단계 값 증감 | Stepper | i++ / i-- |
| 날짜/시간 선택 | Spinner (picker) | Drag 선택, Type Date/Time |
| 콘텐츠/페이지 전환(2개 이상) | Tabs (Segmented menu) | Type Box/Line, Fitted |
| 메뉴 이동, 속성 선택 | Dropdown | Prefix/Suffix, Size M/S |
| 앱 상단 타이틀과 메뉴 | Page Navigation | Main / Depth |
| 텍스트 입력 | Text Field + Keypad | Type Text/Tel/Url/Email/Password |
| 숫자 입력 | Number Field + Keypad(numeric) | Size L/M |
| 확인/취소가 필요한 메시지 | System Popup | Size L/M, Button Align |
| 일시적인 짧은 알림 | Toast / Toast Popup | 패딩 24, max 792 |
| 콘텐츠 스크롤 위치/탐색 | Scroll bar | |
| 값 목록 중 현재 위치 표시 | Indicator | Size Wide/Narrow |
| 앱 알림(시스템 알림센터) | System Notification | OS 제공. 앱은 Android notification으로 발행 |
| 홈 화면 위젯 | Widgets | Handle, Pagination, Title/Action, Information |

---

## 1. Actions

### 1.1 Basic button
- **목적**: 가장 기본적인 버튼입니다. 명확한 영역과 텍스트로 사용자의 행동을 유도합니다.
- **Anatomy**: 1 Root(최상위 컨테이너), 2 Label, 3 Icon(Prefix), 4 Icon(Suffix). 아이콘은 Label을 시각적으로 보충합니다.
- **States/Properties**:

| State | Value | Description |
| --- | --- | --- |
| Pressed | Boolean | 터치로 클릭 중일 때 True |
| Disabled | Boolean | True면 조작 불가 |
| Loading | Boolean | True면 버튼 안에 Loader가 표시되고 조작 불가 |

- 위계(hierarchy)와 크기는 이미지에만 있습니다: basic_state-4ec1963c8af4268285a73d71eae42d18.svg, basic_hierarchy-f5942b136c40fa910ceb0670c5c643e3.svg, **basic_size-033b09c8bbef4d1776a3f4c7c70a49d0.svg**
- 규칙: 0.25초 안에 응답할 수 없는 동작이면 `Loading`을 켭니다(Glanceability 원칙).

출처: `raw/connect/guide/docs-design/components/actions/basic-button.md`

### 1.2 Icon button
- **목적**: 누구나 직관적으로 알아볼 수 있는 아이콘일 때만 씁니다. 애매한 의미이면 라벨이 있는 버튼을 씁니다.
- **Anatomy**: 1 Root, 2 Icon
- **States**: Pressed(Boolean, 기본 False), Disabled(Boolean). 원문의 Pressed 설명은 "Drag 가능한 window일 때 True로 전환"이라고 되어 있는데, 원문 복붙 오류로 보입니다.
- **Properties**: Style `Basic, Plain, Circle` / Size `Large, Medium, Small`
- 이미지: icon_type1-5e4c68f93730504d172ac7049169307b.svg, icon_type2-18cff76e4dc890ec373e860bf76a84f8.svg, icon_type3-e52d5a085ce8709d6023faa7b39a19ab.svg

출처: `raw/connect/guide/docs-design/components/actions/icon-button.md`

### 1.3 Text button
- **목적**: 주로 **부가 정보로 가는 링크**에 씁니다. 앞뒤 아이콘으로 전달력을 높입니다.
- **Anatomy**: Root, Label, Icon(Prefix), Icon(Suffix)
- **States**: Pressed / Disabled / Loading(Basic button과 같음)
- **Properties**: Size `Medium, Small`
- 이미지: text_state-0d9bea89b761e379baa4e21d41ed0e46.svg, text_property-771eff703f78e93614d01e9670f163df.svg

출처: `raw/connect/guide/docs-design/components/actions/text-button.md`

### 1.4 Toggle button
- **목적**: On/Off가 되는 버튼입니다. **여러 옵션을 제공할 때 각 상태를 알아보기 쉽습니다**(예: 공조 옵션 버튼 묶음).
- **Anatomy**: Root, Label, Icon(Prefix), Icon(Suffix)
- **States**: Toggled(Boolean, 기본 False, 활성화되면 True), Pressed(Boolean, 기본 False), Disabled
- **Properties**: Floating(Boolean, 배경 스타일에 따라 Shadow 또는 컬러로 표현) / Size `Large, Medium, Small`
- 이미지: toggle_state-ac74e8fc7902462ab2e74b5950d96c63.svg, toggle_property-5f7bb328641d7572fc4d70ff552b0579.svg

출처: `raw/connect/guide/docs-design/components/actions/toggle-button.md`

### 1.5 Keypad
- **목적**: 숫자, 텍스트 등 정보 입력용 키패드입니다. 시스템 키보드도 이 형식이며, 키보드는 System UI 레이어로 앱을 가릴 수 있습니다.
- **Anatomy**: Root, Label, Icon(Prefix), Icon(Suffix)
- **States**: Pressed(Boolean, 기본 False, 터치 중 True), Disabled
- **Properties**: Keypad `Primary, Secondary, Special`(키 역할에 따른 타입) / Keyboard `Text, Voice`(키패드 입력 또는 음성 입력)
- 레이아웃 이미지: keypad_numberic-ce4517fec9580027f3560673b162512b.svg(숫자), keypad_qwerty-21cdcb430bd3fc41e33f5fcb10a02724.svg(QWERTY), keypad_keyboard-0959fbeb50fcdc44d487bc6324129e82.svg, keypad_state-aad869936628b30fa601fe2aee2fc41a.svg
- 규칙: 텍스트 입력에는 Voice 입력 옵션을 함께 고려합니다(주행 안전, 음성 인터랙션 원칙).

출처: `raw/connect/guide/docs-design/components/actions/keypad.md`

---

## 2. Fields

### 2.1 Text Field
- **목적**: 텍스트, 비밀번호, 이메일 등 텍스트 입력
- **Anatomy**: 1 Root, 2 Field(입력 영역), 3 Cursor, 4 Place Holder(입력 맥락 라벨), 5 Suffix(값의 맥락을 보여 주는 텍스트나 아이콘, Optional)
- **States**:

| State | Value | Description |
| --- | --- | --- |
| Value | String | 입력된 텍스트 |
| Focused | True / False | 터치나 음성으로 접근해 필드를 인식하고 있을 때 True |
| Disabled | True / False | 입력과 접근 불가. Focused가 작동하지 않음 |
| Read Only | True / False | 값에 접근할 수는 있지만 변경 불가. Focused는 작동 |
| Invalid | True / False | 입력 값의 유효성 여부 |

- **Properties**: Type `Text / Tel / Url / Email / Password` / Size `Medium, Small`(원문 오타 "Samll")
- Typing interaction 이미지: typing_interaction-cf69101d3073847deddfc518ad0bf347.svg. 그 외 text_status-536367f063e7ef067ab7fa4a1d8e0339.svg, text_type-bb0b90650535660e2c41bf2924387045.svg
- Compose 매핑: Type은 `KeyboardOptions(keyboardType = Text/Phone/Uri/Email/Password)`와 비밀번호 `visualTransformation`, Invalid는 `isError`, Read Only는 `readOnly = true`로 대응합니다.

출처: `raw/connect/guide/docs-design/components/fields/text-field.md`

### 2.2 Number Field
- **목적**: 숫자 입력
- **Anatomy**: 1 Root, 2 Label(입력된 값), 3 Cursor, 4 Field
- **States**: Value / Focused / Disabled / Read Only / Invalid(Text Field와 같음)
- **Properties**: Size `Large, Medium`
- 이미지: num_states-a00fd36843fa7ababdd575e86a33a275.svg. 크기 이미지(num_size)는 inline SVG라 URL이 없으므로 원문 페이지에서 확인합니다.

출처: `raw/connect/guide/docs-design/components/fields/number-field.md`

---

## 3. Selections

### 3.1 Checkbox
- **목적**: **다중 선택**이 가능한 옵션
- **Anatomy**: 1 Root(최소 단위 컨테이너), 2 Control(상태 시각 요소), 3 Icon
- **States**: Checked(Boolean), Pressed(Boolean), Disabled(Boolean)
- 이미지: Checkbox2-7fe36bdcf09b4a531b37e865114d687b.svg, Checkbox4-7b5b179e93cd4a291f761a84ec258c7f.svg

출처: `raw/connect/guide/docs-design/components/selections/checkbox.md`

### 3.2 Radio button
- **목적**: 하나만 고를 수 있는 독립 선택 옵션
- **Anatomy**: 1 Root, 2 Control
- **States**:

| State | Value | Description |
| --- | --- | --- |
| Value (Selected) | Num / Null | Null은 선택 안 됨, Num은 선택된 Radio의 순서 값 |
| Pressed | Boolean | 터치 중 True |
| Disabled | Boolean | 조작 불가 |
| Invalid | Boolean | 유효하지 않은 값 |

- 이미지: radio2-2e12d4bd8036234c401f5f6601403854.svg, radio3-bc049a112a883573df01e43e96332e31.svg

출처: `raw/connect/guide/docs-design/components/selections/radio.md`

### 3.3 Switch
- **목적**: On/Off로 상태를 바꿀 때 씁니다(설정 항목).
- **Anatomy**: 1 Root, 2 Handle, 3 Track
- **States**: Checked(On/Off), Pressed(Boolean), Disabled(Boolean), Invalid(Boolean)
- 이미지: Switch2-8cc602a4eeae68600f5294eff8302727.svg, Switch3-1f6118f2faea73559f326ab5cc890c81.svg
- Switch와 Toggle button의 구분: 리스트나 설정의 단일 On/Off는 Switch, 버튼 형태로 여러 옵션의 상태를 보여 줄 때는 Toggle button을 씁니다.

출처: `raw/connect/guide/docs-design/components/selections/switches.md`

---

## 4. Controllers (Slider / Stepper)
- **목적**: 최솟값과 최댓값이 있는 범위 안에서 volume, temperature 같은 속성을 **슬라이더나 클릭**으로 조작합니다.
- **States**: Pressed or Dragging(True/False, Handle을 누르기 시작할 때)
- **Options**:

| Option | Value | Description |
| --- | --- | --- |
| Disabled | True / False | **Min 또는 Max 값에 도달하면 True** |
| MinValue | Number | 최솟값 |
| MaxValue | Number | 최댓값 |
| Step | Number | 값을 제어하는 단계 |

### 4.1 Continuous Slider
- **Anatomy**: 1 Root, 2 Control(track과 handle 컨테이너), 3 Handle, 4 Track, 5 Icon(Prefix, Optional), 6 Icon(Suffix), 7 **Stepper**(버튼으로 handle 제어)
- **Slider 스타일**: **Basic**(일반) / **Floating**(밝은 배경이나 배경과 구분이 필요한 곳)
- **Volume 단계 표현**(아이콘이나 시각 단계): Max 100%, High 76~100%, Mid 26~75%, Low 1~25%, Min 0%
- **Optional**: Label True/False, Icon True/False
- 이미지: ContinuousSlider_Anatomy-b639aeb4f9a632abd58f64d7f9dcf28a.svg, Slider_button-4c3036312af7c89f7cee83d904a2c6ac.svg, Slider_Control-c67b8a5b0d5893276d1af7bc6f9c8c07.svg(나머지는 inline SVG)
- 규칙: 주행 중 정밀한 드래그는 어렵습니다. Stepper 버튼(±)을 함께 제공해 탭만으로도 조절할 수 있게 합니다.

### 4.2 Centered Slider
- 중앙 기준으로 양방향 조절(예: 밸런스)
- **Anatomy**: Root, Control, Handle, Track, Stepper
- 이미지: CenteredSlider_Anatomy-c9a425c6d7969d4817cfcebfb6abdc09.svg, Slider_Button-ffaec97079bb3fe3acb8d6f3562dbc74.svg, Slider_State-6f14d94ca858e32dcc6cae9d3d481028.svg, Slider_Control-f9a31c7e3b3f429155504cddf10417ea.svg

### 4.3 Stepper
- **Anatomy**: 1 Root, 2 Current value(값과 Step 버튼 컨테이너), 3 i++(증가), 4 i--(감소)
- 이미지: Stepper_Anatomy-96296503eb2f4947c0c06451c2b4f95d.svg, Stepper_State-306f018ec6d2f9697626cb5d69f3f35a.svg, Stepper_Control-c966b928c40fb331992f9f593c2d0505.svg
- 규칙: Max에서는 i++를, Min에서는 i--를 Disabled로 만듭니다(Options의 Disabled 규칙).

출처: `raw/connect/guide/docs-design/components/controllers.md`

---

## 5. Tabs (Segmented menu)
- **목적**: 2개 이상의 콘텐츠나 기능 옵션을 전환합니다.
- **Anatomy**: 1 Root, 2 Tab Item(전환할 콘텐츠/페이지 그룹), 3 Label, 4 Selected indicator, 5 Tab Container
- **States**: Enable(Boolean, 기본 터치 가능 상태), Pressed(Boolean), Selected(Boolean)
- **Properties**:

| Property | Value | Description |
| --- | --- | --- |
| Type | Box, Line | 두 가지 Tab 스타일 |
| Fitted | Boolean | True면 Tabs 너비에 맞춰 Tab들이 같은 간격으로 늘어남. False면 Left Aligned |

- 이미지: Segmented1-043f0f166f9521db2c54d0ddcfab6962.svg, Segmented7-d19cfa2c49a16afc12931f6eeeb28c5c.svg

출처: `raw/connect/guide/docs-design/components/segmented-menu.md`

## 6. Dropdown
- **목적**: 메뉴 이동이나 메뉴 속성 변경
- **Anatomy**: 1 Root, 2 Label, 3 Prefix(앞쪽 옵션), 4 Suffix(뒤쪽 옵션), 5 Container(항목 목록)
- **States**: Opened(Boolean), Pressed(Boolean), Disabled(Boolean)
- **Properties**: Prefix `App Icon, Icon` / Suffix `Switch, Icon` / Size `Medium, Small`(Dropdown과 컨테이너 항목 크기)
- 이미지: Dropdown1-d5de721fec5e32023e46ffcd1476eaa6.svg, Dropdown2-d217797f4ac6431794115ba67079e66b.svg, Dropdown3-1012a5c327a62b7da854f0446fa6d2c4.svg, Dropdown4-82b35f78f6bb9033cbb177b310d360c8.svg, Dropdown6-2698e8b871e7cbc866ed2b1df1ccd504.svg

출처: `raw/connect/guide/docs-design/components/dropdown.md`

## 7. Page Navigation (앱 상단 바)
- **목적**: 앱의 주요 사용자 여정을 위한 상단 내비게이션 요소입니다.
- **Anatomy**: 1 Root, 2 App icon, 3 Primary Menu(첫 번째 카테고리를 묶는 Dropdown Menu), 4 Secondary Menu(두 번째 그룹 Dropdown, Optional), 5 Suffix(기타 아이콘 메뉴, 앱 목적에 따라 교체 가능)
- **Types**: Main(Boolean, 기본 메뉴) / Depth(Boolean, 페이지 이동 시 Back 버튼이 나오는 경우 True)
- **Properties**(원문 그대로): Primary `App Icon, Suffix` / Secondary Boolean / Subtitle Boolean(영역의 부가 정보를 보여 줄 때 True). 원문의 설명 칸은 다른 행에서 복사된 것으로 보입니다.
- 이미지: nav_anatomy-dde4a448721cfc8aeb32c8bff5958a77.svg, nav_types-ef782b86be4ef1310dc090040c49e085.svg, nav_main-c7f217c641d8e69ef83df92906d81bcf.svg, nav_depth-19328fbb0dafd29f1a488cfb1ca7daa1.svg
- 규칙: 최상위 화면은 Main(App icon과 Dropdown 메뉴)을 쓰고, 하위 화면은 Depth(Back 버튼)를 씁니다.

출처: `raw/connect/guide/docs-design/components/page-navigation.md`

## 8. Bar (Window bar / Scroll bar)
- **목적**: 앱 window의 크기와 위치 조정, 콘텐츠 조작
- **States**:

| State | Value | Description |
| --- | --- | --- |
| Variant | Light / Dark | 앱이 정한 타입에 따라 전환 |
| Dragging | True / False | Drag 가능한 window일 때 True |
| Disabled | True / False | True면 handle이 안 보이고 drag 불가 |
| Pressed | True / False | Handle을 잡고 drag를 시작할 때 |

- **Types**: **Window bar**(window 크기 조정, 시스템이 제공) / **Scroll bar**(콘텐츠 탐색)
- Anatomy(둘 다 같음): 1 Root, 2 Control(Handle과 **최소 drag 영역** 컨테이너), 3 Handle
- 이미지: Bar-e3827782629c2e992d4a32515439e71b.png, WindowBar_Light-5d57a5154a3d79cf47fa69e32f8999b7.svg, WindowBar_Dark-6a6cf658545b7058a29fef5221d12b25.svg, ScrollBar_Light-41c720f33ffb6b1e9eafa27e7716041b.svg, ScrollBar_Dark-a2221d396657e65e2822a4dc2e95b027.svg
- 앱 관점: Window bar는 시스템 소유입니다(앱 상단을 침범하지 않도록 레이아웃 여백을 확인합니다). Scroll bar는 앱 배경에 맞춰 Light/Dark variant를 고릅니다.

출처: `raw/connect/guide/docs-design/components/bar.md`

## 9. Indicator
- **목적**: 값 목록에서 현재(Present) 값과 이전/다음 값을 보여 줍니다(페이지 인디케이터류).
- **Anatomy**: 1 Root, 2 Present Value, 3 Next/Prev Value
- **States**: Present(Boolean, 현재 값이면 True) / **Option**: Size `Wide / Narrow`
- 이미지: Indicator1-6bd2529562427a44d6a9dc6f558a4aac.svg

출처: `raw/connect/guide/docs-design/components/indicator.md`

## 10. Spinner (Picker)
- **목적**: **Drag로 여러 옵션 중 하나를 선택**합니다.
- **Anatomy**: 1 Root, 2 Selection Value(현재 선택 값), 3 Contents(선택 가능한 값 목록)
- **Type**: Date(날짜) / Time(시간)
- 이미지: Picker1-58af05a862c1abc9f94795bff7e2dea1.svg, Picker4-bf618a18ba4a30687201cd9d0a5a1c77.svg, Interaction Guide Picker7-a4f951ca2d6ff641942d33f27291f2df.svg
- 참고: 이 "Spinner"는 로딩 스피너가 아니라 **휠 피커**입니다. 로딩 표시는 foundations의 Animation(Linear/Tension)을 씁니다.

출처: `raw/connect/guide/docs-design/components/spinner.md`

---

## 11. Popups / Toast

### 11.1 System Popup
- **목적**: 앱 안에서 쓰는 기본 팝업입니다. 맥락에 맞게 Body에 콘텐츠를 넣을 수 있습니다.
- **Anatomy**: 1 Root, 2 Title, 3 Description(Optional), 4 Confirm Action(확인 또는 다른 진입점 링크), 5 Cancel Action(Optional)
- **Properties**:

| Property | Value | Description |
| --- | --- | --- |
| Header | Icon, SubTitle, Description | True면 Header 노출 |
| Body | Boolean | True면 Header와 버튼 사이에 콘텐츠 노출 |
| Size | Large, Medium | 팝업 크기 |
| Button Align | Single / Vertical / Horizontal | 버튼 정렬 |

- 이미지: pop_anatomy-cf6b8f1a8021cd589701650743c234fd.svg, pop_size-f9d32156cd7f49791cef63ce50637d1a.svg, pop_align-01abb7df1e38e4f13e989b4e2d49b9bb.svg, pop_contents-8cde2011d8f2eea43c667df64393a546.svg
- 참고: Gleo 대화는 CCP 정책에서 팝업과 같이 처리됩니다(팝업 중 tilt 제한, 우선 포커스). 앱 팝업도 CCP focus를 받을 수 있게 합니다.

출처: `raw/connect/guide/docs-design/components/popups/system-popup.md`

### 11.2 Toast / Toast Popup
- **목적**: 앱 운영 중 필요한 **일시적인 알림**
- **Anatomy**: 1 Root(최소 단위 컨테이너), 2 Toast message
- **수치**:
  - App panel 안에서 **side padding 24, bottom padding 24**(Toast Popup 페이지에서는 "24dp 공간을 두고 Max로 제공"으로 표현)
  - Toast 전체 **max width 792**, Message text **max width 712**
- 이미지: Toast1-d58eedb1d60a2a692a8212985c00b939.svg, Toast2-e715ceeaf2df6934ffc686847d52f2bd.svg, Toast3-6c8f81619ade90fca108bbdec4b3c9b8.svg
- 표시 시간(duration)은 원문에 없습니다.

출처: `raw/connect/guide/docs-design/components/toast.md`, `raw/connect/guide/docs-design/components/popups/toast-popup.md`

---

## 12. Notifications

### 12.1 System Notification (OS 제공)
- **Anatomy**: 1 Root, 2 Imagery, 3 Header(설명 텍스트), 4 Action(확인 또는 행동 유도 버튼)
- **Types**: 1 App icon(앱 알림), 2 Profile(전화/문자, 앱 아이콘 표시 가능), 3 Image(내비게이션 정보나 기타), 4 Action
- **Properties**: Button Align `Single / Vertical / Horizontal`
- 이미지: system_noti-e41f31fb0032bc4e46546185f09a80a7.svg, system_types-f6f5bf10d3f0d557fc772cfb9b02d5d3.svg, system_properties-b247b148c418c1a6d06a354154138bd1.svg
- 규칙(원칙 문서): 운전자에게 필요한 것만 알리고 광고는 금지입니다. 텍스트는 짧게 씁니다(영문 120자/24단어 이하).

출처: `raw/connect/guide/docs-design/components/notification/system-notification.md`

### 12.2 Vehicle Notification (Driving Notification, 시스템 전용)
- **Anatomy**: 1 Root, 2 Telltale(관련 telltale 아이콘), 3 Title text, 4 Description text, 5 Guide image
- Properties와 Position은 이미지에만 있습니다: noti_driving1-2e4a441a5256fe5c9ce6b78841180d8e.svg, noti_driving3-05b2b27d50f50309c49c5d827f771206.svg, noti_driving4-f10b192e396a55f49d7f3d1b70083bb6.svg

출처: `raw/connect/guide/docs-design/components/notification/vehicle-notification.md`

---

## 13. 시스템 UI 컴포넌트 (앱이 만들지 않음, 레이아웃과 동작 이해용)

### 13.1 Status bar
- 차량 주행과 주요 시스템 상태를 보여 주는 상단 영역입니다.
- **Anatomy**:
  1. **SP0 Static**: 우선순위 높음, 항상 표시(Door Lock, Search)
  2. **SP0 Dynamic**: 우선순위 높음, 이벤트성(Notification center, In-call status, privacy indicator)
  3. **SP1 Dynamic**: 우선순위 낮음, 이벤트성(Download, Update, WPC, Hi-Pass, Sound). 3-1 **Hotspot** 버튼은 SP1 Static의 network 오른쪽에 배치
  4. **SP1 Static**: 우선순위 낮음, 항상 표시(Profile, bluetooth, network)
  5. **Time**: 시스템 설정에 따라 24h 또는 12h
  6. **PAB**: 보조석 에어백 활성 상태. 차량 하드웨어에 PAB 표시가 없을 때만 IVI에 표시
  7. **More Menu**: 영역에 다 들어가지 않는 아이콘
- SP0과 SP1을 구분하기 위해 **40dp safe bounds를 right padding**으로 둡니다. SP1 dynamic 공간이 부족하면 more 메뉴로 이동하고, SP0 dynamic이 늘어나면 SP1 dynamic 영역이 줄어듭니다.
- **Static icon states**: Pressed(Boolean, False), Selected(Boolean, False) / **Dynamic icon**: Activate(Boolean, False, Static indicator 왼쪽에 발생 순서대로 표시)
- **Menu panels**: 상태 메뉴를 누르면 부가 기능 패널(장치 연결, 프로필 변경, 알림 등)이 열립니다. States는 Pressed와 Selected입니다.
- 이미지: statusbar_anatomy-dcf8eb47b4d0999c45e9daba9cc31baa.svg, statusbar_anatomy_1-be027b658e2d222fe2fb288d6816daff.svg, statusbar_anatomy_2-8489c4605ea287fa12b4785c0fe17766.png, menu_list_panel-fd1bc93bf97b19a29e908c6e1e95aa3e.svg

출처: `raw/connect/guide/docs-design/components/status-bar.md`

### 13.2 GNB (Global Navigation Bar)
- 필수 기능을 빠르게 실행하는 하단 바입니다. 영역은 1 운전석 Vehicle control, 2 Application control, 3 조수석 Vehicle control입니다.
- **Control area**: 1 Vehicle settings(편집 불가), 2 Climate control(탭하면 Climate panel이 열리고 다시 탭하면 닫힘), 3 FR/RR Def(토글, 터치하면 미니 팝업으로 FR/RR 개별 제어), 4 Editable slot(기본: 운전석 시트 열선 또는 통풍), 5 Fixed app(Navigation 고정, Center 영역 안에서 위치 이동 가능), 6 Editable slots(**최대 3개**, 앱이나 Climate shortcut. 기본: Bluetooth Music, Phone, App Market), 7 App library(토글로 패널 열기/닫기), 8 Recent apps(**최근 2개**, 최신이 왼쪽, Editable slot에 있는 앱은 제외), 9 조수석 Editable slots(기본: 조수석 시트 통풍과 열선), 10 조수석 Climate control, 11 조수석 Editable slot(기본: Auto)
- **편집**: Editable slot을 long-press(또는 App library에서 climate shortcut이나 app icon을 long-press)하면 편집 모드로 들어갑니다. Fixed app은 삭제할 수 없고 이동만 됩니다(Fixed app이 없는 사양에서는 Editable slot이 1개 늘어남). Recent apps는 편집할 수 없습니다. App library의 Climate shortcut은 이동만 되고 삭제는 안 됩니다(GNB에 이미 있으면 uneditable). App은 삭제와 이동이 됩니다.
- **OSD**: 스티어링 휠 물리키로 조작할 때 바뀐 상태를 보여 주는 패널입니다. Connect-S에서는 GNB 영역에, Connect-W에서는 GNB 오른쪽에 표시됩니다. **추가 조작이 없으면 2초 후 자동으로 사라집니다.** Case: Media & Sound, Vehicle control(Wiper, Light, Seat Position), Distance control(앞차와의 간격)
- 앱 관점: 앱 아이콘이 GNB slot에 들어갈 수 있으므로 App icon 규격(Adaptive Icon)을 지킵니다.
- 이미지: gnb_anatomy-8ea8f67ed9341b1535278af5a5b665bb.svg, gnb_vehicle_control-c84a9985787267bb620657693d4a7fe2.png, gnb_edit-720361fdd4e40b755a9520b21d3f293d.png, gnb_prop1~4, gnb_osd1~6

출처: `raw/connect/guide/docs-design/components/gnb.md`

### 13.3 App library
- 설치된 앱과 차량 제어 버튼이 모인 런처 패널입니다.
- **Anatomy**: 1 Root, 2 Editing guide(편집 모드에서 표시), 3 Complete button(순서 변경이나 삭제 후 저장), 4 Climate control(Heat, ventilation 등), 5 App(내장 앱과 다운로드 앱), 6 Pagination(페이지당 노출 수를 넘으면 표시), 7 Divider(Climate과 App 구분)
- **Normal**: Connect-S 기준 **한 줄에 최대 7개**입니다. 2줄을 넘으면 좌우 이동 인디케이터가 나타납니다. 위쪽은 차량 제어, 아래쪽은 앱 실행 메뉴입니다.
- **Editing**: 기본 앱이나 GNB에 있어 이동할 수 없는 앱은 비활성화됩니다. 차량 제어와 앱은 각자 지정된 영역으로만 이동합니다. 드래그 중 이동할 수 없는 영역은 비활성화됩니다. 국가 법규에 따라 Frost/Defrost가 GNB에 필수로 들어갈 수 있습니다.
- 이미지: library_anatomy-1a11a9b71d9dedaf923b74fcb82c4ad6.svg, library_behavior-f5ceb52e0b90ec92ff3165ed4a34f1fb.svg, library_edit_app-36e67fe8e67fd2cdb65d22432c429033.svg

출처: `raw/connect/guide/docs-design/components/app-library.md`

### 13.4 Widgets
- **Anatomy**: 1 Root, 2 Handle(아래로 끌어내려 위젯 숨김), 3 Pagination(위젯 개수와 현재 순서), 4 Title/Action area, 5 Information area(주요 정보)
- Properties와 Interaction은 **이미지에만** 있습니다: Widget1-d50b04650838c81607b91de7c3172924.svg, Widget2-baf4749a6bc814f28196a3571f80caec.png, Widget3-b99de7ee37d94ed63a17e55df4d8d49e.png, Widget4-2d7e47425707180a20d6b80ff54db06a.png

출처: `raw/connect/guide/docs-design/components/widgets.md`
