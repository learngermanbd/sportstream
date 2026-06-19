// Top-level build file. Sub-project/plugin configuration per module.
// Step 1.1: declare plugin coordinates only (apply false).
// Step 1.2 will keep these and add: com.google.gms.google-services (for FCM only).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.google.services) apply false
}
