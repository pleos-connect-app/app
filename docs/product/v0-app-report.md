# 차량 앱 v0 구현 보고서

> 작성: 2026-11-10 (밤사이 작업)
> 함께 볼 문서: [서버 작업 요청서](server-handoff.md) (서버 담당께 전달용) · [v0 시나리오 검증 보고서](scenario-v0-validation.md) · [MVP 명세](randomdrive-mvp-spec.md)

## 한눈에 보기

- **v0 흐름이 에뮬레이터에서 처음부터 끝까지 동작합니다.** 반경 선택 → 랜덤 추첨 → 내비 전달 → 주행 기록 → 도착 자동 판정 → 업로드 → 리캡 QR.
- 서버 없이도 돌아갑니다. 앱에 서버 계약과 똑같이 동작하는 가짜 서버가 들어 있습니다.
- 실제 mock 서버(`server/`)에 붙여서도 3개 API가 모두 동작하는 것을 확인했습니다.
- 맥락 없는 서브 에이전트에게 코드 리뷰를 맡겼습니다. 지적 15건 중 12건은 고쳤고, 3건은 일부만 고쳤거나 서버와 합의가 필요해서 남겼습니다(아래 "코드 리뷰" 절).
- 단위 테스트 40개가 모두 통과합니다.
- **사람이 확인해 주셔야 할 것**은 맨 아래 "문의·결정 필요" 절에 모았습니다.

## 1. 만든 것

### 화면 흐름

| # | 화면 | 내용 | 스크린샷 |
|---|---|---|---|
| 1 | 홈 | 현재 위치, 내비 연결 상태, 반경 50/100/150km 선택, "목적지 뽑기" | [01](screenshots/01-home.jpg) |
| 2 | 추첨 중 | 최소 1.2초 추첨 연출 | [02](screenshots/02-drawing.jpg) |
| 3 | 추첨 결과 | 목적지 이름·주소, 출발지부터 직선거리, **내비 기준 예상 시간·도로 거리**(`requestTrip`), 내비게이션 시작 / 다시 뽑기 / 반경 다시 고르기. 전달 중·실패 상태 표시 | [03](screenshots/03-result.jpg) |
| 4 | 여행 기록 중 | 경과 시간, 기록한 위치 수, 내비로 돌아가기, 여행 종료(확인 창), 기록 취소. 30초 넘게 GPS가 안 들어오면 경고 | [04](screenshots/04-trip.jpg) |
| 5 | 도착 | 앱이 도착을 판정하면 자동으로 바뀜 → "리캡 영상 만들기" | [05](screenshots/05-arrived.jpg) |
| 6 | 리캡 만드는 중 | 업로드 → 생성 중 → 실패 시 다시 시도 | [06](screenshots/06-finishing.jpg) |
| 7 | 리캡 완성 | 이동 거리·시간·예상 밖 정차 횟수, 공유 링크 QR, 새 여행 시작 | [07](screenshots/07-recap-qr.jpg) |
| - | 주행 중 | 앱은 주행 중 사용(DO)을 선언하지 않았으므로 시스템 차단 화면이 대신 나옴 | [08](screenshots/08-driving-blocked.jpg) |

화면 폭이 넓으면 좌우 2단, 좁으면(세로 화면) 위아래 1단으로 바뀝니다.

### 기능별 구현 (MVP 명세 번호 기준)

| 명세 | 구현 내용 | 주요 파일 |
|---|---|---|
| F-01 반경 랜덤 추천 | 직선거리 반경 필터, 3km 미만 제외, "다시 뽑기"는 본 곳 제외. 서버가 없으면 앱 내장 후보 15곳(`assets/candidates.json`) 사용 | `domain/CandidatePicker.kt`, `data/FakeRecapApi.kt` |
| F-02 내비 목적지 전달 | NaviHelper 연결 상태 관리, 연결된 뒤에만 요청, `onRouteStarted`/`onError` 대기(최대 20초), 시간 초과 시 요청 취소, 앱에서 거리 사전 검사(0.5km 미만 / 반경의 1.2배 초과면 막음) | `navi/NaviGateway.kt`, `ui/MainViewModel.kt` |
| F-03 경유지·도착 이벤트 | 내비 이벤트 `onDestinationArrived`, `onWaypointChanged`(ADD), `onWayPointArrived` 기록. 경유지 좌표는 `getWaypointInfo()`로 받음 | `trip/TripRecordingService.kt` |
| F-04 위치·주행 기록 | Android `LocationManager`(GPS 우선, network는 대체용), 1초 간격, 포그라운드 서비스(location)로 내비가 화면을 차지해도 계속 기록. 앱이 종료됐다 다시 열려도 이어서 기록 | `location/LocationSource.kt`, `trip/TripStore.kt` |
| (추가) 도착 자동 판정 | 에뮬레이터 내비는 가상 GPS를 못 받아 도착 이벤트가 안 옴 → **목적지 150m 안에 10초 이상 머물면 도착**으로 처리. 내비 도착 이벤트가 오면 그것도 반영 | `domain/ArrivalDetector.kt` |
| F-05 예상 밖 정차 | 60m 안에 5분 이상 머문 구간, 앞뒤에 주행이 있어야 함, 목적지·경유지 300m 안은 제외 | `domain/StopDetector.kt` |
| F-06 주행 중 UI | DO 미선언 → 주행 중에는 시스템 차단 화면. 모든 조작은 정차(P) 중에만 | `AndroidManifest.xml` |
| F-07 서버 리캡 | 서버 계약 3개 API 클라이언트, 재시도(네트워크·5xx), 화면이 보일 때만 2초 간격 조회, 최대 약 3분 | `data/HttpRecapApi.kt`, `ui/MainViewModel.kt` |
| F-08 QR | 공유 URL을 기기에서 QR로 생성(ZXing, 네트워크·GMS 불필요) | `ui/Components.kt` |

