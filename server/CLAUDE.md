# server — RandomDrive 백엔드

Pleos Connect 앱(`../`)이 호출하는 API 서버. 앱 쪽 `CLAUDE.md`의 Pleos 디자인 가이드라인/스킬은 서버 작업에는 해당되지 않는다.

## 기술 스택

- Java 21, Spring Boot 4.x (Gradle, `spring-boot-starter-webmvc`, `spring-boot-starter-validation`)
- 현재 단계: 랜덤 목적지는 실제 추첨(후보는 TourAPI에서 받아 둔 JSON 파일), 리캡(요약·영상·썸네일·공유 링크·공유 페이지)은 실제 동작. 트립은 **H2 파일 DB**(Spring Data JPA, 기본 `data/randomdrive.mv.db`, 환경변수 `DB_PATH`, 스키마는 `ddl-auto=update`). 영상·썸네일 파일은 `recap.storage-dir`(기본 `data/recaps/`, git 제외).
- 리캡 영상: Java2D로 프레임을 그려 외부 **ffmpeg**로 MP4 인코딩. ffmpeg가 없으면 썸네일만 만든다.
- 공유 페이지는 Thymeleaf(`src/main/resources/templates/share*.html`).

## 패키지 구조 (`com.randomdrive.server`)

- `controller` — HTTP 요청 수신, 서비스 호출만. 비즈니스 로직 금지.
- `dto` — 요청/응답 JSON 형태. Java `record`로 작성, 요청은 `@Valid` + Bean Validation으로 검증.
- `service` — 비즈니스 로직과 저장소.
- `exception` — `ApiException` 계열 예외 + `GlobalExceptionHandler`(`@RestControllerAdvice`).
- `security` — `/api` 앞단 보호(`ApiGuardFilter`: 앱 키·IP당 요청 수 제한, `RateLimiter`).
- `persistence` — DB 저장. `TripStore`(트립 한 건 = 메모리 객체 하나로 캐시, 상태를 바꾼 쪽이 `synchronized(trip)` 안에서 save), 엔티티 `TripEntity`(목적지·상태·요약·공유 토큰), `TripFinishEntity`(주행 데이터 JSON, 생성할 때만 읽음). **주행 데이터는 트립이 DONE·FAILED가 되면 바로 지운다**(개인정보처리방침 약속, 서버 시작 때 남은 것도 정리).
- `controller/MapController`·`service/MapProxyService` — 앱 지도 중계(위성 타일, 좌표 → 지역 이름). VWorld 키를 앱에 두지 않기 위함.
- `recap` — 리캡 영상·썸네일 생성(F-07). `RecapMediaService`(조립·파일), `RecapPainter`(장면 그리기, 네트워크 없음), `FfmpegEncoder`, `RouteCamera`(경로 장면 카메라 움직임)·`PerspectiveMap`(기울인 지도 2.5D 렌더링)·`TileCache`(타일 미리 받기), `MapView`(썸네일용 평면 지도), `RouteMask`(출발지 가리기), `VWorldClient`(위성 타일·역지오코딩), `PhotoLoader`. 생성 작업 스레드는 `RecapExecutorConfig`(`recap.workers`, 0이면 요청 안에서 바로 — 테스트용).
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
- 오류 응답: HTTP 상태코드 + `{"code": "...", "message": "..."}`. 코드: `INVALID_REQUEST`(400), `UNAUTHORIZED`(401), `TRIP_NOT_FOUND`(404), `NO_CANDIDATE`(404), `RATE_LIMITED`(429, `Retry-After` 헤더 = 초), `MAP_UNAVAILABLE`(503, 지도 타일을 받지 못함).
- 앱 키: `/api/v1/**` 요청은 `X-App-Key` 헤더를 보낸다. 서버에 `api.keys`(환경변수 `API_KEYS`, 쉼표로 여러 개 = 교체 기간)가 설정돼 있을 때만 검사한다. 공유 페이지 `/r/**`는 검사하지 않는다.
- 요청 수 제한(클라이언트 IP당, `security/ApiGuardFilter`): 목적지 추첨 분당 30, finish 시간당 30, 리캡 조회 분당 120, 지도 타일 분당 600, 지역 이름 분당 30, 그 밖 분당 60. `api.rate-limit.*`로 조정.
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
- 방금 본 곳을 빼는 `excludePlaceIds`는 두지 않기로 합의(handoff 3-1, 후보 1만 곳이라 불필요).

### 2. `POST /api/v1/trips/{tripId}/finish` → 202

