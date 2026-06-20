plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics.gradle)
}

android {
    // namespace = internal code package (where R/BuildConfig generate). Kept as-is
    // intentionally — changing it would force a 56-file package refactor with zero
    // Play Store / functional benefit. Only applicationId matters for the listing.
    namespace = "com.randomchat.shnapp"
    compileSdk = 35

    defaultConfig {
        // Play Store identity — MUST match the originally-published app to recover
        // the existing listing + 100k installs.
        applicationId = "com.shaheen.randomchat"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.0.1234"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.database)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.appcheck.debug)
    implementation(libs.firebase.crashlytics)

    implementation(libs.play.services.ads)
    implementation(libs.billing.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.mlkit.text.recognition)

    debugImplementation(libs.androidx.ui.tooling)
}
