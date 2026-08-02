package com.shiftly.planner

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import com.shiftly.planner.ui.CalendarSyncScreen
import com.shiftly.planner.ui.GuideScreen
import com.shiftly.planner.ui.OnboardingScreen
import com.shiftly.planner.ui.ScheduleViewModel
import com.shiftly.planner.ui.SetupScreen
import com.shiftly.planner.ui.ShiftTypesScreen
import com.shiftly.planner.ui.theme.ShiftlyTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ScheduleViewModel by viewModels()

    /** Flipped once consent is resolved and the Mobile Ads SDK is initialised. */
    private var adsReady by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ShiftlyTheme {
                // Consent lookup reads from disk and can show a dialog. Kicking it off from a
                // LaunchedEffect rather than onCreate keeps it off the path to the first frame;
                // nothing on screen depends on ads having resolved.
                LaunchedEffect(Unit) {
                    ConsentManager.gatherConsentThenInitialise(this@MainActivity) {
                        adsReady = true
                    }
                }

                ShiftlyApp(viewModel = viewModel, adsReady = adsReady)
            }
        }
    }
}

/** Which screen is in front. Still no navigation library, but the list is growing. */
private enum class Destination { Calendar, Setup, Guide, Onboarding, ShiftTimes, CalendarSync }

/**
 * The calendar, the rotation setup, the guide, the shift-times editor, calendar sync, and the
 * first-run walkthrough that comes before all of them.
 *
 * The walkthrough wins over everything on a fresh install. Past that, setup is forced open while
 * there is no rotation, because a calendar with no rotation has nothing to show.
 */
@Composable
private fun ShiftlyApp(viewModel: ScheduleViewModel, adsReady: Boolean) {
    val schedule by viewModel.schedule.collectAsStateWithLifecycle()
    val onboardingComplete by viewModel.onboardingComplete
        .collectAsStateWithLifecycle(initialValue = true)
    var editingPattern by remember { mutableStateOf(false) }
    var showingGuide by remember { mutableStateOf(false) }
    var showingShiftTimes by remember { mutableStateOf(false) }
    var showingCalendarSync by remember { mutableStateOf(false) }

    val needsSetup = schedule.pattern == null
    val destination = when {
        !onboardingComplete -> Destination.Onboarding
        showingGuide -> Destination.Guide
        showingShiftTimes -> Destination.ShiftTimes
        showingCalendarSync -> Destination.CalendarSync
        needsSetup || editingPattern -> Destination.Setup
        else -> Destination.Calendar
    }

    // Back leaves the screen you are on rather than the app, matching the arrow in each top bar.
    BackHandler(enabled = showingGuide) { showingGuide = false }
    BackHandler(enabled = showingShiftTimes) { showingShiftTimes = false }
    BackHandler(enabled = showingCalendarSync) { showingCalendarSync = false }

    when (destination) {
        Destination.Onboarding -> OnboardingScreen(
            viewModel = viewModel,
            onFinished = { /* onboardingComplete flips and re-routes on its own. */ },
        )

        Destination.Guide -> GuideScreen(onDone = { showingGuide = false })

        Destination.ShiftTimes -> ShiftTypesScreen(
            viewModel = viewModel,
            onDone = { showingShiftTimes = false },
        )

        Destination.CalendarSync -> CalendarSyncScreen(
            viewModel = viewModel,
            onDone = { showingCalendarSync = false },
        )

        Destination.Setup -> SetupScreen(
            viewModel = viewModel,
            onDone = { editingPattern = false },
            onShowGuide = { showingGuide = true },
            onShowShiftTimes = { showingShiftTimes = true },
            onShowCalendarSync = { showingCalendarSync = true },
        )

        Destination.Calendar -> {
            NotificationPermissionRequest()
            CalendarScreen(
                viewModel = viewModel,
                onEditPattern = { editingPattern = true },
                onShowGuide = { showingGuide = true },
                bannerAd = { BannerAd(adsReady = adsReady) },
            )
        }
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
