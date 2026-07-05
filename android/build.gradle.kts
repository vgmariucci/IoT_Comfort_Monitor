// Top-level build file — no plugin declarations needed here.
// All plugin versions are managed in gradle/libs.versions.toml
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android)      apply false
    alias(libs.plugins.hilt.android)        apply false
    alias(libs.plugins.ksp)                 apply false
    alias(libs.plugins.kotlin.compose) apply false
}