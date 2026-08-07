# Releasing to Google Play

What the repository does for you, what you have to do by hand, and the order to do it in.

The build side is finished: `./gradlew :app:bundleRelease` produces a signed Android App Bundle
that Play accepts. What is left is a Play Console account, an AdMob account, and the store
listing — none of which can live in a git repository.

---

## 1. Create the upload key

Play signs what users install with a key it holds. The key below is the *upload* key: it only
proves an upload came from you. Losing it is recoverable (Google can reset it); leaking it is not
something you want to test.

This must be a **different key** from the `SIGNING_*` one `release.yml` uses for the pre-release
APKs in Releases. Those two channels are unrelated and should not share a key.

```bash
keytool -genkeypair -v -keystore upload.jks -alias shiftly-upload \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 upload.jks   # macOS: base64 -i upload.jks
```

Keep `upload.jks` and both passwords somewhere durable — a password manager, not the repo.
`.gitignore` already covers `*.jks`, and it must stay that way.

## 2. Get the AdMob ids

In the [AdMob console](https://apps.admob.com): create the app, then create one **banner** ad
unit for it. You need two values:

| Value | Shape | Where it goes |
|---|---|---|
| App id | `ca-app-pub-…~…` (tilde) | `SHIFTLY_ADMOB_APP_ID` |
| Banner unit id | `ca-app-pub-…/…` (slash) | `SHIFTLY_ADMOB_BANNER_UNIT_ID` |

Neither is a secret — both ship inside the bundle and anyone can unzip it out. They are kept out
of the source tree for a different reason: a live unit id that a debug build can reach is one
careless thumb away from a permanently banned AdMob account. The release build refuses to run
without them, and debug builds ignore them even when they are set.

## 3. Configure the repository

**Settings → Secrets and variables → Actions.**

Secrets:

| Secret | Value |
|---|---|
| `PLAY_UPLOAD_KEYSTORE_BASE64` | the base64 output from step 1 |
| `PLAY_UPLOAD_KEYSTORE_PASSWORD` | the store password you chose |
| `PLAY_UPLOAD_KEY_ALIAS` | `shiftly-upload` |
| `PLAY_UPLOAD_KEY_PASSWORD` | the key password you chose |

Variables (the **Variables** tab, not Secrets):

| Variable | Value |
|---|---|
| `ADMOB_APP_ID` | the app id from step 2 |
| `ADMOB_BANNER_UNIT_ID` | the banner unit id from step 2 |

## 4. Build the bundle

Run the **Play bundle** workflow (Actions → Play bundle → Run workflow). It asks for a
`versionCode`.

> **versionCode must be higher than every code you have already uploaded to Play**, including
> ones you rolled back or abandoned in draft. Play rejects a repeat permanently. Start at `1`
> and increase by one each time; the number is unrelated to the run numbers in `release.yml`,
> which feed the separate pre-release APK channel.

The workflow runs the tests, builds, checks the bundle really is signed, and attaches two
artifacts:

- `shiftly-play-<code>` — the `.aab` you upload
- `shiftly-play-<code>-mapping` — the R8 `mapping.txt`

Upload the mapping file in the Play Console alongside the bundle (**App bundle explorer →
Downloads → upload deobfuscation file**). Without it, every crash report from the field arrives
as unreadable obfuscated stack traces.

To build one locally instead:

```bash
SHIFTLY_VERSION_CODE=1 \
SHIFTLY_UPLOAD_KEYSTORE_FILE=/path/to/upload.jks \
SHIFTLY_UPLOAD_KEYSTORE_PASSWORD=… \
SHIFTLY_UPLOAD_KEY_ALIAS=shiftly-upload \
SHIFTLY_UPLOAD_KEY_PASSWORD=… \
SHIFTLY_ADMOB_APP_ID=ca-app-pub-…~… \
SHIFTLY_ADMOB_BANNER_UNIT_ID=ca-app-pub-…/… \
./gradlew :app:bundleRelease
```

### Before AdMob exists

The `ad_ids` input takes `test`, which builds with Google's sample ad ids and needs no AdMob
configuration — only the signing secrets. That is the build to put on the **internal testing**
track to get the app onto a phone and take screenshots, well before there is anything to monetise.

Its artifacts are named `…-TEST-ADS-DO-NOT-PROMOTE`, because the artifact name is the only thing
anyone reads before dragging a file into Play. Internal testing is where it stops: promoted to
closed testing or production it would serve sample ads forever and earn nothing.

Locally the same build is `./gradlew :app:bundleRelease -PshiftlyUseTestAds=true`, which needs no
configuration at all — unsigned, and enough to check that R8 has not broken a keep rule.

## 5. Publish the privacy policy

Play requires a **public URL**, not a file. [PRIVACY.md](../PRIVACY.md) is written and ready;
it just needs somewhere to live. The cheapest option is this repository:

```
https://github.com/itaitoker64/ShiftPlanner/blob/main/PRIVACY.md
```

That is a real, permanent, publicly readable URL and Play accepts it. GitHub Pages gives you a
tidier one if you would rather.

Check the contact address in the policy is one you will actually read — Play shows it to users.

## 6. The Play Console

A one-off $25 registration fee, and identity verification that can take a few days. Start it
before you need it.

### Package name

**`com.shiftly.rota`** — the `applicationId` in `app/build.gradle.kts`.

It does not match the `namespace` above it (`com.shiftly.planner`), and that is deliberate rather
than an oversight to tidy up. `com.shiftly.planner` was already taken on Play. Play only ever sees
the applicationId; the namespace is the Kotlin package, and renaming it would move every source
file for no user-visible gain.

Once an app exists in the console under a package name, that name is fixed forever, and Play never
releases it again — not to anyone else, and not back to you if you delete the app. Deleting a draft
you never published burns it just the same. Be sure before you create the console entry.

### Data Safety form

This is the part most likely to get a submission rejected, so here are the answers for this app
as it is actually built. Verify them against the code rather than trusting this table blindly.

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** — via the ads SDK only |
| Device or other IDs | **Collected and shared.** Purpose: *Advertising or marketing*. Not required to use the app. Not processed ephemerally. |
| Approximate location | **Collected and shared** by Google's ad SDK, derived from IP. Purpose: *Advertising or marketing*. Optional. |
| App interactions | **Collected and shared** — ad impressions and clicks. Purpose: *Advertising or marketing*. Optional. |
| Personal info, financial info, health, messages, photos, contacts, files, calendar *contents* | **Not collected.** Calendar access is used to write events on the device; nothing is read out or transmitted. |
| Is data encrypted in transit? | **Yes** — the ad SDK uses HTTPS |
| Can users request data deletion? | **Yes** — uninstalling removes everything; there is no server copy |

The rota itself is never "collected" in Play's sense: it never leaves the device.

### Advertising ID declaration

Answer **yes** — the app uses advertising ID, for advertising. The `AD_ID` permission is already
declared in the manifest, which Play cross-checks against this answer.

### Content rating

Fill in the questionnaire honestly. A shift-rota app with a banner ad rates **Everyone / PEGI 3**
in every region. Declare that the app contains ads — Play shows an "Contains ads" badge, and
failing to declare it is a policy violation.

### Target audience

Not directed at children. Do **not** opt into the Designed for Families programme; it forbids
the ad setup this app uses.

## 7. What still is not done

None of this is a code problem, and none of it can be automated from here.

| Needed | State |
|---|---|
| App icon | **Placeholder vector.** Ships as-is without Play objecting, but it is the first thing anyone sees. |
| 512×512 store icon (PNG) | Not made |
| Feature graphic, 1024×500 | Not made |
| Phone screenshots (2–8, min 320px) | **Not possible yet** — the app has never been run on a screen |
| Short description (80 chars) | Not written |
| Full description (4000 chars) | Not written — the README's "Why this exists" is the raw material |
| Actually running the app | **Nothing here has been on a device or an emulator.** |

That last row is the real blocker, and it is worth being blunt about it: the test suite proves
the screens compose and the date maths is right. It does not prove the app is usable, that the
widget looks correct on a homescreen, that reminders arrive, or that the banner appears where it
should. Install the debug APK on a phone and use it for a week before shipping it to strangers.

## Verified about the current build

Checked against a real bundle built from this tree, rather than assumed:

- The release bundle assembles with R8 minification and resource shrinking, and `lintVital`
  passes. No keep rules are missing for kotlinx.serialization, Glance or WorkManager.
- The signed bundle verifies (`jarsigner -verify` → `jar verified`).
- `targetSdk` is 36, which is above Play's API 36 floor for new submissions from 31 August 2026.
- The two bundled native libraries (`libdatastore_shared_counter.so`,
  `libandroidx.graphics.path.so`) are 16 KB page aligned, which Play requires of new apps.
  Confirmed with `readelf -lW`: `LOAD` segments align at `0x4000`.
- The release manifest carries the injected AdMob app id; the debug manifest carries Google's
  test id even when the live ids are present in the environment.
