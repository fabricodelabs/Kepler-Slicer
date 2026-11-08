# Splash Screen and Branding Audit

## What exists
The request mentions a "new animated splash screen mode". **No animated splash exists in this repository**: all
local and remote branches were searched (`SplashScreen`, `AnimatedVectorDrawable`, Lottie, `windowSplashScreen*`,
"splash"/"заставк" in commit messages) without a match. What ships:

1. The Android 12+ system splash (automatic, app icon on the theme background; no custom attributes).
2. `LaunchActivity` (`app/.../LaunchActivity.java`, 23 lines): when Preferences → General → "Show splash screen" is on,
   it shows a static view ("Kepler Slicer" + "Starting…" on the glass background), waits for its first frame
   (`OnPreDrawListener` → `postOnAnimation`) and starts `MainActivity`; otherwise it routes immediately.
3. Icon: adaptive launcher icon `@drawable/ic_launcher` (vector foreground `ic_launcher_foreground.xml`).

The animated-splash part of the audit could therefore not be performed. If such a mode exists on another machine or
an unpushed branch, it is unaudited.

## Security
| Check | Result |
|---|---|
| External/remote resources | none: text, local gradient drawable, vector icon |
| SVG / animation files, dynamic code from resources | none |
| Extra permissions | none |
| Activity/Context leaks | none: no static references; `launch()` checks `isFinishing()`; the activity finishes after `startActivity` |
| Graphics resources | gradient drawable only; released with the activity |
| Intent handling | only `content://` URIs are forwarded (SEC-01), with `FLAG_GRANT_READ_URI_PERMISSION` |

## Stability (emulator, Pixel_Tablet API 36)
| Scenario | Result |
|---|---|
| Cold start (3 runs) | `TotalTime` 589 / 390 / 402 ms to the first frame, workspace shown |
| Hot start (from home) | `LaunchState: HOT`, 288 ms |
| Return from background, reopen | tested (hot start above): workspace restored |
| Recreation / orientation | code review only: tablet landscape-locked, mobile portrait-locked; `LaunchActivity` keeps no state |
| Interrupted start (back during splash) | code review only: `isFinishing()` stops the forward |
| Loops, hangs | code review: one pre-draw callback, removed on first call |
| Process death, low memory | not tested on device; code review: restart goes through the same route, model restored by `MainActivity` |

## Performance
- Main thread work before the first frame: `GeneralPreferences.showSplash()` reads SharedPreferences (one disk read
  on first access; small). No artificial delays.
- Lint `CustomSplashScreen` (warning): on Android 12+ the custom screen shows after the system splash, so users can
  see two splashes. Recommendation (not applied — product decision): drop the custom view and theme the system
  splash (`windowSplashScreenBackground`, `windowSplashScreenAnimatedIcon`, `postSplashScreenTheme`); keep the
  "Show splash screen" preference as a no-op or hold the system splash briefly with `setKeepOnScreenCondition`.

## Branding
- App label "Kepler Slicer" hard-coded in the manifest and `LaunchActivity` (not translated — intended brand name).
- No OrcaSlicer branding in launcher assets.

## Issues
| ID | Severity | Issue | Status |
|---|---|---|---|
| SPL-01 | Low | Double splash on Android 12+ (system + custom) | Not fixed — recommendation above |
| SPL-02 | Info | Requested animated splash not present in the codebase | Requires clarification |
