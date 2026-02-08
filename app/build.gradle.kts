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
    composeOptions {
        // Updated to match a more modern Kotlin/Compose alignment
        kotlinCompilerExtensionVersion = "1.5.10"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // UPDATED BOM: This aligns all Compose versions automatically to prevent the NoSuchMethodError
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))

    // Core Android
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Jetpack Compose (UI) - Versions are now managed by the BOM above
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Icons: Now aligned with the BOM and correctly resolved
    implementation("androidx.compose.material:material-icons-extended")

    // WorkManager (For scheduling posts)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Gemini (Upgraded to handle G3 Flash logic better)
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    // Ktor (Networking & Video Uploads)
    implementation("io.ktor:ktor-client-core:2.3.11")
    implementation("io.ktor:ktor-client-android:2.3.11")
    implementation("io.ktor:ktor-client-okhttp:2.3.11")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.11")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.11")
    implementation("io.ktor:ktor-client-logging:2.3.11")

    // TikTok SDK
    implementation("com.tiktok.open.sdk:tiktok-open-sdk-core:2.4.0")
    implementation("com.tiktok.open.sdk:tiktok-open-sdk-auth:2.4.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // THE VAULT
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}