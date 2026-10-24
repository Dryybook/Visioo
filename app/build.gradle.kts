plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val apiBaseUrl = providers.gradleProperty("visioo.apiBaseUrl")
    .orElse("http://10.0.2.2:3000/api/v1/")
val googleWebClientId = providers.gradleProperty("visioo.googleWebClientId").orElse("")
val termsUrl = providers.gradleProperty("visioo.termsUrl").orElse("")
val privacyUrl = providers.gradleProperty("visioo.privacyUrl").orElse("")
val apiBaseUrlRelease = providers.gradleProperty("visioo.apiBaseUrl.release")
    .orElse("https://api.example.com/api/v1/")

android {
    namespace = "com.visioo.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.visioo.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    defaultConfig {
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${googleWebClientId.get()}\"")
        buildConfigField("String", "TERMS_URL", "\"${termsUrl.get()}\"")
        buildConfigField("String", "PRIVACY_URL", "\"${privacyUrl.get()}\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl.get()}\"")
            manifestPlaceholders["usesCleartext"] = "true"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrlRelease.get()}\"")
            manifestPlaceholders["usesCleartext"] = "false"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // The Kotlin compiler already enforces Media3's @UnstableApi opt-in (see @file:OptIn in the player files).
        // Lint's own check only understands androidx.annotation.OptIn, so it would flag that valid opt-in.
        disable += "UnsafeOptInUsageError"
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:network"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play)
    implementation(libs.googleid)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
