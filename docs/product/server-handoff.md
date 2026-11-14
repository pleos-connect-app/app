# 서버 작업 요청서 — 차량 앱 v0 기준

> 작성: 앱 담당 · 2026-11-10
> 대상: 서버 담당
> 기준 계약: `server/CLAUDE.md`의 "API 계약" (지금 mock 서버와 동일)

차량 앱 v0을 계약 그대로 구현해 두었습니다. 지금 mock 서버에 붙여도 처음부터 끝까지 동작합니다(아래 "4. 연동 확인 결과" 참고).
이 문서는 **앱이 서버에 무엇을 기대하는지**, 그리고 **실제 서비스가 되려면 서버에서 해 주셔야 할 일**을 정리한 것입니다.
계약을 바꾸는 항목은 "3. 계약 변경 제안"에 따로 모았습니다. 합의 전에는 앱도 지금 계약대로 둡니다.

---

## 1. 앱이 서버를 부르는 순서

```
[차량 앱]                                   [서버]
 반경 선택(50/100/150) → "목적지 뽑기"
   ── POST /api/v1/destinations/random ──▶  후보 중 1곳 추첨, tripId 발급
   ◀─ { tripId, destination } ───────────
 "다시 뽑기"를 누르면 위 호출을 다시 함 (매번 새 tripId)
 "내비게이션 시작" → 차량 내비로 목적지 전달
 (주행 중: 앱이 GPS 1초 간격 기록, 서버 호출 없음)
 도착 또는 "여행 종료"
   ── POST /api/v1/trips/{tripId}/finish ─▶  경로·이벤트 저장, 리캡 생성 작업 시작
   ◀─ 202 { tripId, status: PENDING } ───
   ── GET /api/v1/trips/{tripId}/recap ──▶  (2초마다, 최대 3분)
   ◀─ { status: RUNNING } ...
   ◀─ { status: DONE, shareUrl, summary } ─
 차량 화면에 shareUrl을 QR로 표시 → 휴대폰으로 스캔해서 영상 보기
```

## 2. 실제 서비스를 위해 서버에서 해 주셔야 할 일

우선순위 순입니다.

### 2-1. 공유 링크와 공유 페이지 (필수 · 시연에 바로 보임)

- **`shareUrl`이 휴대폰에서 열리는 주소여야 합니다.** 지금 mock은 `http://localhost:8080/r/demo123`이라, 휴대폰으로 QR을 찍으면 휴대폰 자기 자신의 localhost로 접속해서 열리지 않습니다.
  - 공개 주소(도메인이나 공인 IP)를 설정값으로 바꿀 수 있게 해 주세요. 예: `app.public-base-url=${PUBLIC_BASE_URL}`
- `/r/{token}` 공유 페이지가 필요합니다. 지금은 404입니다.
  - 모바일 웹에서 영상 재생, 저장(다운로드), 공유가 돼야 합니다.
  - 개인정보, 차량 식별 정보, 원본 GPS 로그는 페이지에 노출하지 않습니다(MVP 명세 F-08).
  - 토큰은 추측할 수 없게 만들어 주세요(`demo123` 같은 고정값은 안 됨). 만료 정책은 서버에서 정해 주시면 됩니다.
- `videoUrl`이 가리키는 영상 파일도 실제로 내려받을 수 있어야 합니다. 지금은 404입니다.

### 2-2. 리캡 영상 생성 (필수)

- `finish`로 받은 `points`(경로)와 `events`, 그리고 `tripId`로 찾은 목적지로 영상을 만듭니다.
- MVP 명세 F-07 기준 출력: 세로 9:16, 약 8~12초, 지도 위 2D 경로 애니메이션, 장소명·거리·시간·정차 요약, MP4.
- 생성은 비동기로 처리하고, 상태는 `PENDING → RUNNING → DONE / FAILED`로 알려 주세요.
- **앱은 `FAILED`를 받으면 "다시 시도" 버튼을 보여 주고, 누르면 같은 tripId로 `finish`를 다시 보냅니다.**
  - 지금 계약은 "같은 tripId로 다시 보내면 현재 상태를 그대로 반환"입니다. 그러면 FAILED 상태에서는 다시 시도가 안 됩니다.
  - **FAILED 상태에서 `finish`가 오면 작업을 다시 시작해 주세요.** (3-5 참고)

