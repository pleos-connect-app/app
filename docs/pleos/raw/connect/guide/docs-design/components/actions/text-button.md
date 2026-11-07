<!-- source: https://document.pleos.ai/docs/connect/guide/docs-design/components/actions/text-button -->

# Text button

#### 텍스트 버튼은 부가 정보에 대한 링크를 위해 주로 사용되며, 앞/뒤 아이콘을 통해 정보 전달력을 강화할 수 있습니다.

![text_button_anatomy.svg](https://document.pleos.ai/assets/images/text_button_anatomy-c8ea15ef3060d5024b71993e1ff8f1bc.svg)

**1. Root** : 버튼을 감싸는 최상위 컨테이너 영역   
**2. Label** : 버튼 레이블   
**3. Icon**(Prefix) : Label에 대한 시각적 부연설명을 하는 아이콘 영역   
**4. Icon**(Suffix) : Label에 대한 시각적 부연설명을 하는 아이콘 영역

---

## States

| State | Value | Description |
| --- | --- | --- |
| Pressed | Boolean | 터치로 클릭 중인 상태일 때 True로 전환 |
| Disabled | Boolean | True일 경우, Button이 제어불가능한 상태 |
| Loading | Boolean | True일 경우, Button내 Loader가 표시되고 제어불가한 상태가 됨 |

![text_state.svg](https://document.pleos.ai/assets/images/text_state-0d9bea89b761e379baa4e21d41ed0e46.svg)

  

---

## Properties

| State | Value | Description |
| --- | --- | --- |
| Size | Medium, Small | Button의 사이즈 옵션을 선택하여 사용할 수 있습니다. |

![text_property.svg](https://document.pleos.ai/assets/images/text_property-771eff703f78e93614d01e9670f163df.svg)
