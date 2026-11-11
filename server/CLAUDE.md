# server — RandomDrive 백엔드

Pleos Connect 앱(`../`)이 호출하는 API 서버. 앱 쪽 `CLAUDE.md`의 Pleos 디자인 가이드라인/스킬은 서버 작업에는 해당되지 않는다.

## 기술 스택

- Java 21, Spring Boot 4.x (Gradle, `spring-boot-starter-webmvc`, `spring-boot-starter-validation`)
- 현재 단계: 랜덤 목적지는 실제 추첨(후보는 TourAPI에서 받아 둔 JSON 파일), 리캡 요약·공유 링크·공유 페이지는 실제 동작, 영상 생성 진행(RUNNING→DONE)만 mock. 저장은 메모리(`ConcurrentHashMap`), DB 없음.
- 공유 페이지는 Thymeleaf(`src/main/resources/templates/share*.html`).

## 패키지 구조 (`com.randomdrive.server`)

- `controller` — HTTP 요청 수신, 서비스 호출만. 비즈니스 로직 금지.
- `dto` — 요청/응답 JSON 형태. Java `record`로 작성, 요청은 `@Valid` + Bean Validation으로 검증.
- `service` — 비즈니스 로직과 저장소.
- `exception` — `ApiException` 계열 예외 + `GlobalExceptionHandler`(`@RestControllerAdvice`).
- `tourapi` — 한국관광공사 TourAPI 연동. 요청 처리 중에는 호출하지 않고, `./gradlew importCandidates`(프로필 `local,import`)로 후보 파일을 만들 때만 쓴다.

## 랜덤 목적지 후보 데이터

- 출처: 공공데이터포털 "한국관광공사_국문 관광정보 서비스_GW"(KorService2) `areaBasedList2`, 관광지(contentTypeId=12) 전국.
- 실시간 호출하지 않는 이유: 위치기반 조회는 반경 최대 20km라 50~150km 추첨에 못 쓰고, 일일 호출 수 제한이 있다.
- 후보 기준(2026-11-11 결정): 대표 이미지 있음, 쇼핑·숙박 대분류 제외, 도시공원(VE03)·교육시설(VE09)·교통시설(VE11)·기타문화관광지(VE12) 제외, 한국 좌표 범위 밖 제외. 기준은 `tourapi/CandidateImporter`에 있다.
- `src/main/resources/candidates.json`은 자동 생성 파일이고 커밋한다(키 없는 팀원·배포 서버도 같은 후보로 동작). 직접 고치지 말고 다시 가져온다. 결과가 1,000건 미만이면 덮어쓰지 않고 실패한다.
- `demo`(남한산성)는 파일 안에 고정으로 들어가며 추첨 후보와 별개다.
- TourAPI 이미지는 공공누리 1유형(출처표시)·3유형(출처표시+변경금지)이 섞여 있다. 후보의 `imageLicense`로 구분되며, 공유 페이지·영상에서 이미지를 쓸 때 출처("한국관광공사")를 표시하고 3유형은 자르기·편집하지 않는다.

## API 계약 (앱과 약속한 기준)

**이 계약이 서버 구현의 기준이다. 경로·필드명·타입·상태코드·enum 값을 바꾸거나 필드를 추가/삭제해야 하면 코드를 고치기 전에 Android 팀원과 먼저 합의한다.** 합의 없이 계약을 바꾸지 말 것.

공통 규칙:
- 시간은 epoch 밀리초(`long`), 좌표는 `lat`/`lng`(`double`).
- 오류 응답: HTTP 상태코드 + `{"code": "...", "message": "..."}`. 코드: `INVALID_REQUEST`(400), `TRIP_NOT_FOUND`(404), `NO_CANDIDATE`(404).
- `TripStatus`: `PENDING`, `RUNNING`, `DONE`, `FAILED`.

### 1. `POST /api/v1/destinations/random` → 200

요청: `{"lat": 37.5665, "lng": 126.9780, "radiusKm": 100, "demo": false}`
- `radiusKm`는 50/100/150만 허용(그 외 400 `INVALID_REQUEST`). `demo`는 선택, 기본 `false`.

