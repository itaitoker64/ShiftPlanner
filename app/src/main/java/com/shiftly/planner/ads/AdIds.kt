package com.shiftly.planner.ads

import com.shiftly.planner.BuildConfig

/**
 * Ad unit ids.
 *
 * Debug builds always use Google's public test units. Clicking a live ad on your own device is the
 * fastest way to get an AdMob account permanently banned, so the real ids must never be reachable
 * from a development build.
 */
object AdIds {

    private const val TEST_BANNER = "ca-app-pub-3940256099942544/9214589741"

    /**
     * The live unit comes from the build, not from a constant here: a real id in the source tree
     * is one careless debug build away from a banned account, and one merged PR away from being
     * public. `build.gradle.kts` fails the release build when it is missing.
     *
     * The DEBUG branch is belt and braces — Gradle already gives debug builds the test id — but it
     * means no build configuration can ever put a live unit in front of a developer's thumb.
     */
    val banner: String
        get() = if (BuildConfig.DEBUG) TEST_BANNER else BuildConfig.ADMOB_BANNER_UNIT_ID
}
