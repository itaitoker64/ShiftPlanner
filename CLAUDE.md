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

Unit tests cover the domain only. The UI, widget and reminders have **never been run on a device or
emulator** — do not describe them as working. Anything outside `domain/` is unverified until someone
has actually looked at it on a screen.

## Environment gotchas

- **Never place this project in a synced folder** (OneDrive, Dropbox, iCloud). Sync clients lock
  files mid-build; Gradle dies with `AccessDeniedException` on `build/intermediates`.
- Windows: set `JAVA_HOME` to Android Studio's bundled JDK, e.g.
  `C:\Program Files\Android\Android Studio\jbr`.
- `local.properties` (SDK path) is gitignored and must be created locally.

## Wording

Users are shift workers, not developers. "Rotation", "cycle", "shift", "days off" — never "pattern
anchor" or "epoch day" in anything user-facing.
