# 랜덤드라이브 (RandomDrive)

반경(50 / 100 / 150km)만 고르면 오늘의 목적지를 랜덤으로 뽑아 내비게이션으로 안내하고,
여행이 끝나면 이동 기록으로 리캡을 만들어 QR로 휴대폰에 공유하는 Pleos Connect 앱입니다.

## 개발·심사 환경

| 항목 | 값 |
|---|---|
| 에뮬레이터 | **Pleos Connect v2.1.0** (`RELEASE.release_connect_v2.1.0`) |
| 시스템 이미지 | `system-images/android-34/pleos_car_emulator/arm64-v8a` (Android 14, API 34, android-automotive) |
| 화면 규격 | **2560 × 1440 px, 160 dpi** (가로). Driving view 853px + 앱 영역 1682px(2 window) |
| 앱 창 | 방향 미지정(반응형). 앱 영역 전체(2 window)와 내비 옆 반쪽(1 window, 약 840dp) 모두 지원 |
| RAM | 4096MB (AVD 설정) |
| 실행 조건 | AVD **cold boot**(quick boot면 차량 기능 비활성) |

앱 정보: 패키지 `ai.randomdrive.app`, minSdk 28, target/compileSdk 37, 버전 0.1.0.

## 빌드와 설치

JDK 17 이상과 Android SDK가 필요합니다. 에뮬레이터를 켠 상태에서 프로젝트 루트에서 실행합니다.

```bash
# 앱 내장 가짜 서버(오프라인)로 설치 — 서버 없이 전체 흐름 확인 가능
./gradlew :app:installDebug

# 실제 서버에 연결해 설치 (서버는 server/ 에서 따로 실행, 아래 참고)
./gradlew :app:installDebug -Prandomdrive.useFakeServer=false -Prandomdrive.serverBaseUrl=http://10.0.2.2:8080

# 시연용: 반경과 관계없이 남한산성 고정
./gradlew :app:installDebug -Prandomdrive.demoMode=true
```

설치 후 에뮬레이터 앱 목록에서 **랜덤드라이브**를 누르거나
`adb shell am start -n ai.randomdrive.app/.MainActivity`로 실행합니다.

릴리스 빌드는 `-Prandomdrive.releaseServerBaseUrl=https://...`가 없으면 빌드가 멈춥니다(평문 HTTP 금지).

### 추첨 화면 위성 지도(선택)

목적지를 뽑는 동안 위성 지도 위에서 핀이 돌아다니다 목적지에 착지하는 연출이 나옵니다.
지도는 [공간정보 오픈플랫폼(VWorld)](https://www.vworld.kr) 위성 타일을 쓰며, 인증키를 `local.properties`(git 미포함)에 넣습니다.

```properties
randomdrive.vworldKey=발급받은_인증키
```

키가 없거나 지도를 받지 못하면 어두운 배경에 반경 원만 그리는 화면으로 대신합니다(기능은 동일).

### 서버

`server/`의 Spring Boot 서버(JDK 21)입니다. 실행과 앱 연동 방법은
[docs/product/server-run-guide-macos.md](docs/product/server-run-guide-macos.md)를 보세요.

```bash
cd server && ./gradlew bootRun
```

## 에뮬레이터에서 확인하는 방법

1. 에뮬레이터 위치를 한국으로: `adb emu geo fix 126.9707 37.5547` (경도, 위도 — 서울역)
2. 앱에서 반경 선택 → **목적지 뽑기** → **내비게이션 시작**
3. 가상 주행: `python3 tools/emu-drive.py 37.5547 126.9707 <목적지 위도> <목적지 경도> --seconds 90 --dwell 20`
4. 도착 확인 → **리캡 영상 만들기** → QR

내비와 앱을 나란히 보려면 앱 창 오른쪽 끝의 ⋮ 손잡이를 왼쪽으로 끕니다.

### 에뮬레이터의 알려진 제약 (운영사무국 확인)

- 에뮬레이터 내비는 가상 GPS를 인식하지 않아 현재 위치가 고정돼 있습니다. 앱의 위치 기록·도착 판정은
  `LocationManager`로 받은 가상 GPS를 사용하므로 정상 동작합니다.
- 법규상 고정밀 지도를 제공하지 않아 내비 지도가 비어 보일 수 있습니다.
- 위 이유로 내비의 도착·경유지 이벤트는 에뮬레이터에서 발생하지 않습니다. 앱은 자체 도착 판정(목적지 150m 안에서 10초)으로 대신합니다.

## 주행 중 동작

DO(주행 중 사용)를 선언하지 않았습니다. 모든 조작은 정차(P) 중에 하고, 주행 중에는 시스템 차단 화면이
표시됩니다. 위치 기록은 포그라운드 서비스로 주행 중에도 계속됩니다.

## 사용 권한

| 권한 | 용도 |
|---|---|
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | 출발 위치, 이동 경로 기록, 도착 판정 |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` | 다른 앱(내비)을 보는 동안 위치 기록 유지 |
| `POST_NOTIFICATIONS` | 기록 중 알림 |
| `INTERNET` | 목적지 추첨·리캡 서버 통신, 장소 사진, 추첨 화면 위성 지도 |
| `pleos.car.permission.NAVI_ROUTE`, `NAVI_ROUTE_SEARCH`, `NAVI_CUSTOM_MAP` | NaviHelper(경로 안내 요청, 예상 시간 조회, 경유지 정보) |

## 외부 라이브러리와 라이선스

| 라이브러리 | 용도 | 라이선스 |
|---|---|---|
| Pleos Playground NaviHelper 3.0.5 | 내비 경로 안내 | Pleos Connect SDK 약관 |
| AndroidX (Compose, Activity, Lifecycle), Kotlin Coroutines | UI·비동기 | Apache License 2.0 |
| ZXing core 3.5.3 | QR 코드 생성 | Apache License 2.0 |

장소 사진과 정보는 한국관광공사 TourAPI 자료이며 화면에 출처를 표시합니다.
추첨 화면의 위성 지도는 국토교통부 공간정보 오픈플랫폼(VWorld) 타일이며 화면에 출처를 표시합니다.

## 문서

- 기능 명세(MVP): [docs/product/randomdrive-mvp-spec.md](docs/product/randomdrive-mvp-spec.md)
- 구현 현황·검증 결과: [docs/product/v0-app-report.md](docs/product/v0-app-report.md)
- 시나리오 검증·운영사무국 답변: [docs/product/scenario-v0-validation.md](docs/product/scenario-v0-validation.md)
- Pleos Connect 가이드 요약: [docs/pleos/index.md](docs/pleos/index.md)
