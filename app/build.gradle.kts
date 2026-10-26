plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "org.yourchurch.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.osec.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        resourceConfigurations += listOf("en", "nb", "no", "om")
    }

    buildTypes {
        release {
            // Makes the app much smaller: unused code and unused pictures are removed, the rest is packed tighter.
            // Test the release build well before it goes to people (the checklist in SHRINK-NOTES.md).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    // The app has three languages (English, Norsk, Afaan Oromoo). Without this, every library brings the texts of 80 languages.
    // Norwegian is written "nb" in the app and "no" in some libraries, so both are kept.
    bundle {
        language {
            // The app has its own language choice: keep every language in every install.
            enableSplit = false
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
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    implementation(platform("com.google.firebase:firebase-bom:33.4.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("com.google.firebase:firebase-appcheck-debug")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
