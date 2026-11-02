# Preferences — 39 remaining desktop entries

Android Phase 2, 2026-11-02. This is a consumer inventory, not a claim that all 39 desktop behaviors are complete. No JNI or packaged slicer library has been changed. Phases 3–7 remain gated.

All 39 entries remain visible in their desktop sections and order. A missing capability is an explanatory row, never an enabled-looking stored switch. Extra mobile controls such as sensitivity, delay and explicit backup/sync actions are outside this count.

| # | Entry | Android consumer / remaining boundary |
|---|---|---|
| 1 | General: Language | Android application locale, EN/RU. New preference strings localized; legacy hardcoded screens are not fully translated. |
| 2 | Start page | LaunchActivity routes to Workspace or existing recent-file page. |
| 3 | Single instance | Android enforces a single shared workspace/task. Multiple concurrent workspaces are unsupported; no ineffective off switch. |
| 4 | Splash | Controls branded in-app startup screen. Mandatory Android system splash is unaffected. |
| 5 | Downloads folder | Persisted SAF directory grant seeds G-code export document picker. A user still chooses the destination through Android. |
| 6 | Project load behavior | Conservative 3MF decision policy; geometry import is explicit and original project is preserved. Full native config application is blocked. |
| 7 | Add STL/STEP to recent | Actual STL recent-list insertion gate. STEP import remains absent. |
| 8 | Modified G-code warning | Existing project metadata is inspected before geometry preview; warning can be suppressed. This does not bypass native slicing compatibility guards. |
| 9 | STEP mesh controls | Blocked: no Android OCCT importer or tessellation entry point. |
| 10 | Auto backup | Foreground-only scheduled streaming model/plate/placement backup, manual backup/restore, checksums and atomic publication. It is not yet a complete multi-object project/config backup. |
| 11 | Group filament presets | User presets grouped together / ungrouped / by effective type / by vendor in the virtualized preset picker. System presets remain separate descriptors. |
| 12 | Control: auto flushing | Blocked: no multi-filament snapshot or flushing-matrix calculation bridge. |
| 13 | Arrange after cloning | Blocked: no multi-instance clone/Arrange bridge. Import/centering is not substituted. |
| 14 | Auto slicing | Debounced saved-change hooks; foreground handoff, generation invalidation, fingerprint freshness and worker snapshots. Uses the existing slicing screen; it does not send to a printer. |
| 15 | Mixed temperatures | Blocked: single-filament bridge has no original compatibility-check hook. |
| 16 | Camera style | Actual default/touchpad pointer handling, separate from phone gesture swap. |
| 17 | Zoom to gesture center | Projection-based gesture focus in StlView with bed-plane fallback. |
| 18 | Free camera | Removes constrained orbit pitch; no change to model geometry. |
| 19 | Swap camera gestures | Actual one/two-finger pan/rotate mapping. |
| 20 | Clear unsaved project choice | Removes remembered replace / backup-and-replace answer only. |
| 21 | Clear unsaved preset choice | Removes remembered Save / Discard answer only; successful saves and canceled Save As are distinguished. |
| 22 | Clear printer sync choice | Blocked: original printer-sync prompt is not connected. Cloud state is never cleared as a substitute. |
| 23 | Online: region | Blocked: original Bambu network agent is absent. Global Orca Cloud is not falsely assigned regional routes. |
| 24 | Stealth | Dynamic cloud/public HTTP gate, permits validated LAN targets, cancels active HTTP work on policy changes. |
| 25 | Network test | Selected printer protocol test; explicit official HTTPS reachability test when no printer is selected. |
| 26 | Stable updates only | Real stable/prerelease selection for official Orca release checks. No automatic desktop binary installation. |
| 27 | Automatic profile sync | Explicit opt-in, account-scoped pull/merge/push through the actual user preset repository; conflict handling and tombstones. Real cloud account verification remains outstanding. |
| 28 | Built-in profile updates | Blocked: compatible profile-pack validation/catalog rebuild/rollback is absent. Release checking is separate. |
| 29 | Filament sync mode | Blocked: Bambu/AMS device state and mappings unavailable. |
| 30 | Enable network plug-in | Blocked: no compatible Android Bambu provider. Java HTTP is not that plug-in. |
| 31 | Network plug-in version | Blocked: no provider distributions/ABI lifecycle. |
| 32 | Associate: STL | Android VIEW/SEND route and format-validated file-picker test. Android owns default-handler selection. |
| 33 | Associate: 3MF | Same Android association route for 3MF, preserving native import guards. |
| 34 | Associate: OBJ | Same Android association route for OBJ. |
| 35 | Developer mode | Reveals application diagnostics/gallery and filters schema entries explicitly marked comDevelop. No claim of additional native debug APIs. |
| 36 | Skip AMS check | Blocked: Bambu provider and actual AMS compatibility path absent. |
| 37 | Allow abnormal storage | Blocked: no provider storage-state report/upload gate. |
| 38 | Log level | Real structured Java event severity filtering; native Boost logger severity is not changed. |
| 39 | Reload plug-in | Blocked: Bambu provider lifecycle absent. |

There are **13 entirely blocked entries** (#9, 12, 13, 15, 22, 23, 28–31, 36, 37, 39), plus the explicit partial/platform adaptations above. “26 other entries have a consumer/adaptation” must not be restated as “26 exact desktop features complete”.

Engine proposal and acceptance checks: [PREFERENCES-NATIVE-PLAN.md](PREFERENCES-NATIVE-PLAN.md). Network/provider boundary: [PREFERENCES-ONLINE.md](PREFERENCES-ONLINE.md).

## Verification boundaries

Host tests exercise real Java policy, backup and repository code with explicit Android/native stubs or supplied cloud snapshots. They do not prove JNI import/slice behavior, actual cloud authentication, physical printer operation or desktop parity. Computer-use results and exact APK hashes are recorded with the release evidence after the final build.

The global preset and object override stores do not share a cross-store transaction. Both candidates are validated before writing; a post-commit failure keeps the object draft, advances the committed preset baseline and stops navigation. Retry can complete the remaining operation.
