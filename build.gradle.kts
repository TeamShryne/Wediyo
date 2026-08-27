// Top-level build file for Wediyo
// Do NOT run Gradle locally — builds run only on GitHub Actions
// (gradlew is checked in for CI, local machine is metadata-only)

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
