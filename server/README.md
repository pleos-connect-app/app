# RandomDrive Server

RandomDrive 앱(Pleos Connect)용 API 서버. Java 21 + Spring Boot.
랜덤 목적지는 `src/main/resources/candidates.json`의 후보(TourAPI 전국 관광지 약 1만 곳) 중에서 실제로 추첨하고, 주행 종료·리캡 조회는 아직 mock(고정) 응답이다.
저장은 메모리라 서버를 재시작하면 트립 데이터가 사라진다.

## 랜덤 목적지 후보 갱신 (TourAPI)

`candidates.json`은 커밋돼 있으므로 서버 실행만 할 때는 키가 필요 없다. 후보를 최신으로 다시 받을 때만 아래를 한다.

1. 공공데이터포털에서 "한국관광공사_국문 관광정보 서비스_GW" 활용신청 → 인증키(**Decoding 키**)
2. `src/main/resources/application-local.yml`에 넣는다(또는 환경변수 `TOUR_API_KEY`).
   ```yaml
   tour-api:
     service-key: "발급받은 Decoding 키"
   ```
3. 실행 (약 15초, API 13회 호출):
   ```bash
   ./gradlew importCandidates
   ```
4. 로그의 `후보 N건, 제외 {...}`를 확인하고 `git diff --stat`으로 변경 규모를 본 뒤 커밋한다.

파일을 직접 고치지 않는다(다시 가져오면 덮어씀). 후보 기준은 `tourapi/CandidateImporter.java`에서 바꾼다.
placeId 중복, 필수값 누락, 좌표 범위 오류가 있거나 `demo`가 없으면 서버가 시작되지 않는다.

API 계약과 개발 규칙은 [CLAUDE.md](CLAUDE.md) 참고.

## 요구사항

- JDK 21

## 실행

```bash
cd server
./gradlew bootRun          # Windows PowerShell/cmd: .\gradlew.bat bootRun
```

로그에 `Tomcat started on port 8080`이 보이면 준비 완료. 종료는 `Ctrl+C`.

로컬 설정(비밀키 등)을 쓸 때는 `src/main/resources/application-local.yml`에 넣고 `local` 프로필로 실행한다.

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

> `application-local.yml`은 `.gitignore` 대상이다. 절대 커밋하지 말 것.

## 테스트

```bash
./gradlew test
```

## 접속 주소

| 클라이언트 | Base URL |
|---|---|
| 같은 PC (브라우저, curl) | `http://localhost:8080` |
| **Android 에뮬레이터 (AAOS)** | **`http://10.0.2.2:8080`** |

에뮬레이터 안에서 `localhost`는 에뮬레이터 자신을 가리키므로, 호스트 PC의 서버에 접속하려면 앱에서 `10.0.2.2`를 써야 한다.
HTTPS가 아닌 HTTP이므로 앱 쪽에 디버그 빌드 한정 cleartext 허용(`network_security_config`)이 필요하다.

## 빠른 확인

```bash
# 1) 랜덤 목적지 + tripId 발급 (반경 안에서 랜덤, "demo":true면 항상 남한산성)
curl -X POST http://localhost:8080/api/v1/destinations/random \
  -H 'Content-Type: application/json' \
  -d '{"lat":37.5665,"lng":126.9780,"radiusKm":100}'

# 2) 주행 종료 (바디는 파일로) → 202
curl -X POST http://localhost:8080/api/v1/trips/{tripId}/finish \
  -H 'Content-Type: application/json; charset=utf-8' --data-binary @finish.json

# 3) 리캡 조회 (2번은 RUNNING, 3번째부터 DONE — DONE이면 shareUrl·summary가 채워짐)
curl http://localhost:8080/api/v1/trips/{tripId}/recap

# 4) 공유 페이지: 3)의 shareUrl을 브라우저로 연다
```

## 공유 링크 주소 (QR)

`shareUrl`은 `share.public-base-url`(기본 `http://localhost:8080`) + `/r/{token}`이다.
QR을 휴대폰으로 찍으면 휴대폰 브라우저가 이 주소를 열기 때문에 **localhost로는 휴대폰에서 열리지 않는다.** 시연·배포 때는 휴대폰에서 접속되는 주소로 바꾼다.

```bash
# 예: 같은 와이파이의 휴대폰으로 시험 (PC의 내부 IP)
PUBLIC_BASE_URL=http://192.168.0.10:8080 ./gradlew bootRun
# PowerShell: $env:PUBLIC_BASE_URL="http://192.168.0.10:8080"; .\gradlew.bat bootRun
```

공유 링크 유효 기간은 `share.ttl`(기본 `30d`). 서버 저장소가 메모리라 서버를 재시작해도 링크가 사라진다.

> Windows(Git Bash/PowerShell)에서 한글이 들어간 JSON을 `-d`로 바로 넘기면 인코딩이 깨져 400이 날 수 있다.
> 한글이 포함된 JSON은 UTF-8 파일로 저장해 `--data-binary @파일`로 보내거나 Postman 같은 도구를 쓴다.
