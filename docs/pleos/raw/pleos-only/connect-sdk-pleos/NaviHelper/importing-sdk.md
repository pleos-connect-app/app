<!-- source: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/importing-sdk -->

# Importing NaviHelper SDK

## Maven 저장소 등록

프로젝트의 `settings.gradle.kts` 또는 `settings.gradle`에 NaviHelper SDK가 배포된 Maven 저장소를 추가합니다.

- Kotlin DSL
- Groovy

settings.gradle.kts

```
dependencyResolutionManagement {

repositories {

maven {

url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/")

}

}

}
```

settings.gradle

```
dependencyResolutionManagement {

repositories {

maven {

url "https://nexus-playground.pleos.ai/repository/maven-releases/"

}

}

}
```

## SDK 의존성 추가

앱 모듈의 빌드 파일에 NaviHelper SDK 의존성을 추가합니다.

- Kotlin DSL
- Groovy

build.gradle.kts

```
dependencies {

implementation("ai.pleos.playground:NaviHelper:3.0.5")

}
```

build.gradle

```
dependencies {

implementation "ai.pleos.playground:NaviHelper:3.0.5"

}
```

의존성을 추가한 후 Gradle 프로젝트를 동기화합니다. 사용할 SDK 버전은 프로젝트의 배포 기준에 맞춰 지정합니다.
