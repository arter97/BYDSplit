import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing; app/signing.properties is kept out of version control
val signingProperties = Properties().apply {
    val file = file("signing.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.arter97.bydsplit"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.arter97.bydsplit"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        if (!signingProperties.isEmpty) {
            create("release") {
                storeFile = file(signingProperties.getProperty("storeFile"))
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // Sign every build type with the same key when available
        all {
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        release {
            optimization {
                enable = true
                keepRules {
                    files.add(file("proguard-rules.pro"))
                }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    androidResources {
        localeFilters += listOf("en", "ko")
    }
    packaging {
        resources.excludes += listOf("META-INF/**", "kotlin/**", "**.kotlin_builtins", "DebugProbesKt.bin")
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.hiddenapibypass)
    compileOnly(project(":hidden-api"))
    debugImplementation(libs.androidx.compose.ui.tooling)
}
// Baseline profiles only speed up first launch; not worth the size for this app
configurations.configureEach {
    exclude(group = "androidx.profileinstaller", module = "profileinstaller")
}
