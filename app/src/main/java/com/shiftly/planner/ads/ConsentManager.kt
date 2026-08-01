package com.shiftly.planner.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.shiftly.planner.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Google's User Messaging Platform consent flow.
 *
 * Required by AdMob policy and by EU/UK law before a personalised ad can be requested. The Mobile
 * Ads SDK is deliberately not initialised until consent has been resolved.
 */
object ConsentManager {

    private val adsInitialised = AtomicBoolean(false)

    fun gatherConsentThenInitialise(activity: Activity, onReady: () -> Unit) {
        val params = ConsentRequestParameters.Builder()
            .apply {
                if (BuildConfig.DEBUG) {
                    // Lets us see the European consent form from a non-EEA test device.
                    setConsentDebugSettings(
                        ConsentDebugSettings.Builder(activity)
                            .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                            .build()
                    )
                }
            }
            .setTagForUnderAgeOfConsent(false)
            .build()

        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    // Ignore the form error: without consent we simply fall back to whatever
                    // non-personalised serving the SDK allows, rather than blocking the app.
                    initialiseAdsIfAllowed(activity, consentInformation, onReady)
                }
            },
            {
                // Consent lookup failed (offline, most likely). The app must still work.
                initialiseAdsIfAllowed(activity, consentInformation, onReady)
            },
        )
    }

    private fun initialiseAdsIfAllowed(
        context: Context,
        consentInformation: ConsentInformation,
        onReady: () -> Unit,
    ) {
        if (!consentInformation.canRequestAds()) return
        if (adsInitialised.getAndSet(true)) return

        MobileAds.initialize(context) { onReady() }
    }
}
