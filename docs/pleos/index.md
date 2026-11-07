# Pleos Connect 지식 베이스 (LLM Wiki)

Pleos Connect(Hyundai/Kia AAOS 인포테인먼트)용 앱을 설계·구현할 때 참고하는 문서 모음.
출처: https://document.pleos.ai — 2026-11-07 수집.

## 구조

```
docs/pleos/
├── index.md              ← 지금 이 파일 (진입점)
├── wiki/                 ← 정제된 요약. 먼저 여기를 읽는다
│   ├── planning-principles.md   앱 설계(기획) 가이드: 주행 안전, 지역 규제표, 카테고리, 화면 모델, HW/SDK 정책, 보안, 성능
│   ├── design-principles.md     디자인 가이드 개요: 철학, 운전용 앱 디자인, 테마, 디자인 토큰, Driving view, Gleo AI
│   ├── design-foundations.md    레이아웃/윈도우, 컬러, 타이포, 그림자, 애니메이션, 인터랙션, 아이콘, 앱 아이콘, 3D
│   ├── design-components.md     컴포넌트별 용도·스펙·상태·do/don't
│   ├── dev-guide.md             구현 가이드: 주행 규제, 오디오 포커스, 음악/내비 앱, CCP, IME, ADB, 에뮬레이터, 테스트, 앱 연동
│   └── review-checklist.md      앱 심사 체크리스트 + 제출/등록/보안/버전 정책
├── raw/                  ← 원문 전체 (HTML→Markdown 변환, 이미지는 원격 URL)
│   ├── pleos-only/vehicle-app-planning-guide/   앱 설계 가이드 (10)
│   ├── pleos-only/vehicle-app-developer-guide/  개발 가이드
│   ├── pleos-only/distribute/                   심사·제출 가이드
│   ├── pleos-only/app-registration-guide/       등록·보안·버전 정책
│   ├── pleos-only/app-test-guide/               테스트 가이드
│   ├── pleos-only/app-integration-guide/        앱 연동 가이드
│   └── connect/guide/                           디자인 가이드(docs-design), 시작하기, 용어집
└── tools/refresh.sh      ← 원문 재수집 스크립트
```

## 읽는 순서

1. 기능 기획 → `wiki/planning-principles.md` (TL;DR만 읽어도 핵심 제약 파악)
2. 화면 디자인 → `wiki/design-foundations.md` → `wiki/design-components.md`
3. 구현 → `wiki/dev-guide.md`
4. 출시 전 → `wiki/review-checklist.md`
5. 수치·세부가 필요하면 각 섹션 끝의 `raw/...` 경로로 원문 확인

## 미수집 범위

- Connect SDK: **NaviHelper·FusedLocation 가이드는 `raw/pleos-only/connect-sdk-pleos/`에 수집**. 단 API Reference(Dokka) 페이지는 로그인 필요로 본문이 비어 있음 → 시그니처는 AAR을 javap로 확인(결과는 `docs/product/scenario-v0-validation.md`). Vehicle·STT·TTS·LLM SDK는 미수집.
- 디자인 가이드의 많은 내용이 이미지(SVG/PNG)로만 제공됨 → raw 파일의 이미지 URL 참고. 특히 **컬러 토큰/hex, 타입 스케일, easing·duration, 해상도·dp 그리드, 컴포넌트 크기·radius는 텍스트에 없음** (지어내지 말 것). 원본 Figma: `https://document.pleos.ai/img/Pleos_Connect_design_assets_215.fig`, `https://document.pleos.ai/img/Pleos_Connect_display_guide_215.fig`, `https://document.pleos.ai/img/policy/Pleos-Connect-CCP-UX-guideline-20260220.fig`.
- 원문 간 불일치(스크린샷 크기 840x121 vs 840x1212, Connect-S 14.5 vs 14.6인치 등)는 각 wiki에 표기됨.

## 갱신

```bash
python3 -m venv /tmp/pleosvenv && /tmp/pleosvenv/bin/pip install beautifulsoup4 markdownify html5lib
docs/pleos/tools/refresh.sh /tmp/pleosvenv/bin/python
```
갱신 후 raw의 diff를 보고 wiki를 업데이트한다.