요청: `{"startedAt": long, "endedAt": long, "points": [{"lat", "lng", "ts"}], "events": [{"type", "ts", "lat", "lng", "name"(선택)}]}`
- `points` 2~10,000개. `events.type`: `DESTINATION_SET`, `WAYPOINT_ADDED`, `WAYPOINT_ARRIVED`, `DESTINATION_ARRIVED`.
- 경유지 이벤트의 좌표를 모르면 앱이 `lat: 0, lng: 0`을 보낸다(경유지 그리기·정차 제외 기준에서 뺀다).
- 없는 `tripId` → 404 `TRIP_NOT_FOUND`.

응답: `{"tripId": "...", "status": "PENDING"}`
- 여행은 항상 사용자가 "여행 종료"를 눌러 끝난다(목적지 도착으로 자동 종료되지 않음). 그래서 `points`에는 도착 후 주변을 돌아다닌 기록도 들어가고, `endedAt`은 사용자가 종료를 누른 시각이다.
- 도착 여부는 `DESTINATION_ARRIVED` 이벤트 유무로 판단한다(`endReason` 필드는 두지 않기로 합의, handoff 3-4). 도착하지 않고 끝낸 여행도 있다.
- 출발지 이름은 앱이 보내지 않는다(`origin` 필드 없음, handoff 3-3). 필요하면 서버가 `points[0]` 좌표로 찾는다.
- 같은 `tripId`로 재전송 시 새로 시작하지 않고 현재 상태를 그대로 반환(멱등). 받은 데이터도 바꾸지 않는다.
- 단, 상태가 `FAILED`면 새로 받은 데이터로 처음부터 다시 생성하고 `PENDING`을 반환(앱 "다시 시도", handoff 3-5).

### 3. `GET /api/v1/trips/{tripId}/recap` → 200

응답: `{"tripId", "status", "shareUrl", "videoUrl", "thumbnailUrl", "summary": {"distanceKm", "durationMin", "stopCount"}}`
- 상태: finish 전 `PENDING` → finish 후 생성 대기 `PENDING` → 생성 중 `RUNNING` → `DONE` 또는 `FAILED`. 생성은 finish 때 백그라운드 작업으로 시작한다(PC에서 약 7초 + 지도 타일 받는 시간, 앱은 2초 간격 최대 3분 조회).
- `DONE`이 아니면 `shareUrl`/`videoUrl`/`thumbnailUrl`/`summary`는 `null`. `DONE` 이후 조회는 늘 같은 값.
  - `shareUrl`: `{share.public-base-url}/r/{token}`. token은 128비트 난수(URL-safe base64 22자).
  - `videoUrl`: `{shareUrl}/video.mp4`. 9:16 10초 MP4(F-07). ffmpeg가 없거나 `recap.video=false`면 `DONE`이어도 `null`.
  - `thumbnailUrl`(handoff 3-2): `{shareUrl}/thumbnail.jpg`. 16:9(1280×720) JPEG, 영상 표지와 같은 내용(목적지 사진·이름·날짜·출발 동네·요약, 사진이 없으면 경로 지도). 공유 페이지 영상 포스터로도 쓴다. 만들지 못했으면 `null`이고 앱은 목적지 사진을 보여 준다. 배포 시 `PUBLIC_BASE_URL`이 https면 https.
  - `summary`: finish 데이터로 계산(`RecapSummarizer`, 앱 `localSummary`와 같은 규칙). `distanceKm` 경로 길이(소수 첫째 자리), `durationMin` `endedAt-startedAt`(분, 버림), `stopCount` 예상 밖 정차(`StopDetector`, 앱과 같은 규칙: 60m 안 5분 이상·앞뒤 주행·목적지/경유지 300m 밖).
- 생성 중 예외(ffmpeg 실패 등) → `FAILED`. 앱이 finish를 다시 보내면 새로 생성한다.
- 없는 `tripId` → 404 `TRIP_NOT_FOUND`.

### 4. `GET /r/{token}` → 200 HTML (공유 페이지, F-08)

앱이 호출하지 않는다. QR을 찍은 휴대폰 브라우저가 연다.
- 목적지 사진(출처 표시)·이름·주소, 날짜, 요약 수치, 영상(포스터는 썸네일, 없으면 "준비 중")과 저장 버튼, 공유 버튼. 미리보기 이미지(`og:image`)는 썸네일.
- 출발지·경로 좌표 등 원본 위치 로그와 개인·차량 식별 정보는 넣지 않는다.
- 없는 토큰 404, 기간(`share.ttl`, 기본 30일)이 지난 토큰 410 — 둘 다 HTML 안내 페이지.
- `Referrer-Policy: no-referrer`, `X-Robots-Tag: noindex` 헤더.

### 5. `GET /r/{token}/video.mp4`, `GET /r/{token}/thumbnail.jpg` (리캡 미디어)

