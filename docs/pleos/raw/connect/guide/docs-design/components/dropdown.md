<!-- source: https://document.pleos.ai/docs/connect/guide/docs-design/components/dropdown -->

# Dropdown

#### 메뉴의 이동이나 원하는 메뉴 속성 변경을 위한 요소입니다.

![Dropdown1.svg](https://document.pleos.ai/assets/images/Dropdown1-d5de721fec5e32023e46ffcd1476eaa6.svg)

**1. Root** : Dropdown 컴포넌트를 감싸는 최상위 컨테이너 영역   
**2. Label** : 버튼 레이블   
**3. Prefix** : 메뉴 앞쪽에 제공되는 옵션 영역   
**4. Suffix** : 메뉴 뒷쪽에 제공되는 옵션 영역   
**5. Container** : Dropdown 요소를 담고 있는 영역

  

---

## States

| States | Value | Description |
| --- | --- | --- |
| Opened | Boolean | Dropdown 컨테이너가 열린 상태일 때 True로 전환 |
| Pressed | Boolean | 터치로 클릭 중인 상태일 때 True로 전환 |
| Disabled | Boolean | True일 경우, Dropdown이 제어 불가능한 상태 |

![Dropdown4.svg](https://document.pleos.ai/assets/images/Dropdown4-82b35f78f6bb9033cbb177b310d360c8.svg)
![Dropdown2.svg](https://document.pleos.ai/assets/images/Dropdown2-d217797f4ac6431794115ba67079e66b.svg)

  

---

## Properties

| Property | Value | Description |
| --- | --- | --- |
| Prefix | App Icon, Icon | 메뉴 앞쪽에 제공되는 옵션 |
| Suffix | Switch, Icon | 메뉴 뒷쪽에 제공되는 옵션 |
| Size | Medium, Small | Dropdown, 컨테이너 아이템의 사이즈 옵션 |

![Dropdown3.svg](https://document.pleos.ai/assets/images/Dropdown3-1012a5c327a62b7da854f0446fa6d2c4.svg)
![Dropdown6.svg](https://document.pleos.ai/assets/images/Dropdown6-2698e8b871e7cbc866ed2b1df1ccd504.svg)
