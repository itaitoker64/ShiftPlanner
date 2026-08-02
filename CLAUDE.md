# CLAUDE.md

Project context for Claude Code. See [README.md](README.md) for what the app is and why.

## The one idea

Every rotation is a **fixed-length cycle of shift-type ids anchored to a real date**, resolved with
floor-mod so dates before the anchor wrap correctly. On top sits a sparse override map
(`epochDay -> shiftTypeId`) that always wins over the pattern.

Before adding a new concept, check whether it's expressible as a cycle plus overrides. It usually
is. Resist adding a second pattern type.

## Layout

```
domain/     Pure Kotlin, no Android imports, fully unit-tested
data/       ScheduleRepository — DataStore, single JSON blob
ui/         Compose; ScheduleViewModel exposes StateFlow, screens are stateless
widget/     Glance widget, reads the same repository
reminder/   WorkManager, notifies the evening before a working day
ads/        AdMob banner + UMP consent
```

## Rules

- **`domain/` must not import Android.** It's testable on the JVM in seconds precisely because of
  this. Anything needing a `Context` belongs in `data/` or above.
- **Domain changes require tests.** `app/src/test/java/com/shiftly/planner/domain/ScheduleTest.kt`
  covers cycle wrapping, negative dates, override precedence and month totals. Extend it.
- **Never hardcode a live ad unit id.** `AdIds` switches on `BuildConfig.DEBUG` and must keep
  resolving to Google's test units in debug builds.
- **Never commit keystores.** Losing or leaking the upload key means the app can never be updated
  again.
- **`targetSdk` must stay at 36 or higher.** Google Play rejects new submissions below API 36 from
  31 August 2026.
- **Mutations go through `ScheduleViewModel.mutate`**, which persists *and* refreshes the widget.
  Writing to the repository directly leaves a stale homescreen.

## Verifying

```bash
./gradlew :app:testDebugUnitTest
```

```bash
./gradlew :app:assembleDebug
```

Tests run on the JVM in four groups: `domain/` (pure logic), `ui/ScreenRenderTest` (Robolectric
composes the screens), `widget/ShiftWidgetTest` (Glance's own unit-test harness, since Glance
composes to RemoteViews rather than to a View tree), and `reminder/ReminderWorkerTest`.

Anything that renders takes the date as a parameter defaulting to `LocalDate.now()` — see
`WidgetContent`. Calling `now()` inside the body makes the interesting states untestable.

All of this catches crashes and wrong text. It is **not** the same as working: nothing here has been
run on a device or emulator, and no one has looked at a single pixel of it. Do not describe any of
it as working.

Robolectric tests run on the stock `Application`, not `ShiftlyApplication` — see the comment at the
top of `ScreenRenderTest` for why.

## Environment gotchas

- **Never place this project in a synced folder** (OneDrive, Dropbox, iCloud). Sync clients lock
  files mid-build; Gradle dies with `AccessDeniedException` on `build/intermediates`.
- **Tests need a Java 21+ JVM.** Robolectric will not build a sandbox for SDK 36 on Java 17 —
  `Android SDK 36 requires Java 21 (have Java 17)`, thrown before any test runs. The app still
  targets Java 17 bytecode; this is only the JVM Gradle runs on. CI pins 21 for the same reason.
- Windows: set `JAVA_HOME` to Android Studio's bundled JDK, e.g.
  `C:\Program Files\Android\Android Studio\jbr`.
- `local.properties` (SDK path) is gitignored and must be created locally.

## Wording

Users are shift workers, not developers. "Rotation", "cycle", "shift", "days off" — never "pattern
anchor" or "epoch day" in anything user-facing.
