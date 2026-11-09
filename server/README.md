# RandomDrive Server

RandomDrive 앱(Pleos Connect)용 API 서버. Java 21 + Spring Boot.
현재는 mock(고정) 응답 + 메모리 저장이라 서버를 재시작하면 트립 데이터가 사라진다.

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
# 1) 랜덤 목적지 + tripId 발급
curl -X POST http://localhost:8080/api/v1/destinations/random \
  -H 'Content-Type: application/json' \
  -d '{"lat":37.5665,"lng":126.9780,"radiusKm":100}'

# 2) 주행 종료 (바디는 파일로) → 202
curl -X POST http://localhost:8080/api/v1/trips/{tripId}/finish \
  -H 'Content-Type: application/json; charset=utf-8' --data-binary @finish.json

# 3) 리캡 조회 (2번은 RUNNING, 3번째부터 DONE)
curl http://localhost:8080/api/v1/trips/{tripId}/recap
```

> Windows(Git Bash/PowerShell)에서 한글이 들어간 JSON을 `-d`로 바로 넘기면 인코딩이 깨져 400이 날 수 있다.
> 한글이 포함된 JSON은 UTF-8 파일로 저장해 `--data-binary @파일`로 보내거나 Postman 같은 도구를 쓴다.
