<!-- source: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro -->

# Introduction

## FusedLocation SDK 소개

FusedLocation SDK는 앱이 차량 위치 데이터를 수신할 수 있도록 API를 제공합니다. Pleos Connect 앱은 FusedLocation SDK를 통해 Pleos Connect의 FusedLocation Service와 연동됩니다.

![FusedLocation SDK architecture diagram](https://document.pleos.ai/assets/images/fused-sdk-diagram-e46e820477eb65e7355d75fab4a325a3.png)

SDK 버전

이 가이드는 FusedLocation SDK v1.1.3을 기준으로 작성되었습니다.

## 제공 기능

FusedLocation SDK는 다음과 같은 위치 데이터 접근 기능을 제공합니다.

- 마지막으로 확인된 위치 1회 조회: `getLastKnownLocation()`
- 위치 업데이트 수신: `registerFusedLocationCallback()`
- 위치 업데이트 중지: `unregisterFusedLocationCallback()`
- FusedLocation SDK 지원 환경 확인: `isSupported()`
- 위치 접근이 차단된 경우 안내 팝업 표시: `showLocationAccessBlockedPopup()`
- 설정 영역 진입/이탈 알림 등록: `addProximityAlert()`
- 설정 영역 진입/이탈 알림 해제: `removeProximityAlert()`

`getLastKnownLocation()`은 새로운 위치를 계산하는 API가 아니라 현재 사용할 수 있는 마지막 위치를 반환하는 API입니다. 위치 데이터가 없으면 콜백에 `null`이 전달될 수 있습니다.

## FusedLocation SDK 지원 환경 확인

FusedLocation API를 사용하기 전에 `isSupported()`로 현재 실행 환경의 지원 여부를 확인해야 합니다.
지원되지 않는 환경에서는 API가 정상적으로 동작하지 않을 수 있으므로, FusedLocation 기능을 제공하지 않도록 처리해야 합니다.

## 진입점

`FusedLocation` 클래스가 SDK의 주요 진입점입니다. 일반적인 사용 순서는 다음과 같습니다.

1. `FusedLocation` 객체를 생성합니다.
2. `isSupported()`로 현재 실행 환경이 FusedLocation SDK를 지원하는지 확인합니다.
3. `initialize()`로 SDK를 초기화합니다.
4. `getLastKnownLocation()`으로 마지막 위치를 조회하거나 `registerFusedLocationCallback()`으로 위치 업데이트 수신을 시작합니다.
5. `unregisterFusedLocationCallback()`으로 위치 업데이트 수신을 중지합니다.
6. SDK 사용이 끝나면 `release()`로 리소스를 해제합니다.

패키지 구조와 전체 API 명세는 [FusedLocation API Reference](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/entry)에서 확인할 수 있습니다.

## 위치 데이터

위치 결과는 `LocationData`로 전달됩니다. `LocationData`는 위도, 경도, 고도, 진행 방향, 신뢰도 및 타임스탬프를 포함합니다.
필드의 타입과 상세 설명은 [LocationData API Reference](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused.model/location-data/entry)를 참고하세요.

## 권한 및 접근 제약

위치 API를 사용하려면 `pleos.car.permission.FUSED_LOCATION` 권한을 선언해야 합니다. 이 권한은 SDK의 Dokka API Reference에서 각 위치 API에 요구 권한으로 정의되어 있습니다. 권한 선언 방법은 [권한 선언](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/declaring-permissions)을 참고하세요.

## Emulator setup

FusedLocation SDK는 Pleos Connect Emulator 환경에서 개발 및 검증합니다. 아래 순서로 개발 환경을 준비하세요.

1. [소프트웨어 개발 환경 요구사항 확인](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator#소프트웨어-개발-환경)
2. [Pleos Connect Emulator 설치](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator#installing-the-pleos-connect-emulator)
3. [AVD 생성 및 실행](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator#setting-up-the-emulator)
4. [Pleos Connect Virtual Console 설정](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)
5. [마이크 접근 설정](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator#마이크-접근-설정)
6. [Cold boot 설정](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator#cold-boot-설정)

## 다음 단계

1. [FusedLocation SDK 가져오기](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/project-setup)
2. [권한 선언](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/declaring-permissions)
3. [위치 데이터 접근](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/location-data-access)
4. [SDK 초기화 및 해제](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/initialization-and-release)
5. [오류 처리](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/error-handling)
6. [FusedLocation API Reference](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/entry)
