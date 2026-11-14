# 갈래말래 (Gallaemallae)

> **반경만 고르면 오늘의 목적지를 랜덤으로 뽑아 주고, 다녀온 길을 리캡 영상으로 만들어 주는 차량용 여행 앱**

반경(50 / 100 / 150km)만 고르면 오늘의 목적지를 랜덤으로 뽑아 내비게이션으로 안내하고,
여행이 끝나면 이동 기록으로 리캡 영상을 만들어 QR로 휴대폰에 공유하는 Pleos Connect 앱입니다.

팀 **경로이탈** · 2026 차량용 앱 개발 공모전 출품작

## APK

| 항목 | 값 |
|---|---|
| 파일 | [`apk/경로이탈_갈래말래_release.apk`](apk/) (release, 배포 키 서명) |
| 연결 서버 | 테스트 서버 `https://13-125-180-227.sslip.io` (AWS 서울, HTTPS). 별도 설정 없이 바로 동작 |

```bash
adb install -r apk/경로이탈_갈래말래_release.apk
adb shell am start --user 10 -n com.gyeongroital.gallaemallae/.MainActivity   # AAOS 사용자 ID 10
```

첫 실행 때 **위치정보 수집·이용 안내**가 나옵니다. "동의하고 시작"을 누르면 위치 권한을 묻고 홈으로 갑니다.
에뮬레이터 위치를 한국으로 먼저 옮겨 두세요(아래 "에뮬레이터에서 확인하는 방법" 1번). 미국 기본 위치면
"반경 안에 추천할 장소가 없어요"가 나옵니다.

## 개발·심사 환경

| 항목 | 값 |
|---|---|
| 에뮬레이터 | **Pleos Connect v2.1.0** (`RELEASE.release_connect_v2.1.0`) |
| 시스템 이미지 | `system-images/android-34/pleos_car_emulator/arm64-v8a` (Android 14, API 34, android-automotive) |
| 화면 규격 | **2560 × 1440 px, 160 dpi** (가로). Driving view 853px + 앱 영역 1682px(2 window) |
| 앱 창 | 방향 미지정(반응형). 앱 영역 전체(2 window)와 내비 옆 반쪽(1 window, 약 840dp) 모두 지원 |
| RAM | 4096MB (AVD 설정) |
| 실행 조건 | AVD **cold boot**(quick boot면 차량 기능 비활성) |

앱 정보: 패키지 `com.gyeongroital.gallaemallae`, minSdk 28, target/compileSdk 37, 버전 0.1.0.

## 빌드와 설치

JDK 17 이상과 Android SDK가 필요합니다. 에뮬레이터를 켠 상태에서 프로젝트 루트에서 실행합니다.

```bash
# 앱 내장 가짜 서버(오프라인)로 설치 — 서버 없이 전체 흐름 확인 가능
./gradlew :app:installDebug

# 실제 서버에 연결해 설치 (서버는 server/ 에서 따로 실행, 아래 참고)
./gradlew :app:installDebug -Pgallaemallae.useFakeServer=false -Pgallaemallae.serverBaseUrl=http://10.0.2.2:8080

# 시연용: 반경과 관계없이 남한산성 고정
./gradlew :app:installDebug -Pgallaemallae.demoMode=true
```

설치 후 에뮬레이터 앱 목록에서 **갈래말래**를 누르거나
`adb shell am start -n com.gyeongroital.gallaemallae/.MainActivity`로 실행합니다.

### release 빌드

```bash
./gradlew :app:assembleRelease -Pgallaemallae.releaseServerBaseUrl=https://13-125-180-227.sslip.io
```

- `releaseServerBaseUrl`(https)이 없으면 빌드가 멈춥니다(평문 HTTP 금지, release는 가짜 서버를 쓰지 않음).
- 배포 서명 키가 필요합니다. 키 파일(`keystore/`)과 비밀번호는 git에 넣지 않고 `local.properties`에 둡니다.
  키를 잃으면 같은 앱으로 업데이트할 수 없으니 팀 비밀 저장소에 백업합니다.

```properties
gallaemallae.release.storeFile=keystore/gallaemallae-release.jks
gallaemallae.release.storePassword=...
gallaemallae.release.keyAlias=gallaemallae
gallaemallae.release.keyPassword=...
```

### 위성 지도·출발지 이름

