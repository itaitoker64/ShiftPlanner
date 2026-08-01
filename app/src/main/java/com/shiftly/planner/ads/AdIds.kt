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

    // TODO: replace with the real unit id from the AdMob console before the release build.
    private const val LIVE_BANNER = "ca-app-pub-0000000000000000/0000000000"

    val banner: String
        get() = if (BuildConfig.DEBUG) TEST_BANNER else LIVE_BANNER

    /** True while the live ids are still placeholders; guards the release build. */
    val isLiveConfigured: Boolean
        get() = !LIVE_BANNER.startsWith("ca-app-pub-0000")
}
