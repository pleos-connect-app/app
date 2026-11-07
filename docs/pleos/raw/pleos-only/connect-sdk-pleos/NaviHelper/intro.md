<!-- source: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro -->

# Introduction

## NaviHelper SDK 소개

NaviHelper SDK는 경로 안내 제어와 내비게이션 정보 조회를 위한 API를 제공합니다. 3rd party 앱은 NaviHelper SDK를 통해 Pleos Connect의 Navi Service​와 연동됩니다. 3rd party 앱이 목적지·경유지 설정, 경로 변경, 정보 조회 또는 Navigation 화면 표시를 요청하면 Navi Service​가 이를 처리하고, 처리 결과와 주행 상태 변화를 이벤트로 전달합니다.

![](https://document.pleos.ai/assets/images/navi-helper-509b526aa25dda110c7187abe640b76e.png)

SDK 버전

이 가이드는 NaviHelper SDK v3.0.5를 기준으로 작성되었습니다.

## 제공 기능

NaviHelper SDK는 다음 기능을 제공합니다.

- 목적지 설정·확인·취소
- 경로 옵션 변경 및 재탐색
- 경유지 추가·조회·제거
- 경유지를 포함한 경로 요청 ([Route requests with waypoints](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/route-with-waypoints))
- 경로 탐색과 주행 상태 이벤트 수신
- 내비게이션 정보 조회 및 Navigation 화면 요청 (API Reference 참고)

NaviHelper 요청은 Pleos Connect Navigation 앱에 전달됩니다. 요청 결과와 주행 중 변경 사항은 `NaviHelperEventListener`의 콜백으로 전달됩니다. 실제로 전달되는 이벤트는 Navigation 앱의 구현과 현재 상태에 따라 달라질 수 있습니다.

## 진입점

`NaviHelper`가 SDK의 진입점입니다. 일반적인 사용 순서는 다음과 같습니다.

1. `NaviHelper` 객체를 생성합니다.
2. `initialize()`로 SDK 연결을 초기화합니다.
3. `addListener()`로 이벤트 리스너를 등록합니다.
4. 경로 요청 또는 정보 조회 API를 호출합니다.
5. 콜백으로 결과와 상태 변화를 처리합니다.
6. `removeListener()`와 `release()`로 리소스를 정리합니다.

패키지 구조와 전체 API 명세는 [NaviHelper API Reference](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/entry)에서 확인할 수 있습니다.

## Emulator setup

NaviHelper SDK를 사용하려면 Android Studio에 Pleos Connect Emulator를 설치하고 AVD를 설정해야 합니다.

1. [소프트웨어 개발 환경 요구사항 확인](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator#소프트웨어-개발-환경)
2. [Pleos Connect Emulator 설치](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator#installing-the-pleos-connect-emulator)
3. [AVD 생성 및 실행](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator#setting-up-the-emulator)
4. [Pleos Connect Virtual Console 설정](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)
5. [마이크 접근 설정](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator#마이크-접근-설정)
6. [Cold boot 설정](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator#cold-boot-설정)

## 다음 단계

1. [NaviHelper SDK 가져오기](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/importing-sdk)
2. [권한 선언](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions)
3. [SDK 초기화 및 해제](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/initialization-and-release)
4. [이벤트 리스너](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/event-listeners)
5. [목적지 설정](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations)
6. [경로 설정](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/routes)
7. [경유지 설정](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/waypoint-settings)
8. [오류 처리](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/error-handling)
9. [NaviHelper API Reference](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/entry)
