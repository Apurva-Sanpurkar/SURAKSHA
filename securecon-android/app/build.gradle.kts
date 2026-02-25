plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Removed the 'kotlin-compose' alias here if it's causing issues, 
    // we will stick to standard XML Views for now as per your layout.
}

android {
    namespace = "com.example.suraksha_app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.suraksha_app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // This fixes the warning about 'kotlinOptions'
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }
}

dependencies {
    // Use direct implementation for core libraries to avoid "mutation" errors
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    // CameraX - Use versions directly here to ensure they don't get "mutated" during resolve
    val camerax_version = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax_version")
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")

    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}