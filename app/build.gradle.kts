import java.time.Duration

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Set by CI so that every published build outnumbers the last one. Android will not install an
// APK whose versionCode is not greater than the installed one, so a fixed value means the phone
// silently refuses every update after the first.
val buildNumber = providers.environmentVariable("SHIFTLY_VERSION_CODE").orNull?.toInt() ?: 1

// A stable key for the builds published to the phone. Absent locally, in which case Gradle falls
// back to the throwaway debug key it generates per machine — fine for running from Studio, but no
// use for updating an existing install, since a different key reads as a different app.
val distributionKeystore = providers.environmentVariable("SHIFTLY_KEYSTORE_FILE").orNull

android {
    namespace = "com.shiftly.planner"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.shiftly.planner"
        minSdk = 26
        // Google Play requires new submissions to target API 36 from 31 August 2026.
        targetSdk = 36
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (distributionKeystore != null) {
            create("distribution") {
                storeFile = file(distributionKeystore)
                storePassword = providers.environmentVariable("SHIFTLY_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("SHIFTLY_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("SHIFTLY_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            if (distributionKeystore != null) {
                signingConfig = signingConfigs.getByName("distribution")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        // Robolectric renders the real layouts, so it needs the merged resources and manifest.
        unitTests.isIncludeAndroidResources = true
    }
}

// A Compose test that never reaches idle hangs rather than fails, and CI will happily sit on it
// until the runner's own six-hour limit. Failing after ten minutes turns that into a build
// failure, and logging each test as it starts means the last line names whichever one stuck.
// Four minutes: the whole suite runs in well under one.
//
// Deliberately outside the android block: in there `java` resolves to Gradle's java extension
// rather than the package, so java.time.Duration will not compile.
tasks.withType<Test>().configureEach {
    timeout.set(Duration.ofMinutes(4))
    testLogging {
        events("started", "passed", "failed", "skipped")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    // Homescreen widget
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // Shift reminders
    implementation(libs.androidx.work.runtime.ktx)

    // Monetisation. Test ad unit ids are used until the release build.
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)

    testImplementation(libs.junit)
    // Compose on the JVM. The screens have never run on a device, so composing them in a plain
    // unit test is the only automated check that they render at all.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.androidx.glance.appwidget.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
