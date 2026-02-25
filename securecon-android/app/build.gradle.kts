plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    
    // ADD THIS LINE
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "com.example.suraksha_app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.suraksha_app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0-Phase1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Crypto
    implementation(libs.androidx.security.crypto)
}