### 2-3. 랜덤 목적지 (필수)

- 지금 mock은 요청과 상관없이 항상 "안목해변(92.4km)"을 줍니다. 반경 50km를 골라도 92km 떨어진 곳이 나옵니다.
- 필요한 동작:
  - `lat`, `lng` 기준 **직선거리 `radiusKm` 이내**의 후보 중 랜덤 1곳을 주세요.
  - 너무 가까운 곳은 빼 주세요. 앱은 직선 3km 미만을 뺍니다.
  - 폐업했거나 출입이 제한된 곳은 빼 주세요.
  - `distanceKm`는 요청 좌표부터의 직선거리(km)입니다. 앱 화면에 "직선 17km"로 나갑니다.
  - `address`는 화면에 그대로 나갑니다. `imageUrl`은 없어도 됩니다(null이면 앱이 이미지 없이 표시).
- **`demo: true`의 의미를 정해 주세요.** 앱 쪽 제안은 "항상 같은 시연용 목적지(남한산성)"입니다.
  - 앱의 내장 가짜 서버는 이렇게 동작합니다.
  - 앱은 시연 빌드에서만 `demo: true`를 보냅니다(`-Pgallaemallae.demoMode=true`).
- 반경 안에 후보가 하나도 없을 때의 응답도 정해 주세요. 제안: `404` + `{"code":"NO_CANDIDATE","message":"..."}`
  - 그 밖의 오류 코드는 "목적지를 불러오지 못했어요"로 안내합니다.
  - 앱은 이미 `NO_CANDIDATE` 코드가 오면 서버가 보낸 `message`를 그대로 보여 주도록 되어 있습니다.

### 2-4. 요약 수치 (`summary`)

- `distanceKm`: `points`를 이은 총 길이
- `durationMin`: `endedAt - startedAt` (분)
- `stopCount`: "예상 밖 정차" 횟수. 앱도 같은 규칙으로 직접 계산해서, 서버 summary가 없을 때 대신 보여 줍니다. 같은 규칙을 쓰면 숫자가 맞습니다.
  - 반경 60m 안에 **5분 이상** 머문 구간
  - 그 앞뒤에 주행 구간이 있어야 함(출발 전 대기, 도착 후 체류는 제외)
  - 목적지나 계획 경유지(`WAYPOINT_ADDED`/`WAYPOINT_ARRIVED` 이벤트 좌표)에서 300m 이내면 제외
  - 앱 코드: `app/src/main/java/com/gyeongroital/gallaemallae/domain/StopDetector.kt`
  - 기준값은 실제 주행 로그로 다시 맞춰야 합니다. 서버가 더 나은 방법을 쓰면 앱은 서버 값을 우선 표시합니다.

---

## 3. 계약 변경 제안 (합의 필요)

합의되면 앱은 바로 맞추겠습니다. 모두 **선택 필드 추가**라서 기존 mock은 깨지지 않습니다.

| # | 제안 | 이유 | 앱 쪽 현재 처리 |
|---|---|---|---|
| 3-1 | `/destinations/random` 요청에 `excludePlaceIds: string[]` 추가 | "다시 뽑기"를 눌렀을 때 방금 본 곳이 또 나오지 않게 | 앱이 본 곳 목록을 들고 있지만 서버로 보낼 필드가 없어서 안 보냄 |
| 3-2 | `/recap` 응답에 `thumbnailUrl` 추가 | 차량에는 Google 지도가 없어서(GMS 없음) 결과 화면에 지도를 그릴 수 없음. 서버가 만든 정적 지도 이미지나 영상 첫 장면을 보여 주고 싶음 | 썸네일 없이 숫자 요약 + QR만 표시 |
| 3-3 | `finish` 요청에 `origin: { name, lat, lng }` 추가 | 영상에 "서울역 → 남한산성"처럼 출발지 이름을 넣으려면 필요. 차량에는 주소 변환(Geocoder)이 없어서 앱은 이름을 모름 | 보내지 않음. 서버가 `points[0]` 좌표로 이름을 찾아도 됨 |
| 3-4 | `finish` 요청에 `endReason: "ARRIVED" \| "USER_ENDED"` 추가 | 도착해서 끝났는지, 중간에 사용자가 끝냈는지에 따라 영상 문구를 다르게 쓰려면 필요 | 도착이면 `DESTINATION_ARRIVED` 이벤트가 들어 있고, 사용자가 끝냈으면 없음. 이걸로 구분할 수도 있음 |
| 3-5 | `FAILED` 상태에서 `finish`가 다시 오면 재생성 | 2-2 참고 | 앱은 FAILED 후 "다시 시도" 시 finish를 다시 보냄 |
| 3-6 | 랜덤 목적지 실패 코드 `NO_CANDIDATE`(404) | 2-3 참고 | 알 수 없는 코드는 일반 오류로 안내 |

