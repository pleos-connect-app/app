# 서버 실행·앱 연동 가이드 (macOS)

> 대상: 앱 담당(macOS, Pleos 에뮬레이터 사용 중)
> 목적: 최신 서버를 내 PC에서 띄우고, 에뮬레이터 앱을 붙여 "목적지 뽑기 → 주행 → 리캡 → QR → 휴대폰 공유 페이지"까지 확인
> 소요: 처음 한 번 15분 정도(JDK 설치 포함), 이후 1분

---

## 0. 준비 (처음 한 번)

### JDK 21

서버는 JDK 21이 꼭 필요합니다(17이나 25만 있으면 빌드가 toolchain을 못 찾고 실패).

```bash
/usr/libexec/java_home -V          # 설치된 JDK 목록. 21이 있으면 건너뜀
brew install --cask temurin@21     # 없으면 설치
```

`brew install openjdk@21`(cask가 아닌 것)로 설치하면 Gradle이 못 찾을 수 있습니다. 그 경우 위 cask로 설치하세요.

### ffmpeg (리캡 영상)

```bash
brew install ffmpeg
```

없어도 서버는 뜨지만 리캡에 영상이 없고 썸네일만 나옵니다(서버 시작 로그에 `ffmpeg not found` 경고).

### VWorld 키 (선택)

앱 `local.properties`의 `gallaemallae.vworldKey`와 같은 키를 서버에 넣으면 영상 경로 화면에 위성 지도가 깔리고 "용산구 동자동 →"처럼 출발 동네 이름이 들어갑니다. 없으면 단색 배경입니다.

```bash
export VWORLD_API_KEY=<앱과 같은 키>   # 서버를 켜는 터미널에서
```

### 최신 코드

```bash
git checkout main && git pull       # f38bce0 이후
ls -l server/gradlew                # -rwxr-xr-x 여야 함 (실행 권한)
```

예전에 받은 체크아웃에서 `permission denied`가 나면 `chmod +x server/gradlew`.

## 1. 서버 켜기

터미널 하나를 서버 전용으로 씁니다. **이 창은 계속 열어 두세요**(닫거나 `Ctrl+C`면 서버 꺼짐).

```bash
cd server
./gradlew bootRun
```

로그에 `Started ServerApplication`이 보이면 준비 완료(처음엔 의존성 받느라 1~2분).

> 휴대폰으로 QR까지 열어 보려면 대신 이렇게 켭니다(맥과 휴대폰이 같은 와이파이).
> ```bash
> PUBLIC_BASE_URL=http://$(ipconfig getifaddr en0):8080 ./gradlew bootRun
> ```
> 처음 실행 때 macOS가 "들어오는 네트워크 연결 허용?"을 물으면 **허용**.

### 서버만 빠르게 확인 (선택)

다른 터미널에서:

```bash
# 서울역 기준 50km 랜덤 목적지 → 실제 관광지 + https 이미지가 나와야 함
curl -s -X POST http://localhost:8080/api/v1/destinations/random \
  -H 'Content-Type: application/json' \
  -d '{"lat":37.5547,"lng":126.9707,"radiusKm":50}'

# 후보 없는 좌표(바다) → 404 NO_CANDIDATE
curl -s -X POST http://localhost:8080/api/v1/destinations/random \
  -H 'Content-Type: application/json' \
  -d '{"lat":30.0,"lng":125.0,"radiusKm":150}'
```

서버 테스트(56개)도 돌려 볼 수 있습니다: `cd server && ./gradlew test`

## 2. 앱을 서버에 붙이기

에뮬레이터를 켠 상태에서, **프로젝트 루트**(서버 창이 아닌 다른 터미널)에서:

```bash
./gradlew :app:installDebug -Pgallaemallae.useFakeServer=false -Pgallaemallae.serverBaseUrl=http://10.0.2.2:8080
```

### 에뮬레이터 위치를 한국으로 (중요)

에뮬레이터 기본 GPS는 미국이라, 그대로 뽑으면 근처에 후보가 없어 **"반경 50km 안에 추천할 장소가 없어요"**(`NO_CANDIDATE`)가 나옵니다. 앱을 열기 전에 서울역으로 옮겨 두세요.

