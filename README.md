# No Doomscroll — a guarded Instagram client for Android (WebView-based)

Personal-use Android app that logs into **instagram.com in a hardened WebView**
(no Meta developer credentials needed — you sign in on Instagram's own page and
the session lives in the WebView cookie jar). Everything that makes Instagram a
doomscroll machine is **hard-disabled and enforced**:

| Requirement | How it is enforced |
|---|---|
| Vertical swipe/paging disabled entirely | `NoScrollWebView.onTouchEvent` consumes every vertical gesture (layer 1); `GuardJs` sets `overflow:hidden`, `overscroll-behavior:none` and `preventDefault()`s wheel/touchmove (layer 2). Taps still work; scrolling never does. |
| Reels tab unreachable | The bottom "Reels 🔒" tab renders a dead-end screen and never loads `/reels`. Inside the page, Reels/Explore links are rewritten to `/direct`, their labels hidden, and any SPA navigation (`pushState`) into `/reels` or `/explore` is bounced back. Server-side navigations are cancelled by `shouldOverrideUrlLoading` / `shouldInterceptRequest`. |
| Watch a reel opened from a DM thread, but no auto-advance | Only `/direct/… → /reel/<id>/` opens count against the daily budget; the video plays inline with native controls, `autoplay`/`loop` stripped, and an `ended` listener resets & pauses — there is no "next". One viewer at a time (`activeViewer`). |
| Kill autoplay | `mediaPlaybackRequiresUserGesture = true` at the engine level + DOM stripping of `autoplay` + programmatic `play()` is paused unless preceded by a real tap. |
| Kill infinite feed refresh | XHR/fetch requests containing `max_id=` (feed pagination) are refused inside the page; `policy.shouldLoadMoreFeed()` / `shouldAutoRefreshFeed()` are constants `false`. |
| Login → Inbox → Home feed only | Three surfaces exist: Home feed (single frozen page), Inbox list, DM thread (+ one inline reel). Nothing else is mountable. |
| Push new DMs | `DmNotificationListenerService` mirrors Instagram's own DM notifications (user grants Notification Access once) and taps land in the guarded inbox. No API keys required. |
| Enforced | Persisted daily reel budget + cooldown lock (`PrefsBudgetStore`), inescapable `LockdownActivity` when limits/blocked surfaces are hit, `FLAG_SECURE` on the lock screen. |
| Android 15+ | `targetSdk 35`, minSdk 26. |

## Why injected JS?
Instagram web is a React SPA: tapping Reels does not fire a page load, so
native URL callbacks alone miss it. `GuardJs` runs at **document-start**
(`androidx.webkit` `DOCUMENT_START_SCRIPT`, with a page-finish fallback) so it
wraps `history.pushState`, `fetch`, and `XMLHttpRequest` before Instagram's own
code executes.

## Layout
```
policy/   pure-Kotlin rules (unit-tested, no Android deps except PrefsBudgetStore)
guard/    NoScrollWebView, GuardJs, GuardManager, LockdownActivity
notify/   DM notification relay
data/     encrypted session flag
ui/       MainActivity (Home / Inbox / locked Reels tab)
```

## Tests
`./gradlew test` — `DoomscrollPolicyTest` covers forbidden surfaces, DM-only
reel origin, no-autoplay/no-auto-advance decisions, single-viewer rule, daily
limit + cooldown, and store round-trip. (Verified passing with kotlinc 2.0.21 +
JUnit 4.13.2.)

## Build
Android Studio (Hedgehog+) or `./gradlew assembleDebug`. Requires Android SDK
platform 35. Open app → sign in on instagram.com → you land in the Inbox.
Grant Notification Access from the in-app prompt to get DM push.

## Honest limitations
* A determined user can defeat this from outside the app (it's a personal
  guardrail, not enterprise MDM). True blocking of the *official* Instagram app
  would require Device Policy Owner (adb `dpm set-device-owner`) — the hook is
  stubbed in `LaunchBlockerReceiver`.
* Instagram changes its DOM regularly; selectors in `GuardJs` may need updates,
  but layers 1 & 3 (touch interception + URL blocking) keep working regardless.

## Download / Install

**Prebuilt APK (fastest):** `NoDoomscroll-debug.apk` in the repo root (~7.6 MB).
AirDrop/email it to the phone, tap it, allow "Install unknown apps", or sideload:

```bash
adb install -r NoDoomscroll-debug.apk
```

**Full source project:** `NoDoomscroll-project.zip` (33 files, includes Gradle wrapper 8.10.2 + all Kotlin sources).
Unzip, then open the folder in Android Studio (Iguana+ / targetSdk 35):

```bash
unzip NoDoomscroll-project.zip -d NoDoomscroll && cd NoDoomscroll && ./gradlew assembleDebug
# APK lands at app/build/outputs/apk/debug/app-debug.apk
```

Note: `local.properties` points at this container's SDK (`/opt/android-sdk`). On your machine delete it and let Android Studio regenerate it, or set `sdk.dir` to your own SDK path.

**Post-install setup (enforcement is opt-in by OS design):**
1. Open the app -> log into instagram.com in the WebView.
2. Settings -> Apps -> Special app access -> **Notification access** -> enable *No Doomscroll* (required for DM push alerts).
