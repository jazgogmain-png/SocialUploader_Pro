import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lola.pro"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lola.pro"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        val properties = Properties()
        val propertiesFile = project.rootProject.file("local.properties")
        if (propertiesFile.exists()) {
            properties.load(propertiesFile.inputStream())
        }

        buildConfigField("String", "TIKTOK_KEY", "\"${properties.getProperty("TIKTOK_CLIENT_KEY") ?: ""}\"")
        buildConfigField("String", "TIKTOK_SECRET", "\"${properties.getProperty("TIKTOK_CLIENT_SECRET") ?: ""}\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"${properties.getProperty("GEMINI_API_KEY") ?: ""}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2023.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Security Crypto (The Vault Lock)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // --- GEMINI FIX (0.9.0 -> 0.9.1) ---
    // This fixes the "Unknown enum: PROHIBITED_CONTENT" crash
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    // Ktor
    implementation("io.ktor:ktor-client-core:2.3.11")
    implementation("io.ktor:ktor-client-android:2.3.11")
    implementation("io.ktor:ktor-client-okhttp:2.3.11")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.11")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.11")
    implementation("io.ktor:ktor-client-logging:2.3.11")

    // TikTok SDK
    implementation("com.tiktok.open.sdk:tiktok-open-sdk-core:2.4.0")
    implementation("com.tiktok.open.sdk:tiktok-open-sdk-auth:2.4.0")

    // FFmpeg Kitchen
    implementation(files("libs/ffmpeg-kit-full-gpl-6.0-2.LTS.aar"))
    implementation("com.arthenica:smart-exception-java:0.2.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}