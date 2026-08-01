# Shiftly

An offline Android app for people who work rotating shifts — nurses, factory crews, emergency
services, aviation — and are badly served by ordinary calendar apps.

You tell it your rotation once ("4 on, 4 off", "Panama 2-2-3", DuPont, or a cycle you tap out
yourself), anchor it to a real date, and it fills in every past and future month. A homescreen
widget answers the only question that matters day to day: *am I in today, and when am I next in?*

**Status: pre-release.** Debug and release builds compile, the domain logic is tested, and it has
never been run on a screen. See [Where it stands](#where-it-stands).

---

## Why this exists

Rotating rotas are hard to express in Google Calendar. A "4 on, 4 off" cycle doesn't align to weeks,
so it can't be a weekly repeat; entering it by hand means creating hundreds of events, and one
swapped shift desynchronises everything after it.

The category is also genuinely underserved on Play — unlike the calculator/flashlight/QR-scanner
space, where hundreds of near-identical apps compete for the same keywords.

## How it works

The whole app rests on one idea:

> **Every rotation is a fixed-length cycle of shifts, anchored to a real calendar date.**

That single abstraction covers all ten presets and anything a user builds by hand, which is why
there's one `ShiftPattern` type rather than a class per rotation.

```kotlin
fun shiftTypeIdOn(date: LocalDate): String {
    val offset = date.toEpochDay() - anchorEpochDay
    val index = Math.floorMod(offset, cycle.size.toLong()).toInt()
    return cycle[index]
}
```

Floor-mod rather than `%`, so dates *before* the anchor wrap correctly instead of producing a
negative index — past months resolve as readily as future ones.

On top of that sits a sparse **override** map (`epochDay -> shiftTypeId`). This is what makes the app
survive contact with reality: rotas get swapped and leave gets taken, and a rotation you can't
correct is dead within a fortnight. Overrides take precedence over the pattern, and a day can always
be reset back to it.

### Architecture

```
domain/     Pure Kotlin. No Android imports. Fully unit-tested.
  ShiftType     A kind of shift: name, colour, hours, working or not.
  ShiftPattern  The repeating cycle + anchor date. The engine.
  Schedule      Shift types + pattern + overrides. Resolves any date.
  Presets       Ten real-world rotations.

data/       ScheduleRepository — DataStore, one JSON blob. No backend, no network.
ui/         Compose. ScheduleViewModel exposes StateFlow; screens are stateless.
widget/     Glance homescreen widget, reads the same repository.
reminder/   WorkManager job; notifies the evening before a working day.
ads/        AdMob banner + UMP consent. Test ad units in debug builds.
```

Deliberate constraints, each load-bearing:

- **No backend.** Everything is on-device. No hosting cost, no accounts, no privacy surface beyond
  the ad SDK.
- **No navigation library.** There are two screens. A boolean is enough.
- **`domain/` has no Android dependencies.** That's why it can be tested on the JVM in seconds.

## Getting the app

There is **no Play Store release yet**, so there is nothing to search for and nothing to download
from a store. There are two ways to get it onto a phone.

**Without installing anything.** Every push builds a debug APK in CI. Open the
[Android workflow](../../actions/workflows/android.yml), click the most recent green run, and
download the `shiftly-debug-apk-…` artifact at the bottom of the page. Unzip it, copy the APK to
your phone, and open it — Android will ask you to allow installs from that source. Requires Android
8.0 or newer.

The debug build has applicationId `com.shiftly.planner.debug`, so it installs alongside a future
release build rather than clashing with it, and it serves Google's test ads rather than live ones.

**By building it yourself.** See below.

## Building

Requires Android Studio (or just a JDK 17+) and the Android SDK. The Gradle wrapper handles the
rest.

```bash
./gradlew :app:testDebugUnitTest
```

```bash
./gradlew :app:assembleDebug
```

On Windows, use `gradlew.bat`. If Gradle can't find your SDK, create `local.properties` with
`sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk` — it's gitignored.

> **Do not put this project inside OneDrive, Dropbox, or iCloud Drive.** Sync clients lock files
> mid-build and Gradle fails with `AccessDeniedException` on `build/intermediates`. This cost us an
> hour; learn from it.

## Where it stands

| Area | State |
|---|---|
| Pattern engine, presets, overrides | Done — 21 domain tests |
| Persistence (DataStore) | Done |
| Month calendar, day detail, setup flow | Composes in tests, **never seen on a screen** |
| Homescreen widget (Glance) | Content tested; untested on a real homescreen |
| Shift reminders (WorkManager) | Logic tested; delivery untested on device |
| AdMob banner + UMP consent | Wired into the calendar, untested on device |
| Debug + release builds, CI | Both assemble; every push builds an APK |
| Test suite | 39 JVM tests: 21 domain, 7 screen, 6 widget, 5 reminder |
| App icon | Placeholder vector |
| Play Store listing, privacy policy, signed AAB | Not started |

### Good first contributions

1. **Run it and report what's broken.** Nothing has been on a screen yet. This is the single most
   valuable thing anyone can do right now, and it no longer needs a toolchain — grab the APK from
   [Getting the app](#getting-the-app) and open it.
2. **A real app icon.** The current one is a placeholder vector.
3. **Editable shift types.** `ShiftType` supports custom names, colours and hours, and the data
   model persists them — but there's no UI to create or edit one.
4. **More presets.** Add to `domain/Presets.kt`; the tests assert cycle lengths and working-day
   counts, so follow the existing pattern.

## Contributing

PRs welcome. A few conventions:

- **Domain logic belongs in `domain/`, and comes with tests.** If a change touches shift resolution,
  date maths, or presets, it needs a unit test. That package must stay free of Android imports.
- **Never commit signing material or real ad unit ids.** `.gitignore` covers `*.jks`, `*.keystore`
  and `keystore.properties`. `AdIds.kt` resolves to Google's public test units in debug builds and
  must stay that way — clicking a live ad on your own device is the fastest route to a permanent
  AdMob ban.
- **Match the surrounding style.** Comments explain *why*, not *what*.
- Run `./gradlew :app:testDebugUnitTest` before opening a PR.

### Working on this with Claude Code

See [CLAUDE.md](CLAUDE.md) for project conventions in the form Claude Code reads automatically.

## Licence

Not yet chosen. Until one is added, no permissions are granted beyond viewing the source — if you
want to contribute, open an issue and we'll sort the licence out first.
