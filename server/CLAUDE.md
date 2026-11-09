# server — RandomDrive 백엔드

Pleos Connect 앱(`../`)이 호출하는 API 서버. 앱 쪽 `CLAUDE.md`의 Pleos 디자인 가이드라인/스킬은 서버 작업에는 해당되지 않는다.

## 기술 스택

- Java 21, Spring Boot 4.x (Gradle, `spring-boot-starter-webmvc`, `spring-boot-starter-validation`)
- 현재 단계: mock 응답 + 메모리 저장(`ConcurrentHashMap`). DB 없음.

## 패키지 구조 (`com.randomdrive.server`)

- `controller` — HTTP 요청 수신, 서비스 호출만. 비즈니스 로직 금지.
- `dto` — 요청/응답 JSON 형태. Java `record`로 작성, 요청은 `@Valid` + Bean Validation으로 검증.
- `service` — 비즈니스 로직과 저장소.
- `exception` — `ApiException` 계열 예외 + `GlobalExceptionHandler`(`@RestControllerAdvice`).

## API 계약 (앱과 약속한 기준)

**이 계약이 서버 구현의 기준이다. 경로·필드명·타입·상태코드·enum 값을 바꾸거나 필드를 추가/삭제해야 하면 코드를 고치기 전에 Android 팀원과 먼저 합의한다.** 합의 없이 계약을 바꾸지 말 것.

공통 규칙:
- 시간은 epoch 밀리초(`long`), 좌표는 `lat`/`lng`(`double`).
- 오류 응답: HTTP 상태코드 + `{"code": "...", "message": "..."}`. 코드: `INVALID_REQUEST`(400), `TRIP_NOT_FOUND`(404).
- `TripStatus`: `PENDING`, `RUNNING`, `DONE`, `FAILED`.

### 1. `POST /api/v1/destinations/random` → 200

요청: `{"lat": 37.5665, "lng": 126.9780, "radiusKm": 100, "demo": false}`
- `radiusKm`는 50/100/150만 허용(그 외 400 `INVALID_REQUEST`). `demo`는 선택, 기본 `false`.

응답: `{"tripId": "t_8f3a2c", "destination": {"placeId", "name", "lat", "lng", "address", "imageUrl"(nullable), "distanceKm"}}`
- 호출마다 새 `tripId`("t_" + 6자리)를 발급하고 저장.

### 2. `POST /api/v1/trips/{tripId}/finish` → 202

요청: `{"startedAt": long, "endedAt": long, "points": [{"lat", "lng", "ts"}], "events": [{"type", "ts", "lat", "lng", "name"(선택)}]}`
- `points` 2~10,000개. `events.type`: `DESTINATION_SET`, `WAYPOINT_ADDED`, `WAYPOINT_ARRIVED`, `DESTINATION_ARRIVED`.
- 없는 `tripId` → 404 `TRIP_NOT_FOUND`.

응답: `{"tripId": "...", "status": "PENDING"}`
- 같은 `tripId`로 재전송 시 새로 시작하지 않고 현재 상태를 그대로 반환(멱등).

### 3. `GET /api/v1/trips/{tripId}/recap` → 200

응답: `{"tripId", "status", "shareUrl", "videoUrl", "summary": {"distanceKm", "durationMin", "stopCount"}}`
- finish 전: `PENDING`. `DONE`이 아니면 `shareUrl`/`videoUrl`/`summary`는 `null`.
- (mock 동작) finish 후 1~2번째 조회 `RUNNING`, 3번째부터 `DONE`.
- 없는 `tripId` → 404 `TRIP_NOT_FOUND`.

## 비밀키·설정

- API 키, 토큰, DB 비밀번호 등 비밀값은 **환경변수** 또는 **`application-local.yml`**로만 관리한다.
- `application-local.yml`은 `.gitignore` 대상이며 절대 커밋하지 않는다. `application.properties`/`application.yml`, 코드, 테스트, 문서에 비밀값을 직접 쓰지 않는다.
- 설정에서는 `${ENV_NAME}` 플레이스홀더로 참조한다.

## 작업 규칙

- API를 추가하거나 수정하면 MockMvc 테스트(정상 + 오류 1개 이상)를 함께 작성하고 `./gradlew test` 통과를 확인한다.
- 실행/접속 방법은 `README.md` 참고.
