# SDK probe (검증용, 앱 코드 아님)

2026-11-07 에뮬레이터 검증에 사용한 최소 앱. 별도 Gradle 프로젝트로 빌드했다.
settings.gradle.kts 저장소에 `maven("https://nexus-playground.pleos.ai/repository/maven-releases/")` 추가 필요.

```
adb install -r -g app-debug.apk
adb shell am start -n ai.probe/.ProbeActivity --es cmd route   # route|near|far|trip|fg|dest|cur|state|cancel|last|fused|unfused|blocked
adb logcat -s PROBE:I
```
