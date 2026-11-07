plugins { alias(libs.plugins.android.application) }
android {
    namespace = "ai.probe"
    compileSdk { version = release(37) }
    defaultConfig { applicationId = "ai.probe"; minSdk = 28; targetSdk = 34; versionCode = 1; versionName = "1" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
}
dependencies {
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")
}
