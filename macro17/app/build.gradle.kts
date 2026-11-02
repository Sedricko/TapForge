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
    // Nenhuma dependência de runtime além do stdlib do Kotlin. JUnit é só de teste.
    testImplementation("junit:junit:4.13.2")
}
