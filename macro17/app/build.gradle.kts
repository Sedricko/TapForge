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
        // Só arm64: é o que está em maven-repo (OpenCV e ML Kit foram reduzidos a esta arquitetura).
        ndk { abiFilters += "arm64-v8a" }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    // Mensagem e pilha das falhas no log do CI (sem isto só aparece "AssertionError at Arquivo.kt:linha").
    testOptions { unitTests.all { it.testLogging { exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL; events("failed") } } }
}
dependencies {
    // O runtime do Kotlin e o JUnit vêm de libs/; OpenCV e ML Kit vêm de maven-repo (ver THIRD_PARTY.md).
    implementation(files("../libs/kotlin-stdlib-2.1.0.jar", "../libs/annotations-13.0.jar"))
    // O Kotlin que estas bibliotecas declaram é ignorado: o stdlib acima já está no app (evita classes duplicadas).
    implementation("org.opencv:opencv:4.9.0") { exclude(group = "org.jetbrains.kotlin"); exclude(group = "org.jetbrains.kotlinx") }
    implementation("com.google.mlkit:text-recognition:16.0.1") { exclude(group = "org.jetbrains.kotlin"); exclude(group = "org.jetbrains.kotlinx") }
    testImplementation(files("../libs/json-20240303.jar", "../libs/junit-4.13.2.jar", "../libs/hamcrest-core-1.3.jar"))
}
