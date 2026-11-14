# 갈래말래 (Gallaemallae) — 팀 경로이탈

저장소 이름은 `random_drive_n_recap`(옛 이름 랜덤드라이브). 앱 패키지 `com.gyeongroital.gallaemallae`, 빌드 속성 접두사 `gallaemallae.*`.

Pleos Connect(Hyundai/Kia AAOS 인포테인먼트)에 탑재할 Android 앱.

## 필독

- 이 앱의 모든 기획·디자인·구현은 Pleos Connect 가이드라인을 따른다. 작업 전 `pleos-connect-app-design` 스킬(`.claude/skills/pleos-connect-app-design/SKILL.md`)을 사용하고, 지식 베이스 진입점은 `docs/pleos/index.md`.
- 핵심 제약 요약: 주행 중 동작은 `CarUxRestrictions` 기준, portrait/landscape(1/2 window) 반응형, 하드웨어 직접 접근 금지(AAOS Car API / Pleos Connect SDK), CPU·RAM 상한(비내비 주행 중 CPU <5%, RAM ≤400MB), 큰 터치 타깃·입력 최소화.
- 제품 기능 명세(MVP): `docs/product/gallaemallae-mvp-spec.md` — 기능 구현 시 F-01~F-08 번호로 참조.
- v0 구현 현황·검증 결과·미결 사항: `docs/product/v0-app-report.md`. 서버에 요청할 것: `docs/product/server-handoff.md`.

## 에뮬레이터 작업 팁

- AAOS 사용자 ID가 10이라 앱 파일은 `adb shell run-as com.gyeongroital.gallaemallae --user 10 ...`으로 읽는다.
- 가상 주행: `tools/emu-drive.py` (앱 LocationManager만 움직임, 내비 위치는 시흥동 고정).
- 문서의 `cmd hmg_car_service ... driving_regulation` 명령은 에뮬레이터 v2.1.0에 없음 → Virtual Console의 Drive status 패널에서 D + ACCEL로 주행 상태를 만든다(P 복귀도 Drive status 패널의 P).
