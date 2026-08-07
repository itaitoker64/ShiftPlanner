# Play Console: answers for every setup task

Every item under **Finish setting up your app**, with the answer for this app and why. Verified
against the code, not guessed. You are the one attesting to these — if something below does not
match what the app does, the code is what counts, not this file.

---

## Set privacy policy

```
https://github.com/itaitoker64/ShiftPlanner/blob/main/PRIVACY.md
```

## Sign-in details (App access)

**All functionality is available without special access.**

There is no account, no login, no gated feature. Nothing for a reviewer to sign into.

## Ads

**Yes, my app contains ads.**

Answer honestly — the app serves an AdMob banner. This puts a "Contains ads" badge on the
listing. Saying no here while shipping the Mobile Ads SDK is a policy violation that gets apps
pulled.

## Content rating

Start the questionnaire. Email address: your own.

| Question | Answer |
|---|---|
| Category | **Utility, Productivity, Communication, or Other** |
| Violence, sexual content, profanity, drugs, gambling, crude humour, horror | **No** to all |
| Does the app share the user's current location with other users? | **No** — the calendar sync writes to a calendar on the device; nothing is shared with anyone |
| Does the app allow users to interact or exchange content? | **No** |
| Does the app allow users to purchase digital goods? | **No** |

Expected result: **Everyone / PEGI 3 / USK 0**.

The ad SDK receives approximate location from IP — that is a *Data safety* disclosure, not a
content-rating one. The content-rating question is about sharing location with other users.

## Target audience

**Age groups: 18 and over only.**

Do not tick any group below 18. Ticking one pulls the app into the Families policy programme,
which forbids the ad configuration this app uses and would require a rewrite of the consent flow.

| Question | Answer |
|---|---|
| Target age groups | **18 and over** |
| Could your app unintentionally appeal to children? | **No** |
| Do you want your app in the Designed for Families programme? | **No** |

The app is for working adults managing a shift rota. Nothing about it is child-directed.

## Government apps

**No** — this is not a government app and is not affiliated with any government body.

## Financial features

**My app doesn't provide any financial features.**

No payments, no lending, no crypto, no banking. It counts hours; it does not handle money.

## Health

**My app doesn't have any health features.**

Shift work has health consequences, but the app makes no health claim, gives no health advice,
and is not a medical device. It draws a calendar.

## Data safety

The one most likely to bounce a submission. The rota itself is never "collected" in Play's
sense — it never leaves the device. Everything disclosed below is the ads SDK.

**Does your app collect or share any of the required user data types? → Yes**

| Data type | Collected | Shared | Purpose | Required? |
|---|---|---|---|---|
| Device or other IDs | **Yes** | **Yes** | Advertising or marketing | Optional |
| Location → Approximate location | **Yes** | **Yes** | Advertising or marketing | Optional |
| App activity → App interactions | **Yes** | **Yes** | Advertising or marketing | Optional |

Everything else — personal info, financial info, health, messages, photos and videos, audio,
contacts, calendar *contents*, files — **not collected**.

> Calendar deserves a note, because the app holds `READ_CALENDAR` and `WRITE_CALENDAR` and a
> reviewer will see them. The app lists the calendars you could write into and writes its own
> shift events; it deletes only events it created, whose ids it recorded. It never reads your
> existing events and never transmits anything. So: permission held, data not collected.

Security practices:

| Question | Answer |
|---|---|
| Is all user data encrypted in transit? | **Yes** — the ad SDK uses HTTPS |
| Do you provide a way for users to request data deletion? | **Yes** — uninstalling removes everything; there is no server copy |
| Has your data collection been independently validated? | **No** |

## Advertising ID

Declare **yes, the app uses advertising ID**, purpose **advertising**. The `AD_ID` permission is
in the manifest and Play cross-checks the two — a mismatch is an automatic rejection.

## App category and contact details

| Field | Value |
|---|---|
| App or game | **App** |
| Category | **Productivity** |
| Email | your own, and one you will actually read — Play shows it publicly |
| Website | `https://github.com/itaitoker64/ShiftPlanner` (optional) |
| Phone | optional, leave blank |

## Store listing

Copy and graphics are in [play-listing.md](play-listing.md). Screenshots still have to come off a
real phone.

---

## Then: the order that actually matters

**Internal testing** is open to you now and does not require the setup tasks. **Closed testing**
is what starts the 14-day clock, and it is locked until setup is done. Use that gap:

1. **Finish the tasks above.** Nothing else unlocks until they are done.
2. **Push a build to Internal testing and put it on your own phone.** This is the first time the
   app runs on a screen, and the first time anyone sees the R8-minified release build rather than
   a debug one — minification bugs exist only there. Take the screenshots here.
3. **Fix what you find.** This is the cheap moment. Testers have not arrived yet.
4. **Then start Closed testing** with 12 testers. The 14-day clock only runs while at least 12
   are opted in, so recruit them before you start rather than after.
5. **Apply for production** once the clock is done.

Do not start the closed test to get the clock going early on a build you have not run. Twelve
people who install a broken app and stop opening it are worse than a two-day delay — Google also
asks how you gathered feedback when you apply.

### The 12 testers are a real task

They must be 12 distinct Google accounts, opted in via your test link, staying opted in for the
full 14 days. Shift workers you know are the ideal testers — they will tell you within a day
whether the rota maths matches their real rota, which is the one thing the unit tests cannot.
