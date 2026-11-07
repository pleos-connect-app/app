<!-- source: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/project-setup -->

# Importing FusedLocation SDK

FusedLocation SDK를 사용하려면 Maven 저장소를 등록하고 앱 모듈에 SDK 의존성을 추가합니다.

## Maven 저장소 등록

프로젝트의 `settings.gradle.kts` 또는 `settings.gradle`에 FusedLocation SDK가 배포된 Maven 저장소를 추가합니다.

- Kotlin DSL
- Groovy

settings.gradle.kts

```
dependencyResolutionManagement {

repositories {

maven("https://nexus-playground.pleos.ai/repository/maven-releases/")

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

앱 모듈의 `build.gradle.kts` 또는 `build.gradle`에 FusedLocation SDK 의존성을 추가합니다. 아래 예시는 SDK `1.1.3`을 기준으로 합니다.

- Kotlin DSL
- Groovy

build.gradle.kts

```
dependencies {

implementation("ai.pleos.playground:FusedLocation:1.1.3")

}
```

build.gradle

```
dependencies {

implementation "ai.pleos.playground:FusedLocation:1.1.3"

}
```

의존성을 추가한 후 Gradle 프로젝트를 동기화합니다.
