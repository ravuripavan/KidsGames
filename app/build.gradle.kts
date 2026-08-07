plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kidsgames.app"
    compileSdk = (findProperty("COMPILE_SDK") as String).toInt()

    defaultConfig {
        applicationId = "com.kidsgames.app"
        minSdk = (findProperty("MIN_SDK") as String).toInt()
        targetSdk = (findProperty("COMPILE_SDK") as String).toInt()
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Personal-use sideload only — no Play Store release, so the
            // debug keystore is signed against directly rather than
            // provisioning a dedicated release key nobody else needs.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":core:gameapi"))
    implementation(project(":core:designkit"))
    implementation(project(":core:shell"))

    // Games. Seven modules cleared review (see docs/superpowers/PARKED.md for the
    // six still parked and talktime, which awaits a confirming review).
    implementation(project(":games:popballoons"))
    implementation(project(":games:patterns"))
    implementation(project(":games:matchshapes"))
    implementation(project(":games:carrace"))
    implementation(project(":games:memorypairs"))
    implementation(project(":games:carwash"))
    implementation(project(":games:tracelines"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