```bash
~/Library/Android/sdk/platform-tools/adb emu geo fix 126.9707 37.5547   # 경도, 위도 순서
```

## 3. 확인할 것 (체크리스트)

| # | 할 일 | 기대 결과 |
|---|---|---|
| 1 | 반경 **50km** → 목적지 뽑기 | 실제 관광지(서울역 기준 3~50km)가 나오고, 앱 내장 15곳에 없는 장소도 나옴. 사전 검사("선택한 반경보다 먼 목적지예요")에 걸리지 않음 |
| 2 | 다시 뽑기 몇 번 | 매번 다른 곳(대부분) |
| 3 | 내비 시작 → 아래 주행 시뮬레이션 → 도착 → 여행 종료 | 리캡 생성(10초 안팎) → QR. 결과 화면 썸네일은 `thumbnailUrl`(16:9, 아직 앱이 안 붙였으면 목적지 사진) |
| 4 | 결과 화면의 요약 | 앱 로컬 계산과 같은 숫자(거리는 소수 첫째 자리). **정차 1번** 시나리오로 정차 수 확인 |
| 5 | QR을 휴대폰 카메라로 | 공유 페이지: 장소 사진·이름·주소·날짜·이동 거리·주행 시간·예상 밖 정차, **리캡 영상(10초, 세로) 재생·저장**, 공유 버튼. 출발지·경로 좌표는 안 보여야 하고, 영상의 경로선은 출발지 1.5km 밖부터 시작 |
| 6 | (선택) demo 빌드 `-Pgallaemallae.demoMode=true` 추가 설치 | 항상 남한산성 |

참고: 트립은 DB에 저장되므로 서버를 재시작해도 여행·공유 링크가 남습니다.

### 주행 시뮬레이션

목적지 좌표는 앱 결과 화면이나 서버 응답의 `lat`/`lng`를 넣습니다.

```bash
# 서울역 → 목적지, 90초 주행, 도착 후 20초 머무름(앱 도착 판정)
tools/emu-drive.py 37.5547 126.9707 <목적지_lat> <목적지_lng> --seconds 90 --dwell 20

# 정차 1번 시나리오: 중간(50%)에서 6분 정차 → 요약의 예상 밖 정차 1번
tools/emu-drive.py 37.5547 126.9707 <목적지_lat> <목적지_lng> --seconds 90 --stop-at 0.5 --stop-seconds 360 --dwell 20
```

## 4. 문제가 생기면

| 증상 | 원인·해결 |
|---|---|
| `./gradlew: permission denied` | 예전 체크아웃. `chmod +x server/gradlew` |
| `Cannot find a Java installation ... languageVersion=21` | JDK 21 없음 → 0장 |
| `Port 8080 was already in use` | 이전 서버가 떠 있음. `lsof -nP -iTCP:8080 -sTCP:LISTEN`으로 PID 확인 후 `kill <PID>` |
| 앱: "목적지를 불러오지 못했어요" | 서버 꺼짐 또는 주소 오류. 서버 창 확인, 설치 명령의 `serverBaseUrl=http://10.0.2.2:8080` 확인 |
| 앱: "반경 50km 안에 추천할 장소가 없어요" | 에뮬레이터 위치가 한국이 아님 → 2장 `geo fix` |
| QR이 휴대폰에서 안 열림 | 서버를 `PUBLIC_BASE_URL` 없이 켬(localhost) → 1장 방법으로 다시 켜기. 같은 와이파이인지, macOS 방화벽 허용했는지 확인 |
| 공유 페이지 "링크를 찾을 수 없어요" | QR 주소가 다른 서버(로컬↔AWS)의 것. 앱이 붙은 서버와 같은지 확인 |
| 공유 페이지에 "리캡 영상은 준비 중이에요" | ffmpeg 없음 → 0장 `brew install ffmpeg` 후 서버 다시 켜기 |
| 영상에 지도 없이 단색 배경, 출발지 이름 없음 | VWorld 키 없음(정상 동작) → 0장 VWorld 키 |

결과(스크린샷·이상한 점)는 `docs/product/server-report.md` "3. 부탁드릴 것" 기준으로 알려 주시면 됩니다.
