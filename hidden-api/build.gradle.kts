plugins {
    alias(libs.plugins.android.library)
}

// Compile-only stubs for hidden framework classes; never packaged into the APK
android {
    namespace = "com.arter97.bydsplit.hiddenapi"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 29
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
