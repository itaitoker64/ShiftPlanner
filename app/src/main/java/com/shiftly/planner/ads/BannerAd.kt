package com.shiftly.planner.ads

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * An anchored adaptive banner across the bottom of the calendar.
 *
 * Adaptive sizing asks AdMob for a banner matched to the device width, which fills better and pays
 * more than the legacy fixed 320x50 unit. Only one banner exists in the whole app, and no
 * interstitial interrupts the calendar — a rota app is checked in five-second glances, and an ad
 * in front of that is what earns one-star reviews.
 */
@Composable
fun BannerAd(modifier: Modifier = Modifier, adsReady: Boolean) {
    if (!adsReady) return

    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(adaptiveSize(context, widthDp))
                adUnitId = AdIds.banner
                loadAd(AdRequest.Builder().build())
            }
        },
    )
}

private fun adaptiveSize(context: Context, widthDp: Int): AdSize =
    AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