### Pleos 가이드 대응

| 가이드 | 대응 |
|---|---|
| 주행 중 사용 제한 | DO 미선언. 주행 중엔 시스템 차단 화면(실측). 기록은 서비스에서 계속됨 |
| 최상위 화면 Back = 앱 종료 금지 | 홈·여행 중·리캡 화면 Back은 `moveTaskToBack(true)` |
| 백그라운드 ≠ 종료 | 화면 상태를 기기에 저장해서 다시 열면 복원. 백그라운드에서는 홈 위치 수신·리캡 조회 중단 |
| press 피드백 | 버튼 누르면 95% 축소 |
| 디자인 수치 | 원문이 이미지라 확인 못 한 값(색·크기)은 `ui/theme/Tokens.kt` 한 파일에 `TODO(pleos-token)`으로 모음. 글자는 18sp 이상, 대비 4.5:1 이상으로 잡음 |
| 보안 | HTTP는 디버그 빌드에서 `10.0.2.2`·`localhost`만 허용. 릴리스는 HTTPS만. 백업 끔. 위치 로그는 새 여행을 시작하거나 기록을 취소하면 삭제 |
| 리소스 | 측정값: 메모리 147MB(상한 400MB), 주행 중 CPU 0%(top 순간값) |

### 빌드 옵션

```bash
./gradlew :app:installDebug                                   # 기본: 앱 내장 가짜 서버
./gradlew :app:installDebug -Prandomdrive.useFakeServer=false # 실서버(기본 http://10.0.2.2:8080)
./gradlew :app:installDebug -Prandomdrive.demoMode=true       # 추첨을 남한산성으로 고정(시연용)
# release는 가짜 서버를 쓸 수 없고 https 주소가 필수
./gradlew :app:assembleRelease -Prandomdrive.releaseServerBaseUrl=https://...
```

패키지명은 임시로 `ai.randomdrive.app`으로 정했습니다(아래 문의 사항 참고).

### 시연 보조 도구

`tools/emu-drive.py`: 에뮬레이터 GPS를 1초마다 움직여 주행을 흉내 냅니다. 내비는 이 위치를 못 받지만 앱은 받습니다.

```bash
# 서울역 → 남한산성, 90초 주행, 도착 후 20초 머무름(→ 앱이 도착 판정)
tools/emu-drive.py 37.5547 126.9707 37.4786 127.1817 --seconds 90 --dwell 20
# 중간에 6분 정차를 넣으면 "예상 밖 정차 1회"
tools/emu-drive.py 37.5547 126.9707 37.4786 127.1817 --seconds 90 --stop-at 0.5 --stop-seconds 360 --dwell 20
```

## 2. 에뮬레이터 검증 결과

환경: Pleos Connect Emulator v2.1.0 (API 34), Pleos 내비 v3.3.2-rc5-hotfix.3, Virtual Console.