- 공유 페이지와 같은 토큰·만료 규칙(없으면 404, 기간 지남 410, 바디 없음). 영상은 Range 요청 지원(휴대폰 브라우저 재생).
- `Cache-Control: private, max-age=3600`, `Referrer-Policy: no-referrer`, `X-Robots-Tag: noindex`.

### 6. 지도 중계 (VWorld 키는 서버에만, 2026-11-14 앱 담당과 합의)

VWorld 인증키는 APK에서 꺼낼 수 있어 앱에 넣지 않는다. 앱은 위성 지도와 지역 이름을 이 서버를 거쳐 받는다(`MapController`·`MapProxyService`).
- `GET /api/v1/map/satellite/{z}/{x}/{y}.jpeg` → 200 `image/jpeg`(VWorld WMTS 위성 256px, `Cache-Control: private, max-age=604800`). z는 6~18, x·y는 0~2^z-1(밖이면 400). 키가 없거나 받지 못하면 503 `MAP_UNAVAILABLE`(앱은 지도 없는 배경). 최근 500장을 메모리에 둔다.
- `GET /api/v1/geo/area?lat=&lng=` → 200 `{"full": "서울특별시 용산구 동자동", "short": "용산구 동자동"}`. 못 찾거나 키가 없으면 둘 다 `null`. 좌표 범위 밖·누락은 400. 좌표는 저장하지 않는다.
- `GET /api/v1/geo/search?query=` → 200 `{"lat", "lng"}`(VWorld 검색: 장소 → 도로명 → 지번 순, 첫 결과). 못 찾으면 둘 다 `null`, query 비었거나 200자 초과면 400. 내비가 경유지 좌표를 주지 않을 때(에뮬레이터는 0,0) 앱이 경유지 이름·주소로 묻는다.

### 7. `GET /privacy.html` (개인정보처리방침·위치정보 이용약관)

앱이 호출하지 않는다. 앱 동의 화면의 QR이 연다(`{서버 주소}/privacy.html`, 위치정보 이용약관은 `#location-terms`). 정적 파일 `src/main/resources/static/privacy.html`.
- 보관 기간·처리 항목을 바꾸면 이 페이지와 앱 동의 화면(`ConsentScreen`, `ConsentStore.VERSION`)을 함께 고친다.

### 리캡 영상 내용 규칙 (F-07, 공유됨)

- 출발 좌표(`points[0]`)에서 직선 1.5km 안의 경로·정차는 그리지 않는다(`recap/RouteMask`, 다시 돌아온 구간 포함). 출발지는 VWorld 역지오코딩한 "구 동" 이름으로만 쓴다(handoff 3-3).
- 도착 문구는 `DESTINATION_ARRIVED` 이벤트 유무로 정한다(handoff 3-4).
- 경유지: `WAYPOINT_ADDED`·`WAYPOINT_ARRIVED` 이벤트 중 좌표를 아는 것(0,0은 "모름", 한국 밖 제외)만 이름 중복·100m 안 중복 없이 그린다. 경로선이 그 지점까지 오면 파란 핀과 이름표, 요약 장면에 "경유지" 줄. 출발지 가림 영역 안이면 그리지 않는다(`RecapMediaService.waypoints`).
- 앱 이름은 "갈래말래"(표지·썸네일 머리글, 요약 장면 아래, 공유 페이지 제목·하단). 정차 횟수 단위는 앱과 같은 "회".
- TourAPI 공공누리 3유형 사진은 자르거나 위에 글자를 얹지 않는다. 사진을 쓰면 "사진: 한국관광공사", 지도 타일을 쓰면 "지도: 브이월드(국토교통부)"를 표시한다.

## 비밀키·설정

- API 키, 토큰, DB 비밀번호 등 비밀값은 **환경변수** 또는 **`application-local.yml`**로만 관리한다.
- `application-local.yml`은 `.gitignore` 대상이며 절대 커밋하지 않는다. `application.properties`/`application.yml`, 코드, 테스트, 문서에 비밀값을 직접 쓰지 않는다.
- 설정에서는 `${ENV_NAME}` 플레이스홀더로 참조한다.

## 작업 규칙

- API를 추가하거나 수정하면 MockMvc 테스트(정상 + 오류 1개 이상)를 함께 작성하고 `./gradlew test` 통과를 확인한다.
- 테스트는 `src/test/resources/config/application.properties`로 네트워크·ffmpeg 없이 돈다(`recap.workers=0`이라 finish 안에서 바로 DONE, 영상 끔, 사진·VWorld 안 받음, DB는 H2 메모리). ffmpeg 인코딩 테스트는 ffmpeg가 있을 때만 돈다(`FFMPEG_PATH`).
- 영상 장면을 바꾸면 실제로 한 편 만들어 프레임을 눈으로 확인한다(ffmpeg로 프레임 추출).
- 실행/접속 방법은 `README.md` 참고.
