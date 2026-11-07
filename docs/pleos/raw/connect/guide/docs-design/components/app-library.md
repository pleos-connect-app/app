<!-- source: https://document.pleos.ai/docs/connect/guide/docs-design/components/app-library -->

# App library

#### App library는 고객이 앱 마켓을 통해 설치한 다양한 앱 서비스가 모여있는 영역입니다.

![library_anatomy.svg](https://document.pleos.ai/assets/images/library_anatomy-1a11a9b71d9dedaf923b74fcb82c4ad6.svg)

**1. Root** : 컴포넌트를 감싸는 최상위 컨테이너 영역   
**2. Editing guide** : Editing mode에서 표시   
**3. Complete button** : Control button 및 App 순서 변경 및 삭제 후 수정 내용을 저장   
**4. Climate control** : Heat, ventilation 등 공조 컨트롤 버튼 영역   
**5. App** : Embed, download app 영역   
**6. Pagination** : 페이지당 Climate control 및 App 노출 가능 수가 넘어가는 경우 표시   
**7. Divider** : Climate과 App 영역을 구분합니다.

  

---

## States

![library_state_apps.svg](https://document.pleos.ai/assets/images/library_state_apps-3b353d3c357bf98d417f3f97d16f7e81.svg)
![library_state_climate.svg](https://document.pleos.ai/assets/images/library_state_climate-f257b1c8eeb3ed79a57ac36b75fa2868.svg)

  

---

## Behaviors

### Normal

![library_behavior.svg](https://document.pleos.ai/assets/images/library_behavior-f5ceb52e0b90ec92ff3165ed4a34f1fb.svg)

- Connect-S를 기준으로 1줄에 최대 7개까지 제공할 수 있습니다.
- Connect-S를 기준으로 2줄 이상 넘어가게 되면 좌/우 이동이 가능한 인디케이터가 표시됩니다.
- 상단 메뉴는 차량 제어, 하단 메뉴는 앱 실행을 위한 메뉴로 구성됩니다.

  

### Editing

![library_edit_app.svg](https://document.pleos.ai/assets/images/library_edit_app-36e67fe8e67fd2cdb65d22432c429033.svg)
![library_edit_control.svg](https://document.pleos.ai/assets/images/library_edit_control-658a5a14e563d61f98a5a4d250fa4090.svg)

- 기본 앱이나 GNB에 존재하여 이동할 수 없는 앱들은 비활성화됩니다.
- 차량 제어 메뉴와 앱은 각각 지정된 영역으로만 이동이 가능합니다.
- Drag를 통해 각 영역의 메뉴를 제어할 때 이동할 수 없는 영역은 비활성화됩니다.
- 각 국가의 법규에 따라 Frost와 Defrost는 GNB에 필수 제공될 수 있습니다.
