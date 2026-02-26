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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    // Modern way to set Kotlin options for Gradle 9
    kotlin {
        jvmToolchain(8)
    }
}