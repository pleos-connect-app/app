# random_drive_n_recap

Pleos Connect(Hyundai/Kia AAOS 인포테인먼트)에 탑재할 Android 앱.

## 필독

- 이 앱의 모든 기획·디자인·구현은 Pleos Connect 가이드라인을 따른다. 작업 전 `pleos-connect-app-design` 스킬(`.claude/skills/pleos-connect-app-design/SKILL.md`)을 사용하고, 지식 베이스 진입점은 `docs/pleos/index.md`.
- 핵심 제약 요약: 주행 중 동작은 `CarUxRestrictions` 기준, portrait/landscape(1/2 window) 반응형, 하드웨어 직접 접근 금지(AAOS Car API / Pleos Connect SDK), CPU·RAM 상한(비내비 주행 중 CPU <5%, RAM ≤400MB), 큰 터치 타깃·입력 최소화.
