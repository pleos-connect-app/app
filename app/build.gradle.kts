plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 서버 연결 설정. 기본은 앱 내장 가짜 서버(FakeRecapApi)라 서버 없이도 전체 흐름이 동작한다.
//   ./gradlew installDebug -Prandomdrive.useFakeServer=false -Prandomdrive.serverBaseUrl=http://10.0.2.2:8080
//   -Prandomdrive.demoMode=true  → 추첨 결과를 시연용 목적지(남한산성)로 고정
fun prop(name: String, default: String) = (project.findProperty(name) as String?) ?: default
val useFakeServer = prop("randomdrive.useFakeServer", "true").toBoolean()
val serverBaseUrl = prop("randomdrive.serverBaseUrl", "http://10.0.2.2:8080")
val demoMode = prop("randomdrive.demoMode", "false").toBoolean()

android {
    namespace = "ai.randomdrive.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "ai.randomdrive.app"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "USE_FAKE_SERVER", useFakeServer.toString())
        buildConfigField("String", "SERVER_BASE_URL", "\"$serverBaseUrl\"")
        buildConfigField("boolean", "DEMO_MODE", demoMode.toString())
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.zxing.core)
    implementation(libs.pleos.navihelper)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