[공간정보 오픈플랫폼(VWorld)](https://www.vworld.kr)의 위성 지도와 지오코더(좌표→시·구·동)를 씁니다.
**인증키는 앱에 넣지 않습니다**(APK를 풀면 보이므로). 앱은 우리 서버의 중계 API(`/api/v1/map/satellite/...`,
`/api/v1/geo/area`)로 받고, 키는 서버 환경변수 `VWORLD_API_KEY`에만 둡니다. 키 발급 시 활용 API에서
**WMTS/TMS API**와 **지오코더 API**를 선택합니다.

- 홈: 고른 반경을 위성 지도 위 원으로 미리 보여 주고, 출발 위치를 "서울특별시 용산구 동자동"처럼 표시
- 추첨: 위성 지도 위에서 핀이 돌아다니다 목적지에 착지하고, 목적지 주변으로 확대해 잠깐 보여 줌

서버에 키가 없거나, 앱 내장 가짜 서버(오프라인 개발)로 실행하면 어두운 배경에 반경 원만 그리고
출발 위치는 "현재 위치"로 표시합니다(기능은 동일).

### 서버

`server/`의 Spring Boot 서버(JDK 21)입니다. 실행과 앱 연동 방법은
[docs/product/server-run-guide-macos.md](docs/product/server-run-guide-macos.md)를 보세요.

```bash
cd server && ./gradlew bootRun
```

## 에뮬레이터에서 확인하는 방법

1. 에뮬레이터 위치를 한국으로: `adb emu geo fix 126.9707 37.5547` (경도, 위도 — 서울역)
2. 앱에서 반경 선택 → **목적지 뽑기** → **내비게이션 시작**
3. 가상 주행(debug 빌드): `python3 tools/emu-drive.py --follow`
   - 앱에서 진행 중인 여행을 읽어 출발지 → 목적지를 실제 도로로 달리고, 목적지에서 머물러 앱이 도착을 판정하게 합니다.
   - **달리는 중에 내비에서 경유지를 추가하면**(검색 → 장소 → "경유지로") 몇 초 안에 알아채고 경유지를 거쳐 가도록 경로를 바꿉니다.
     경유지에서 잠깐 머물러 앱이 경유지 도착을 기록하고, 리캡 영상에 경유지 핀·이름이 나옵니다.
   - 속도 `--kmh 500`(기본), 예상 밖 정차 한 번 넣기 `--stop-after-km 5 --stop-seconds 320`
   - 좌표를 직접 줄 수도 있습니다: `python3 tools/emu-drive.py 37.5547 126.9707 <목적지 위도> <목적지 경도> --road --via <위도>,<경도> --seconds 120`
4. 도착 확인 → **리캡 영상 만들기** → QR

내비와 앱을 나란히 보려면 앱 창 오른쪽 끝의 ⋮ 손잡이를 왼쪽으로 끕니다.

### 에뮬레이터의 알려진 제약 (운영사무국 확인)

- 에뮬레이터 내비는 가상 GPS를 인식하지 않아 현재 위치가 고정돼 있습니다. 앱의 위치 기록·도착 판정은
  `LocationManager`로 받은 가상 GPS를 사용하므로 정상 동작합니다.
- 법규상 고정밀 지도를 제공하지 않아 내비 지도가 비어 보일 수 있습니다.
- 위 이유로 내비의 도착·경유지 **도착** 이벤트는 에뮬레이터에서 발생하지 않습니다. 앱은 자체 도착 판정(목적지·경유지 150m 안에서 10초)으로 대신합니다.
- 경유지 **추가**(내비 "경유지로")는 에뮬레이터에서도 앱에 전달됩니다. 다만 경유지 좌표가 0,0으로 와서 앱이 이름·주소로 서버(VWorld 검색)에 좌표를 묻습니다.

## 주행 중 동작

DO(주행 중 사용)를 선언하지 않았습니다. 모든 조작은 정차(P) 중에 하고, 주행 중에는 시스템 차단 화면이
표시됩니다. 위치 기록은 포그라운드 서비스로 주행 중에도 계속됩니다.

## 개인정보·위치정보

- 첫 실행 때 수집 항목·목적·보관 기간·거부 권리를 알리고 **"동의하고 시작" 버튼으로만** 동의를 받습니다. 동의 전에는 위치를 읽지 않습니다.
- 홈의 "개인정보·위치정보 안내"에서 언제든 동의를 철회할 수 있고, 철회하면 기기의 여행 기록도 지웁니다.
- 서버는 이동 경로를 리캡 영상을 만든 직후 지우고, 영상·공유 링크는 30일 뒤 지웁니다. 영상에는 출발지 1.5km 안의 경로를 넣지 않습니다.
- 전문: 서버의 [`/privacy.html`](server/src/main/resources/static/privacy.html) (개인정보처리방침 + 위치정보 이용약관). 동의 화면 QR로 열립니다.

## 사용 권한

| 권한 | 용도 |
|---|---|
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | 출발 위치, 이동 경로 기록, 도착 판정 |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` | 다른 앱(내비)을 보는 동안 위치 기록 유지 |
| `POST_NOTIFICATIONS` | 기록 중 알림 |
| `INTERNET` | 서버 통신(목적지 추첨·리캡·위성 지도·출발지 이름 중계), 장소 사진 |
| `pleos.car.permission.NAVI_ROUTE`, `NAVI_ROUTE_SEARCH`, `NAVI_CUSTOM_MAP` | NaviHelper(경로 안내 요청, 예상 시간 조회, 경유지 정보) |

## 외부 라이브러리와 라이선스

| 라이브러리 | 용도 | 라이선스 |
|---|---|---|
| Pleos Playground NaviHelper 3.0.5 | 내비 경로 안내 | Pleos Connect SDK 약관 |
| AndroidX (Compose, Activity, Lifecycle), Kotlin Coroutines | UI·비동기 | Apache License 2.0 |
| ZXing core 3.5.3 | QR 코드 생성 | Apache License 2.0 |

장소 사진과 정보는 한국관광공사 TourAPI 자료이며 화면에 출처를 표시합니다.
위성 지도와 출발지 이름은 국토교통부 공간정보 오픈플랫폼(VWorld) 자료이며 지도 화면에 출처를 표시합니다.

## 문서

- 브랜드·마켓 제출 이미지: [docs/brand/](docs/brand/) (로고 원본, 512px 아이콘, IVI 스크린샷 2560×1380)
- 기능 명세(MVP): [docs/product/gallaemallae-mvp-spec.md](docs/product/gallaemallae-mvp-spec.md)
- 구현 현황·검증 결과: [docs/product/v0-app-report.md](docs/product/v0-app-report.md)
- 시나리오 검증·운영사무국 답변: [docs/product/scenario-v0-validation.md](docs/product/scenario-v0-validation.md)
- Pleos Connect 가이드 요약: [docs/pleos/index.md](docs/pleos/index.md)
