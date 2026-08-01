package com.shiftly.planner

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiftly.planner.ads.BannerAd
import com.shiftly.planner.ads.ConsentManager
import com.shiftly.planner.ui.CalendarScreen
import com.shiftly.planner.ui.ScheduleViewModel
import com.shiftly.planner.ui.SetupScreen
import com.shiftly.planner.ui.theme.ShiftlyTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ScheduleViewModel by viewModels()

    /** Flipped once consent is resolved and the Mobile Ads SDK is initialised. */
    private var adsReady by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        ConsentManager.gatherConsentThenInitialise(this) { adsReady = true }

        setContent {
            ShiftlyTheme {
                ShiftlyApp(viewModel = viewModel, adsReady = adsReady)
            }
        }
    }
}

/**
 * Two screens, no navigation library: the calendar, and the rotation setup.
 *
 * Setup is forced open on a fresh install because a calendar with no rotation has nothing to show.
 */
@Composable
private fun ShiftlyApp(viewModel: ScheduleViewModel, adsReady: Boolean) {
    val schedule by viewModel.schedule.collectAsStateWithLifecycle()
    var editingPattern by remember { mutableStateOf(false) }

    val needsSetup = schedule.pattern == null

    if (needsSetup || editingPattern) {
        SetupScreen(viewModel = viewModel, onDone = { editingPattern = false })
    } else {
        NotificationPermissionRequest()
        CalendarScreen(
            viewModel = viewModel,
            onEditPattern = { editingPattern = true },
            bannerAd = { BannerAd(adsReady = adsReady) },
        )
    }
}

/**
 * Asks for notification permission only once the user has a rotation set up.
 *
 * Prompting on first launch, before the app has shown any value, is the reliable way to get denied.
 */
@Composable
private fun NotificationPermissionRequest() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    var requested by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Reminders are optional; a denial changes nothing else. */ }

    LaunchedEffect(Unit) {
        if (!requested) {
            requested = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
