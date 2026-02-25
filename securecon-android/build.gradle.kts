plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    
    // ENSURE THIS MATCHES THE TOML NAME (kotlin-compose becomes kotlin.compose)
    alias(libs.plugins.kotlin.compose) apply false
}