| # | 시나리오 | 결과 | 비고 |
|---|---|---|---|
| E1 | 권한 요청(위치·알림) → 현재 위치 표시 | ✅ | 서울역 좌표 주입값 그대로 표시. 에뮬레이터엔 주소 변환이 없어 이름은 "현재 위치" |
| E2 | 50km 추첨 → 결과 + 내비 예상 시간 | ✅ | 월미도 직선 34km / 내비 47분·56km (내비 출발지는 시흥동 고정이라 값이 다름) |
| E3 | 다시 뽑기 | ✅ | 다른 장소가 나옴 |
| E4 | 내비게이션 시작 → 내비 안내 + 기록 서비스 시작 | ✅ | `onRouteStarted`까지 **약 7초** 걸림(처음 8초 제한이 빠듯해서 20초로 늘림) |
| E5 | 가상 주행(중간 6분 정차) → 앱 자체 도착 판정 | ✅ | 476점 기록, 도착 자동 판정, 서비스 자동 종료 |
| E6 | 리캡 만들기 → 생성 중 → 완성 + QR | ✅ | 거리 17km, 8분, **예상 밖 정차 1회**(6분 정차를 정확히 잡음) |
| E7 | 도착 전 "여행 종료" | ✅ | 도착 이벤트 없이 리캡 생성 |
| E8 | 기록 취소 | ✅ | 서비스 종료, 기록 파일 삭제, 홈으로 |
| E9 | 기록 중 앱 강제 종료 → 다시 열기 | ✅ | 여행 화면 복원, 기록 서비스 재시작, 점이 이어서 쌓임 |
| E10 | 리캡 화면에서 앱 재설치(프로세스 종료) → 다시 열기 | ✅ | 리캡·QR 그대로 복원 |
| E11 | 리캡 화면 Back | ✅ | 앱이 뒤로 가고 리캡은 유지 |
| E12 | 주행(D, 39km/h) | ✅ | 앱 화면 → "주행 중에는 사용할 수 없습니다", 기록은 계속됨. P로 돌아오면 다시 보임 |
| E13 | 실제 mock 서버 연동 | ✅ | 3개 API 정상. 단 mock이 항상 안목해변(실제 약 165km)을 줘서 50km 반경은 앱 사전 검사에서 막힘 → 150km로 끝까지 확인 |
| E14 | 내비 도착·경유지 이벤트 실제 수신 | ❌ 확인 불가 | 에뮬레이터 내비가 가상 GPS를 인식하지 않음(운영사무국 확인). 코드는 넣어 둠 |

## 3. 코드 리뷰

맥락을 전혀 모르는 서브 에이전트에게 커밋 범위만 주고 리뷰를 맡겼습니다. 15건이 나왔습니다.

| 심각도 | 지적 | 처리 |
|---|---|---|
| 높음 | 기록 서비스가 `startForeground` 전에 멈추면 앱이 죽을 수 있음 | ✅ 항상 먼저 포그라운드 진입. 서비스는 저장된 세션을 보고 스스로 멈춤 |
| 높음 | 프로세스가 죽으면 시스템이 백그라운드에서 위치 서비스를 재시작하려다 실패 반복 | ✅ `START_NOT_STICKY` + 실패해도 안전하게 종료. 앱을 열면 다시 시작(E9로 확인) |
| 중상 | 서버 응답 JSON이 깨지면 앱이 죽음(재실행 시에도 반복) | ✅ 파싱 오류를 서버 오류로 바꿔 화면에 안내. 테스트 추가 |
| 중간 | 릴리스 빌드에도 가짜 서버·가짜 QR이 들어감(심사 반려 사유) | ✅ 릴리스는 가짜 서버 금지, https 주소 필수 |
| 중간 | 리캡 생성 중 앱이 죽으면 가짜 서버가 trip을 잊어 복구 불가 | ✅ 404면 한 번 다시 업로드. 실서버 재시작에도 같은 방식으로 복구 |
| 중간 | FAILED 후 "다시 시도"가 지금 서버 계약(멱등)과 안 맞음 | ⏸ 서버와 합의 필요 → [서버 작업 요청서](server-handoff.md) 3-5 |
| 중간 | 경유지 이벤트에 경유지가 아니라 차량 위치가 찍힘 | ✅ `getWaypointInfo()` 좌표 사용 |
| 중간 | "다시 뽑기" 제외 목록이 실서버엔 안 감 | 🔶 앱이 같은 곳이면 최대 3번 다시 요청. 근본 해결은 서버 계약(요청서 3-1) |
| 중간 | GPS와 network 위치가 섞여 경로·도착 판정이 흔들림 | ✅ GPS 우선, 정확도 100m 초과 버림 |
| 중하 | 위치가 꺼진 채 시작하면 조용히 아무것도 기록 안 됨 | ✅ 나중에 켜도 수신, 30초 무수신 경고 |
| 중하 | 여행 후 홈에서 예전 출발지로 추첨될 수 있음 | ✅ 새 여행 때 위치를 다시 확인 |
| 중하 | 리캡 화면 Back이 리캡을 지움 | ✅ Back은 앱을 뒤로 보내기만 함 |
| 낮음 | 백그라운드에 있던 시간도 리캡 대기 시간에 포함됨 | ✅ 화면이 보이는 동안의 조회 횟수로 셈 |
| 낮음 | 기록 파일 쓰기가 메인 스레드에서 실행 | 🔶 1초마다 쓰는 GPS 점은 IO 스레드로 옮김. 화면 쪽의 가끔 있는 상태 저장은 그대로 |
| 낮음 | 시간 초과 뒤 늦게 안내가 시작되면 기록 없이 안내만 돔 | ✅ 시간 초과 시 경로 요청 취소. 내비에서 직접 안내를 취소해도 앱 기록은 계속됨(사용자가 앱에서 종료) |

