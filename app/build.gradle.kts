plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}
android {
    namespace = "com.tapforge"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.tapforge"
        minSdk = 24            // dispatchGesture exige API 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes { release { isMinifyEnabled = true } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.google.mlkit:text-recognition:16.0.1") // OCR embutido, offline
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