---

## 4. 연동 확인 결과 (mock 서버)

`-Pgallaemallae.useFakeServer=false`로 앱을 빌드해서 에뮬레이터에서 `http://10.0.2.2:8080`에 붙여 확인했습니다. 결과는 `docs/product/v0-app-report.md`의 "서버 연동" 절에 적었습니다.

확인한 것:
- 3개 API 모두 계약대로 주고받음(`t_505843` 발급 → finish 202 → recap RUNNING 2회 → DONE)
- 앱은 DONE의 `summary`를 그대로 화면에 표시하고, `shareUrl`을 QR로 만듦
- mock이 반경과 상관없이 안목해변(실제 직선거리 약 165km)을 주기 때문에, 반경 50km를 고르면 앱의 사전 검사("선택한 반경보다 먼 목적지예요")에서 막힙니다. 150km로 골라야 끝까지 진행됩니다. → 2-3의 실제 추첨이 필요한 이유입니다.

아래는 이 확인 때 앱이 기록한 데이터로 만든 `finish` 요청 예시입니다. points는 일부만 보여 줍니다. 실제 전송 때는 10m 미만으로 겹치는 점을 줄여서 보냅니다.

```jsonc
{
  "startedAt": 1794249070574,
  "endedAt": 1794249112198,
  "points": [
    {
      "lat": 37.554698333333334,
      "lng": 126.97069833333333,
      "ts": 1794249070574
    },
    {
      "lat": 37.554698333333334,
      "lng": 126.97069833333333,
      "ts": 1794249072738
    },
    {
      "lat": 37.554698333333334,
      "lng": 126.97069833333333,
      "ts": 1794249073742
    },
    "... (총 37개 중 일부)",
    {
      "lat": 37.6,
      "lng": 127.09999833333333,
      "ts": 1794249111968
    }
  ],
  "events": [
    {
      "type": "DESTINATION_SET",
      "ts": 1794249078407,
      "lat": 37.554698333333334,
      "lng": 126.97069833333333,
      "name": "안목해변"
    }
  ]
}
```

## 5. 앱 쪽 동작 규칙 (서버가 알고 있으면 좋은 것)

| 항목 | 앱 동작 |
|---|---|
| 시간·좌표 | epoch 밀리초, `lat`/`lng` double (계약 그대로) |
| `points` 개수 | 2~10,000개를 지킴. 10m 미만으로 움직인 점은 버리되 30초마다 1점은 남김. 그래도 넘으면 균등하게 솎음. 첫 점은 여행 시작 위치 |
| `events` | `DESTINATION_SET`(내비 안내 시작 시점, 출발 좌표), `WAYPOINT_ADDED`(경유지 좌표, 내비가 좌표를 5초 안에 안 주면 그때 차량 위치)·`WAYPOINT_ARRIVED`(경유지 좌표) — 둘 다 차량 내비가 알려 줄 때만, `DESTINATION_ARRIVED`(목적지 좌표, 내비 이벤트 또는 앱 자체 판정). 사용자가 도착 전에 "여행 종료"를 누르면 `DESTINATION_ARRIVED`가 없음 |
| 도착 판정 | 에뮬레이터 내비는 가상 GPS를 인식하지 않아 도착 이벤트가 안 옵니다. 앱이 "목적지 150m 안에 10초 이상" 머물면 도착으로 봅니다 |
| 재시도 | 네트워크 오류와 5xx만 자동으로 다시 시도(1초 → 2초 간격, 최대 3번 시도). 4xx는 바로 오류 화면으로 가고, 사용자가 "다시 시도"를 누를 수 있음 |
| `recap`이 `TRIP_NOT_FOUND`(404) | 서버가 재시작돼 trip을 잃은 경우로 보고, 같은 tripId로 `finish`를 한 번 다시 보낸 뒤 조회를 계속함. 서버가 `finish`도 404로 주면 오류 화면 |
| "다시 뽑기" | 서버가 방금 본 곳을 또 주면 앱이 최대 3번까지 다시 요청함(3-1이 되면 필요 없어짐) |
| `finish` 중복 | 앱이 재시작되거나 응답을 못 받으면 같은 tripId로 다시 보낼 수 있음(계약상 멱등이라 안전) |
| `recap` 조회 | 앱 화면이 보일 때만 2초 간격으로 조회. 최대 3분 기다리고, 넘으면 "오래 걸리고 있어요 · 다시 시도" |
| 알 수 없는 `status` | 실패로 보지 않고 계속 조회 (서버가 상태를 추가해도 앱이 깨지지 않게) |
| 오류 응답 | `{"code","message"}`를 읽음. 형식이 다르면 `HTTP_<상태코드>`로 처리 |
| 통신 | 디버그 빌드만 `10.0.2.2`·`localhost` HTTP 허용. **릴리스 빌드는 HTTPS만 됨** → 시연·심사용 서버는 HTTPS가 필요합니다(Pleos 보안 가이드: TLS 1.2 이상) |
| 인증 | v0에서는 없음. 차량·사용자 식별자는 보내지 않습니다 |

