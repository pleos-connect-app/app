<!-- source: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator -->

# Pleos Connect Emulator 설치 및 실행

## 소프트웨어 개발 환경

Pleos Connect SDK를 사용하기 위한 소프트웨어 개발 환경은 다음과 같습니다.

| 구분 | 버전 |
| --- | --- |
| Minimum Android SDK | **Android API 28 이상** |
| Gradle 버전 | 8.0 이상 |
| 개발 언어 | Java, Kotlin |
| Pleos Connect Emulator | 버전 2.1.0 |
| Pleos Connect Virtual Console | Pleos Connect Virtual Console를 지원하는 Pleos Connect Emulator 최소 버전: 2.1.0 |

주의

minSDK 버전은 Android API 28 이상이어야 합니다. 이는 차량 사이버 보안 요건(사이닝 키 로테이션·리니지 지원)에 따른 요구사항입니다.

## Pleos Connect Emulator

Pleos Connect Emulator는 Android Studio와 같은 Android 개발 도구에서 Pleos Connect 앱을 개발하고 검증할 수 있는 가상 실행 환경입니다. Android API와 Pleos Connect API를 함께 테스트할 수 있으며, 차량 제어 기능과 차량 컨텍스트 데이터를 에뮬레이션해 앱의 동작을 확인할 수 있습니다.

### Installing the Pleos Connect Emulator

#### Prerequisite

1. Android Studio의 최신 버전을 설치합니다.
2. Android Studio는 [여기](https://developer.android.com/studio?hl=ko)에서 다운로드 가능합니다.
3. Android Emulator를 실행할 수 있는 [시스템 요구 사항](https://developer.android.com/studio/run/emulator?hl=ko#requirements)이 있는지 확인합니다.

#### Emulator를 Android Studio에 추가하기

정보

Pleos Connect Emulator의 이전 버전이 설치되어 있다면 기존 버전을 삭제한 후 다음 설치 절차를 진행해 주시기 바랍니다.

1. Android Studio에서 **Menu** > **Tools** > **SDK Manager**를 선택합니다.
2. **SDK Update Sites** 탭을 클릭합니다.
3. 목록 좌측 상단에 있는 **+** 버튼을 클릭합니다.
   ![](https://document.pleos.ai/assets/images/portal-avd-install1-bf1d177c47f6f13f34abd5839ed39ceb.png)

4. 아래의 사항을 입력해 Pleos Connect SDK site를 추가합니다.

   - Name: Pleos Connect System Image
   - URL for Mac: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
   - URL for Windows/Linux: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`![](https://document.pleos.ai/assets/images/avd-download-url-3810c5dcc787cabe9235cbc691e360ef.png)

5. **OK** 버튼을 클릭합니다.
6. **SDK Platforms** 탭을 클릭합니다.
7. Android API Level 34에 있는 Pleos Connect system Image를 체크합니다.

   ![](https://document.pleos.ai/assets/images/portal-avd-android-34-d1d8b12551f67e7c2a0fd9106fdf1d6e.png)
8. 하단에 있는 **Show Package Details** 체크박스도 활성화합니다.
9. **OK** 버튼을 클릭하면 아래 그림과 같이 Pleos Connect Emulator image 설치가 진행됩니다.

   ![](https://document.pleos.ai/assets/images/portal-avd-installer-0240980e26aa7efe1c9ff55e4256d360.png)
10. 설치가 완료되면 **Finish** 버튼을 누르고 Android Studio를 재시작합니다.

### Setting up the emulator

1. Android Studio를 시작합니다. (이전 단계에서 Android Studio를 재시작하지 않았다면 먼저 재시작합니다)
2. Pleos Connect Emulator를 이용하여 Android Virtual Device(AVD)를 생성합니다.
3. Android Studio에서 **Menu** > **Tools** > **Device Manager**를 선택합니다. 이전 버전의 Android Studio일 경우 **Menu** > **Tools** > **AVD Manager**를 선택합니다.
4. **Create device**를 클릭합니다. 이전 버전의 Android Studio일 경우 **Create Virtual Device**를 클릭합니다.
5. 왼쪽 **Form Factor**에서 `Automotive`를 선택합니다.
6. `Pleos Connect v2.1.0`을 선택하고 **Next** 버튼을 클릭합니다.

   ![](https://document.pleos.ai/assets/images/portal-x86-setting-up-5fb9af480965ca30f4bc9363761d78a5.png)
7. **Configure virtual device** 창에서 **Advanced Settings** 탭을 선택합니다. 이전 버전의 Android Studio일 경우 **Show Advanced Settings** 버튼을 클릭합니다.

   ![](https://document.pleos.ai/assets/images/portal-x86-setting-up-1-4d11fcb55df0e19a9287de8e2752cb1d.png)
8. **Startup** > **Default boot** 항목에서 `Cold`를 선택합니다. 이전 버전의 Android Studio일 경우 **Emulated Performance** 항목에서 **Graphics** 옵션을 `Hardware`로 설정하고 **Boot option** 항목에서 `Cold boot`를 선택합니다.

   ![](https://document.pleos.ai/assets/images/portal-x86-setting-up-2-fd74e669a07f76cd2e0d0d744d7ca60e.png)
9. **Emulated Performance** 항목에서 **RAM** Size를 `4 GB`로 설정합니다.

   ![](https://document.pleos.ai/assets/images/portal-x86-setting-up-3-eeffd450a3475ae9f5acc8e30c9d6fb8.png)
10. 다시 **Device** 탭으로 이동하여 **Select system image** > **API** 항목에서 `API 34`를 선택한 후, 하단 테이블에 나타나는 `Pleos Connect v2.1.0 APIs`를 선택하고 **Finish** 버튼을 누릅니다.

    ![](https://document.pleos.ai/assets/images/portal-x86-setting-up-4-74caa19a5b3cea9bb74ddc639babccff.png)

### Running on the emulator

- Android Studio에서 **Menu** > **Tools** > **Device Manager**에서 생성한 AVD를 실행합니다.

![](https://document.pleos.ai/assets/images/avd-start-ko-1b864d2b1823d7c565b2bfde7ca79c6b.png)

노트

에뮬레이터의 실행 환경 설정은 [Pleos Connect Emulator 설정](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator)을 참조하시기 바랍니다.

### Troubleshooting

Android Emulator에는 알려진 여러 가지 문제점이 있으니 아래 링크를 참고하시기 바랍니다.
[Android Emulator의 알려진 문제 해결](https://developer.android.com/studio/run/emulator-troubleshooting?hl=ko)

Android Studio와 Gradle plugin에도 알려진 여러 가지 문제점이 있으니 아래 링크를 참고하시기 바랍니다.
[Android 스튜디오 및 Android Gradle 플러그인의 알려진 문제](https://developer.android.com/studio/known-issues?hl=ko)
