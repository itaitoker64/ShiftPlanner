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

// The Play upload key, and deliberately not the same key as above. Play re-signs what users
// actually install with a key it holds; this one only proves an upload came from us. Absent
// locally, in which case the release build is left unsigned — enough to verify that it assembles
// and that R8 has not broken anything, not enough to upload.
val uploadKeystore = providers.environmentVariable("SHIFTLY_UPLOAD_KEYSTORE_FILE").orNull

// Google's public test ids. Every debug build serves these, whatever the environment says:
// clicking a live ad on your own device is the fastest way to get an AdMob account banned.
val testAdMobAppId = "ca-app-pub-3940256099942544~3347511713"
val testBannerUnitId = "ca-app-pub-3940256099942544/9214589741"

// The real ids, from the AdMob console. Release-only, and the release build refuses to run
// without them unless -PshiftlyUseTestAds=true says the build is only being verified. Shipping
// Google's sample ids to Play means an app that earns nothing and breaches AdMob's policy.
val liveAdMobAppId = providers.environmentVariable("SHIFTLY_ADMOB_APP_ID").orNull
val liveBannerUnitId = providers.environmentVariable("SHIFTLY_ADMOB_BANNER_UNIT_ID").orNull
val useTestAdsInRelease = providers.gradleProperty("shiftlyUseTestAds").orNull.toBoolean()

android {
    // The Kotlin package, and deliberately not the same as applicationId below. Play only ever
    // sees the applicationId; this one is internal, and renaming it would move every source file
    // to no user-visible end.
    namespace = "com.shiftly.planner"
    compileSdk = 36

    defaultConfig {
        // Identifies the app to Play, permanently: once the console entry exists this can never
        // be changed, and the string can never be reused — not even by us, not even if the app
        // is deleted. com.shiftly.planner was already taken, hence the difference from namespace.
        applicationId = "com.shiftly.rota"
        minSdk = 26
        // Google Play requires new submissions to target API 36 from 31 August 2026.
        targetSdk = 36
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Test ids by default, so any variant that does not override them below is harmless.
        // The release build is the only one that overrides.
        manifestPlaceholders["admobAppId"] = testAdMobAppId
        buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"$testBannerUnitId\"")
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
        if (uploadKeystore != null) {
            create("upload") {
                storeFile = file(uploadKeystore)
                storePassword =
                    providers.environmentVariable("SHIFTLY_UPLOAD_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("SHIFTLY_UPLOAD_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("SHIFTLY_UPLOAD_KEY_PASSWORD").orNull
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
            // Left unsigned when the key is absent. Play rejects an unsigned bundle, so this
            // fails at upload rather than shipping something signed by the wrong key.
            if (uploadKeystore != null) {
                signingConfig = signingConfigs.getByName("upload")
            }
            manifestPlaceholders["admobAppId"] = liveAdMobAppId ?: testAdMobAppId
            buildConfigField(
                "String",
                "ADMOB_BANNER_UNIT_ID",
                "\"${liveBannerUnitId ?: testBannerUnitId}\"",
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

// A release build carrying Google's sample ad ids looks perfectly healthy and earns nothing, and
// the mistake is only visible once the app is live. Catch it here instead.
//
// Hung off preReleaseBuild rather than bundleRelease so it fails in seconds rather than after R8
// has spent five minutes on a bundle nobody can use.
val verifyReleaseAdIds = tasks.register("verifyReleaseAdIds") {
    // Read at configuration time into locals. Referring to the script's own properties from
    // inside doLast captures the script object, which the configuration cache cannot serialize.
    val appId = liveAdMobAppId
    val bannerUnitId = liveBannerUnitId
    val allowTestAds = useTestAdsInRelease

    doLast {
        if (allowTestAds) {
            logger.lifecycle(
                "Release build using Google's TEST ad ids (-PshiftlyUseTestAds). Fine for " +
                    "checking the build and for internal testing; it earns nothing, so never " +
                    "promote it to closed testing or production."
            )
            return@doLast
        }
        val missing = buildList {
            if (appId == null) add("SHIFTLY_ADMOB_APP_ID")
            if (bannerUnitId == null) add("SHIFTLY_ADMOB_BANNER_UNIT_ID")
        }
        if (missing.isNotEmpty()) {
            throw GradleException(
                """
                Release build is missing the real AdMob ids: ${missing.joinToString(", ")}.

                Take them from the AdMob console (app id looks like ca-app-pub-…~…, the banner
                unit id like ca-app-pub-…/…) and set them in the environment.

                To assemble a release build for verification only, pass -PshiftlyUseTestAds=true.
                """.trimIndent()
            )
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyReleaseAdIds)
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
    implementation(libs.androidx.appcompat)

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