## 6. 서버 실행하면서 발견한 것

- `server/gradlew`가 실행 권한 없이 커밋되어 있어 macOS/Linux에서 `./gradlew`가 `permission denied`로 실패합니다.
  - 고치는 방법: `git update-index --chmod=+x server/gradlew` 후 커밋
- 빌드에 JDK 21이 꼭 필요합니다. JDK 17이나 25만 있으면 toolchain을 찾지 못해 실패합니다. README에 이미 적혀 있으니 참고용입니다.

## 7. 앱을 서버에 붙여 보는 방법

```bash
# 서버
cd server && ./gradlew bootRun

# 앱 (프로젝트 루트에서, 에뮬레이터 실행 중)
./gradlew :app:installDebug -Pgallaemallae.useFakeServer=false -Pgallaemallae.serverBaseUrl=http://10.0.2.2:8080
```

`-Pgallaemallae.useFakeServer` 없이 빌드하면 앱 안에 들어 있는 가짜 서버를 씁니다. 서버 없이 화면 개발할 때 쓰는 용도입니다.

## 8. 앱 이름 변경: 갈래말래 (2026-11-14)

앱 이름을 **갈래말래**(영문 Gallaemallae), 팀 이름을 **경로이탈**로 정했습니다. 앱은 이미 바꿨습니다.

- 앱 패키지: `ai.randomdrive.app` → `com.gyeongroital.gallaemallae`
- 앱 빌드 속성: `-Prandomdrive.*` → `-Pgallaemallae.*` (`useFakeServer`, `serverBaseUrl`, `demoMode`, `releaseServerBaseUrl`, `vworldKey`, `apiKey`). 앱 `local.properties`의 키 이름도 `gallaemallae.vworldKey`, `gallaemallae.apiKey`로 바꿔 주세요(옛 이름이 남아 있으면 빌드할 때 경고가 나옵니다).
- API 계약은 바뀐 것이 없습니다.

서버에 부탁드릴 것 (사용자에게 보이는 글자부터):

| 우선 | 위치 | 지금 | 바꿀 것 |
|---|---|---|---|
| 1 | `recap/RecapPainter.java` 표지·썸네일 | `RANDOM DRIVE · 날짜` | `갈래말래 · 날짜` 등 |
| 1 | `recap/RecapPainter.java` 요약 장면 아래 | `RandomDrive` | `갈래말래` |
| 1 | `templates/share.html`, `share-unavailable.html` 하단 | `RandomDrive` | `갈래말래` (페이지 제목·og 태그에 이름이 있으면 함께) |
| 2 | `tourapi/TourApiClient.java` | `MobileApp=RandomDrive` | `Gallaemallae` (다음 후보 가져오기 때) |
| 3 | Java 패키지 `com.randomdrive.server`, 배포(`randomdrive.service`, `/etc/randomdrive.env`, `/var/lib/randomdrive`, DB 파일 `randomdrive.mv.db`) | | 선택. 바꾸면 테스트 서버의 서비스·DB 경로를 옮겨야 해서 서버 담당 판단에 맡깁니다 |
