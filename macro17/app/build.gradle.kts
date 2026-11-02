plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "app.macro17"
    compileSdk = 37
    defaultConfig {
        applicationId = "app.macro17"
        minSdk = 31            // canScheduleExactAlarms (API 31); WindowMetrics e WindowInsets.Type sem ramificação
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    // Tudo vem do repositório (macro17/libs, ver THIRD_PARTY.md): nenhuma biblioteca é baixada de repositório remoto.
    implementation(files("../libs/kotlin-stdlib-2.1.0.jar", "../libs/annotations-13.0.jar"))
    testImplementation(files("../libs/junit-4.13.2.jar", "../libs/hamcrest-core-1.3.jar"))
}