리뷰에서 지적한 테스트 공백 중 **서버 통신 오류 처리**와 **재시작 복구**는 테스트를 추가했습니다(32개 → 40개). **ViewModel 흐름 전체와 NaviGateway 테스트는 아직 없습니다**(SDK를 감싸는 인터페이스가 필요해서 다음 작업으로 남김).

## 4. 커밋 목록

```
c536ffd docs: record contest office answer (LocationManager GPS, no GMS, emulator demo)
c1127b6 build: set up Compose, Pleos NaviHelper repo and ai.randomdrive.app package
0f7c2e9 feat(domain): radius random pick, app-side arrival, stop detection, track simplify
5dbc4e8 feat(data): server contract client + built-in fake server
51d559d feat(navi,location): NaviHelper gateway and LocationManager source
c8af1ae feat(trip): persistent trip session store for process-death recovery
ef724db feat(app): trip flow UI and recording service
f285b63 fix: address independent code review findings
(+ 이 보고서·서버 요청서 커밋)
```

push는 하지 않았습니다. 확인하신 뒤 `git push` 해 주세요.

## 5. 문의·결정 필요

### 결정해 주셔야 할 것

1. **패키지명.** 임시로 `ai.randomdrive.app`입니다. 마켓 등록 후에는 바꿀 수 없으니 확정이 필요합니다.
2. **디자인 값.** 색·글자 크기·버튼 크기는 Pleos Figma(`Pleos_Connect_design_assets_215.fig`) 값이 필요합니다. 지금은 임시 값입니다(`ui/theme/Tokens.kt`). 지금은 시스템 설정을 따라 라이트·다크 테마가 바뀝니다.
3. **도착·정차 기준값.** 도착은 150m·10초, 정차는 60m·5분으로 잡았습니다. 실제 주행 데이터로 맞춰야 합니다.
4. **문구 언어.** 화면 문구가 코드에 한국어로 들어가 있습니다. Pleos는 북미 기준 설계를 권장하므로, 다국어가 필요하면 문자열 리소스로 옮겨야 합니다.

### 운영사무국 추가 답변 → 정리됨 (2026-11-12)

5. 경로 오류 코드가 나오는 조건 → 공개 문서 범위 밖. [향후 계획](future-plans.md) 1-3으로 이동.
6. NaviHelper SDK 권한 신청 → **개발·제출 단계는 승인 불필요**, App Market 배포 심사 때만 확인.
7. 모의 안내로 내비 이벤트 보기 → 공개 문서 범위 밖. [향후 계획](future-plans.md) 1-1로 이동.

### 해결해야 할 것 (확인된 문제)

8. **문서의 주행 규제 테스트 명령이 에뮬레이터에 없습니다.** `adb shell cmd hmg_car_service --service drivingstate driving_regulation on`을 실행하면 `Can't find service: hmg_car_service`가 나옵니다. 대신 Virtual Console에서 D + ACCEL로 확인했습니다. 실차나 다른 이미지에서만 되는 명령일 수 있으니 운영사무국 문의 거리입니다.
9. **내비 화면 출발지는 여전히 시흥동입니다.** 앱의 "직선 34km"와 내비의 "56km"가 다르게 나옵니다. 시연 대본에 한 줄 설명이 필요합니다. → 크게 어긋나는 값은 결과 화면에서 숨기도록 바꿈(`a6b75dc`).
10. **에뮬레이터에 주소 변환(Geocoder)이 없어** 출발지가 "현재 위치"로만 나옵니다. 영상에 "서울역 → …"을 넣으려면 서버가 좌표로 이름을 찾아야 합니다(서버 요청서 3-3).
11. ~~**공유 QR은 아직 열리지 않습니다.**~~ → 서버 공유 페이지 완성, 실서버 연동으로 확인(2026-11-12). 가짜 서버는 `https://randomdrive.invalid/...`, mock 서버는 `localhost` 주소를 줍니다. 서버의 공유 페이지가 필요합니다(서버 요청서 2-1).
12. **결과 화면에 지도가 없습니다.** Pleos 단말은 Google 지도를 쓸 수 없습니다(운영사무국 확인). 서버가 정적 지도 이미지를 주면 붙이겠습니다(요청서 3-2).
13. 후보 15곳의 좌표는 대표 지점 근사치입니다. 실제 후보 DB는 서버 범위입니다.

### 서버 쪽 (별도 문서)

서버 담당께는 **[서버 작업 요청서](server-handoff.md)**를 전달하시면 됩니다. 앱이 서버에 기대하는 동작, 필요한 작업(공유 페이지·영상 생성·실제 추첨), 계약 변경 제안 6건, 실측 요청 예시를 정리했습니다.
