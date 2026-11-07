<!-- source: https://document.pleos.ai/docs/connect/guide/docs-design/components/page-navigation -->

# Page Navigation

#### 앱 서비스를 위해 필요한 주요 고객 경험 여정을 제공하기 위한 요소입니다.

![nav_anatomy.svg](https://document.pleos.ai/assets/images/nav_anatomy-dde4a448721cfc8aeb32c8bff5958a77.svg)

**1. Root** : Page Navigation 컴포넌트를 감싸는 최소단위 컨테이너 영역   
**2. App icon** : 앱 아이콘을 나타내는 시각 요소   
**3. Primary Menu** : 페이지 첫번째 카테고리를 그룹핑하는 Dropdown Menu   
**4. Secondary Menu** : 페이지 두번째 그룹에 대한 Dropdown Menu(Optional)   
**5. Suffix** : 페이지에 필요한 기타 메뉴에 대한 아이콘 메뉴, 앱의 목적에 따라 교체 가능

---

## Types

| Type | Value | Description |
| --- | --- | --- |
| Main | Boolean | 기본으로 제공 되는 메뉴 |
| Depth | Boolean | Depth 상태일 경우 True로 전환 (페이지 이동 시 Back 버튼이 나오는 경우) |

![nav_types.svg](https://document.pleos.ai/assets/images/nav_types-ef782b86be4ef1310dc090040c49e085.svg)

  

---

## Properties

| Properties | Value | Description |
| --- | --- | --- |
| Primary | App Icon, Suffix | Depth 상태일 경우 True로 전환(페이지 이동 시 Back 버튼이 나오는 경우) |
| Secondary | Boolean | 터치로 클릭 중인 상태일 때 True로 전환 |
| Subtitle | Boolean | 영역의 부가 정보를 제공하기 위한 경우 True로 전환 |

![nav_main.svg](https://document.pleos.ai/assets/images/nav_main-c7f217c641d8e69ef83df92906d81bcf.svg)
![nav_depth.svg](https://document.pleos.ai/assets/images/nav_depth-19328fbb0dafd29f1a488cfb1ca7daa1.svg)
