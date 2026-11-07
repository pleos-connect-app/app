<!-- source: https://document.pleos.ai/docs/connect/guide/docs-design/foundations/contents-creation/3d-contents -->

# 3D Assets

Pleos Connect는 직관적인 사용성을 위해 3D model data를 활용하는 것을 권장합니다.   
[Go to Sample](https://www.figma.com/design/FYc6zTha14GLPvqsb1zmkD/%5BInternal%5D-Connect-2.0-Design-Kit(WIP)?node-id=29874-25562)

  

## 3D Model

- Lowpoly 상태의 차량 데이터 필요

  - 내외장 포함 3Ds Max 기준 30 MB, FBX 기준 6 MB
  - Catia, Alias 같은 도면 데이터에서 추출된 Highpoly 데이터일 경우, Retopology 진행
  - Material, object 최적화 하여 Drawcall 최소화 작업
- 3D Data Format

  - glTF(gl Transmission Format)
  - 개발사: Khronos group 3D formats working group(Rhino3D)
  - 3D 모델 정보를 JSON 형식으로 저장하는 3D 파일 형식

## Engine

- Google Filament(link [Filament google](https://github.com/google/filament), [Filament git](https://google.github.io/filament/))
- PBR 엔진으로써 모바일 우선이지만 멀티 플랫폼을 지원

## Properties

- glTF animation : Naming, 속성 값으로 컨트롤 되며 해당 naming은 차량 특성에 따라 변경 가능

  - 3D Tool에서 animation key setting 후 export
  - glTF의 animation 값을 control하거나, glTF의 rotation 값 변경으로 animation 가능
- Part namaing rule : 각 Part 별 컨트롤에 영향이 없도록 정확한 naming을 부여

## Use cases

### Climate

공조를 버튼이 아닌 실내 환경 모델링 이미지를 통해 좌석별 편리한 공조 조작 경험을 제공할 수 있습니다.

**Light**

![Climate1](https://document.pleos.ai/assets/images/Climate1-202d8f18b6a88c0e1c28a6856852490c.svg)

**Dark**

![Climate2](https://document.pleos.ai/assets/images/Climate2-9e314cb92b2fdb886abdbfbc7ce1f8b0.svg)

### Steering wheel

3D 소스 직관적으로 인지될 수 있도록 필요한 영역을 클로즈업하여 사용할 수 있습니다.

![3D1](https://document.pleos.ai/assets/images/3D1-6ebe8f94e6827a71d357a6193f55fbdf.svg)

### Seat Position

3D 소스와 함께 버튼을 활용한 조작을 연동할 때 사용할 수 있습니다.

![3D2](https://document.pleos.ai/assets/images/3D2-aa028c57fc8892a14e2a8df753fc8b97.svg)

---

## Appendix

**glTF Part Naming\_Exterior**   
Connect 모델링별 조작 가능한 요소에 대해 안내합니다.

| 구분 | Exterior 구성요소 | Partname |
| --- | --- | --- |
| Base | Static Object | Body |
| Door | Door Front Left | Door\_F\_L |
| Door Front Right | Door\_F\_R |
| Door Rear Left | Door\_R\_L |
| Door Rear Right | Door\_R\_R |
| Sidemirror Right | Door\_Sidemirror\_L |
| Sidemirror Left | Door\_Sidemirror\_R |
| Door Front Left Window | Door\_F\_L\_Window |
| Door Front Right Window | Door\_F\_R\_Window |
| Door Rear Left Window | Door\_R\_L\_Window |
| Door Rear Right Window | Door\_R\_R\_Window |
| Trunk | Trunk | Trunk |
| Trunk shock absorber | Trunk\_shock\_absorber |
| Frunk | Frunk | Frunk |
| Frunk shock absorber | Frunk\_shock\_absorber |
| Wheel | Wheel Front Right | Wheel\_F\_R |
| Wheel Front Light | Wheel\_F\_L |
| Wheel Rear All | Wheel\_R\_RL |
| Charge | Charging Port | Charging\_Port |
| Roof | Sun roof | Sunroof\_Glass |
| Sunroof\_Shade |
| Roof glass | Roofglass\_Shade |
| Wiper | Wiper Front | Wiper\_F\_R |
| Wiper\_F\_L |
| Wiper Rear | Wiper\_R |

  

**glTF Part Naming\_Interior(Connect기준)**

| 구분 | Interior 구성요소 | Partname |
| --- | --- | --- |
| Base | Static Object | Body |
| Steering Wheel | Steering Wheel | Steering\_Wheel |
| Seat\_Front | Seat Front Left Top | Seat\_F\_L\_Top |
| Seat Front Left Low | Seat\_F\_L\_Low |
| Seat Front Right Top | Seat\_F\_R\_Top |
| Seat Front Right Low | Seat\_F\_R\_Low |
| Seat\_Rear | Seat Rear Top | Seat\_R\_Top |
| Seat Rear Low | Seat\_R\_Low |

  

**glTF Part Naming\_Lamp(Connect기준)**

| Lamp | Material name | Emissive | Alpha | etc |
| --- | --- | --- | --- | --- |
| Head lamp All Off(Default) | Head\_L\_All | 0 | 1 | Head\_L\_Light glTF Hide |
| Head lamp All On | Head\_L\_All | 10 | 1 | Head\_L\_Light glTF Show |
| Rear lamp Positioning Off(Default) | Rear\_L\_Pos | 0 | 1 | cell |
| Rear lamp Positioning On | Rear\_L\_Pos | 2 | 1 | cell |
| Rear lamp Stop Off(Default) | Rear\_L\_Stop | 0 | 0.5 | cell |
| Rear lamp Stop On | Rear\_L\_Stop | 5 | 1 | cell |
| Turn signal All Off(Default) | Turn\_S\_All | 0 | 0 | cell |
| Turn signal All On | Turn\_S\_All | 5 | 1 | cell |