응답: `{"tripId": "t_8f3a2c", "destination": {"placeId", "name", "lat", "lng", "address", "imageUrl"(nullable), "distanceKm"}}`
- 호출마다 새 `tripId`("t_" + 6자리)를 발급하고, 발급한 목적지와 함께 저장.
- 추첨 규칙(앱 `CandidatePicker`와 동일): 요청 좌표에서 직선거리 **3km 이상 `radiusKm` 이하**, `active`인 후보 중 균등 랜덤 1곳. 후보 목록은 `src/main/resources/candidates.json`.
- `distanceKm`: 요청 좌표부터의 직선거리(km, 소수 첫째 자리 반올림).
- `demo: true`: 반경과 무관하게 항상 시연용 장소 남한산성(`demo-namhansanseong`).
- 반경 안에 후보가 없으면 404 `NO_CANDIDATE`, 트립은 만들지 않음. `message`는 앱이 화면에 그대로 띄우는 한국어 문장(예: "반경 50km 안에 추천할 장소가 없어요").

### 2. `POST /api/v1/trips/{tripId}/finish` → 202

요청: `{"startedAt": long, "endedAt": long, "points": [{"lat", "lng", "ts"}], "events": [{"type", "ts", "lat", "lng", "name"(선택)}]}`
- `points` 2~10,000개. `events.type`: `DESTINATION_SET`, `WAYPOINT_ADDED`, `WAYPOINT_ARRIVED`, `DESTINATION_ARRIVED`.
- 없는 `tripId` → 404 `TRIP_NOT_FOUND`.

응답: `{"tripId": "...", "status": "PENDING"}`
- 같은 `tripId`로 재전송 시 새로 시작하지 않고 현재 상태를 그대로 반환(멱등). 받은 데이터도 바꾸지 않는다.
- 단, 상태가 `FAILED`면 새로 받은 데이터로 처음부터 다시 생성하고 `PENDING`을 반환(앱 "다시 시도", handoff 3-5).

### 3. `GET /api/v1/trips/{tripId}/recap` → 200

응답: `{"tripId", "status", "shareUrl", "videoUrl", "summary": {"distanceKm", "durationMin", "stopCount"}}`
- finish 전: `PENDING`. `DONE`이 아니면 `shareUrl`/`videoUrl`/`summary`는 `null`.
- (mock 동작) finish 후 1~2번째 조회 `RUNNING`, 3번째부터 `DONE`. 실제 영상 생성(F-07)이 붙으면 생성 작업 상태를 따른다.
- `DONE`이 처음 될 때 요약을 계산하고 공유 토큰을 발급한다. 이후 조회는 같은 값.
  - `shareUrl`: `{share.public-base-url}/r/{token}`. token은 128비트 난수(URL-safe base64 22자).
  - `videoUrl`: 영상 생성(F-07) 전까지 `null`.
  - `summary`: finish 데이터로 계산(`RecapSummarizer`, 앱 `localSummary`와 같은 규칙). `distanceKm` 경로 길이(소수 첫째 자리), `durationMin` `endedAt-startedAt`(분, 버림), `stopCount` 예상 밖 정차(`StopDetector`, 앱과 같은 규칙: 60m 안 5분 이상·앞뒤 주행·목적지/경유지 300m 밖).
- 없는 `tripId` → 404 `TRIP_NOT_FOUND`.

### 4. `GET /r/{token}` → 200 HTML (공유 페이지, F-08)

앱이 호출하지 않는다. QR을 찍은 휴대폰 브라우저가 연다.
- 목적지 사진(출처 표시)·이름·주소, 날짜, 요약 수치, 영상(없으면 "준비 중"), 공유 버튼.
- 출발지·경로 좌표 등 원본 위치 로그와 개인·차량 식별 정보는 넣지 않는다.
- 없는 토큰 404, 기간(`share.ttl`, 기본 30일)이 지난 토큰 410 — 둘 다 HTML 안내 페이지.
- `Referrer-Policy: no-referrer`, `X-Robots-Tag: noindex` 헤더.

## 비밀키·설정

- API 키, 토큰, DB 비밀번호 등 비밀값은 **환경변수** 또는 **`application-local.yml`**로만 관리한다.
- `application-local.yml`은 `.gitignore` 대상이며 절대 커밋하지 않는다. `application.properties`/`application.yml`, 코드, 테스트, 문서에 비밀값을 직접 쓰지 않는다.
- 설정에서는 `${ENV_NAME}` 플레이스홀더로 참조한다.

## 작업 규칙

- API를 추가하거나 수정하면 MockMvc 테스트(정상 + 오류 1개 이상)를 함께 작성하고 `./gradlew test` 통과를 확인한다.
- 실행/접속 방법은 `README.md` 참고.
