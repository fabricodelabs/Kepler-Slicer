# Preferences — 39 remaining desktop entries

Android Phase 2, 2026-11-02, after the owner approved the native Preferences plan. **The 52 real native self-test assertions passed on installed APK 9FD3374C… / native 8C146C74… through computer use.** This is the current implementation/consumer inventory, not a claim that all 39 desktop behaviors or Phase 2 are complete. Earlier unchanged-library and 13-blocker reports describe the pre-native baseline. Phases 3–7 remain gated.

All 39 entries remain visible in their desktop sections and order. A missing capability is an explanatory row, never an enabled-looking stored switch. Extra mobile controls such as sensitivity, delay and explicit backup/sync actions are outside this count.

| # | Entry | Android consumer / remaining boundary |
|---|---|---|
| 1 | General: Language | Android application locale, EN/RU. New preference strings localized; legacy hardcoded screens are not fully translated. |
| 2 | Start page | LaunchActivity routes to Workspace or existing recent-file page. |
| 3 | Single instance | Android enforces a single shared workspace/task. Multiple concurrent workspaces are unsupported; no ineffective off switch. |
| 4 | Splash | Controls branded in-app startup screen. Mandatory Android system splash is unaffected. |
| 5 | Downloads folder | Persisted SAF directory grant seeds G-code export document picker. A user still chooses the destination through Android. |
| 6 | Project load behavior | Real worker-side typed native 3MF snapshot and explicit load/ask/geometry decision. Configured single-plate projects can use the selected printer or embedded printer, subject to native validation; original archive and native object/volume/layer/material scopes are preserved. The current Settings view is read-only for project values. Multi-plate/nonzero plate-origin projects and unsupported configurations fail explicitly; there is no geometry fallback after full-load failure. |
| 7 | Add STL/STEP to recent | Actual STL/STEP recent-list insertion gate, including STEP/STP import. This is not an Android file association setting for STEP. |
| 8 | Modified G-code warning | Existing project metadata is inspected before geometry preview; warning can be suppressed. This does not bypass native slicing compatibility guards. |
| 9 | STEP mesh controls | Real Android OCCT tessellation with linear/angular tolerance and split-volume options, an import options dialog, retained original source and a checked derived-preview cache. Historical native DA652B… completed a STEP slice at 0.003 mm / 0.5 rad (2,371,725 G-code bytes) after placement correction. Repeating this UI scenario on final APK 9FD3374C… is pending; two-tolerance quality comparison and desktop parity remain acceptance work. |
| 10 | Auto backup | Foreground-only scheduled streaming original-model/plate/placement backup, including project mode and material-edit overlays, manual backup/restore, checksums and atomic publication. It is not yet a complete editable multi-object workspace backup. |
| 11 | Group filament presets | User presets grouped together / ungrouped / by effective type / by vendor in the virtualized preset picker. System presets remain separate descriptors. |
| 12 | Control: auto flushing | Real native multi-material snapshot, original flushing calculator/datasets and a configured-3MF physical-slot colour editor. A committed colour change recalculates affected rows/columns when enabled, preserving unrelated manual matrix values; virtual mixed slots remain read-only. The original archive is not rewritten. General filament assignment/device sync and desktop comparison remain incomplete. |
| 13 | Arrange after cloning | Real native instance cloning and upstream Arrange, bed/exclusion/height checks, frozen publication and a Model Details action for a single plain STL/OBJ object. Historical APK 1E2F… passed an actual OBJ clone + Arrange action with two added copies: 36 preview triangles and 42 × 42 × 20 mm bounds. Repeating this UI scenario on final APK 9FD3374C… is pending. Existing IDs remain stable and clones receive fresh IDs. Configured-3MF cloning/export, belt/IMEX sessions and a full multi-plate workspace are unsupported. |
| 14 | Auto slicing | Debounced saved-change hooks; foreground handoff, generation invalidation, fingerprint freshness and worker snapshots. Uses the existing slicing screen; it does not send to a printer. |
| 15 | Mixed temperatures | Explicit warning/opt-in drives the actual native `Print::set_check_multi_filaments_compatibility` policy. The captured preference participates in slice freshness checks. This does not suppress unrelated invalid temperature/configuration errors; physical multi-material operation is unverified. |
| 16 | Camera style | Actual default/touchpad pointer handling, separate from phone gesture swap. |
| 17 | Zoom to gesture center | Projection-based gesture focus in StlView with bed-plane fallback. |
| 18 | Free camera | Removes constrained orbit pitch; no change to model geometry. |
| 19 | Swap camera gestures | Actual one/two-finger pan/rotate mapping. |
| 20 | Clear unsaved project choice | Removes remembered replace / backup-and-replace answer only. |
| 21 | Clear unsaved preset choice | Removes remembered Save / Discard answer only; successful saves and canceled Save As are distinguished. |
| 22 | Clear printer sync choice | Removes the remembered selected-printer / embedded-printer answer used by the actual 3MF import decision. This does not clear a cloud account, device state or a selected library preset. |
| 23 | Online: region | Works for the Bambu Lab cloud account (China: bambulab.cn, other regions: bambulab.com). The profile cloud keeps its single global API. |
| 24 | Stealth | Dynamic cloud/public HTTP gate, permits validated LAN targets, cancels active HTTP work on policy changes. |
| 25 | Network test | Selected printer protocol test; explicit official HTTPS reachability test when no printer is selected. |
| 26 | Stable updates only | Real stable/prerelease selection for official upstream release checks. No automatic desktop binary installation. |
| 27 | Automatic profile sync | Explicit opt-in, account-scoped pull/merge/push through the actual user preset repository; conflict handling and tombstones. Real cloud account verification remains outstanding. |
| 28 | Built-in profile updates | Blocked: compatible profile-pack validation/catalog rebuild/rollback is absent. Release checking is separate. |
| 29 | Filament sync mode | Colour only, or colour and material: AMS sync also picks a compatible system filament per tray (Bambu LAN/cloud). |
| 30 | Enable network plug-in | Switches the built-in Bambu Lab networking (LAN and cloud) on or off. |
| 31 | Network plug-in version | Shows the built-in implementation (MQTT/TLS, FTPS, account API); no plug-in is downloaded. |
| 32 | Associate: STL | Android VIEW/SEND route and format-validated file-picker test. Android owns default-handler selection. |
| 33 | Associate: 3MF | Same Android association route for 3MF, preserving native import guards. |
| 34 | Associate: OBJ | Same Android association route for OBJ. |
| 35 | Developer mode | Reveals application diagnostics/gallery and filters schema entries explicitly marked comDevelop. No claim of additional native debug APIs. |
| 36 | Skip AMS check | Off: a Bambu start is refused when a used material slot is empty or holds another material type. |
| 37 | Allow abnormal storage | Off: a Bambu upload is refused when the printer reports no usable SD card. |
| 38 | Log level | Structured Java event filtering plus the real upstream native Boost severity setter/readback. Requested Java and actual native levels are distinct: the pinned prerelease engine may enforce a more verbose minimum. No automatic diagnostic upload is implied. |
| 39 | Reload plug-in | Restarts Bambu connections and clears the saved AMS state. |

There are **8 entirely blocked entries**: **#23, #28, #29, #30, #31, #36, #37 and #39**. The other 31 entries have a consumer or platform adaptation with the boundaries above; this is not “31 exact desktop features complete”. In particular, native project settings, STEP, material editing and cloning remain partial contracts rather than a complete Workspace/File port.

Approved engine scope and outstanding acceptance checks: [PREFERENCES-NATIVE-PLAN.md](PREFERENCES-NATIVE-PLAN.md). Native control details: [native-preferences-controls.md](../engine/native-preferences-controls.md). Network/provider boundary: [PREFERENCES-ONLINE.md](PREFERENCES-ONLINE.md).

## Native project contract boundaries

The import worker creates a versioned detached snapshot of global, selected-plate, object, volume, layer-range, material and embedded-preset configurations with native value types and archive-bound identities. Full slicing consumes the validated original model/configuration instead of reloading a stripped geometry file. The effective candidate starts from the selected full native profile configuration, then applies project/plate values; selected-printer mode restores the selected printer scope. Missing options retain that selected base. This does not resolve every arbitrary missing preset parent or make a partial embedded printer JSON a complete standalone profile.

The imported project mode, source hash, plate, selected profiles/overrides and material edits participate in current-model/result freshness. Mode 2 uses the embedded printable area and printer metadata for its header/bed. Project Settings shows effective global/plate values read-only and prevents preset Save/Save As/Use/Delete; these values are not published as library presets. Editing a genuine project-scoped draft and exposing editable object/volume/layer settings remain unfinished.

Multi-plate and nonzero plate-origin projects are rejected until their native-to-workspace origin mapping is faithful. Unsupported units/transforms, substituted/unknown configuration values and incompatible active presets fail explicitly. Geometry-only mode requires the user's explicit decision, clears settings only on a detached geometry copy and retains the original archive. It is never a silent fallback from a failed full import.

## Verification boundaries

Host tests exercise real Java policy, backup and repository code with explicit Android/native stubs or supplied cloud snapshots. The project snapshot parser has 52 host checks and the extended native-config consumer has 45 host checks; these counts do not mean JNI ran. They do not prove native import/slice behavior, actual cloud authentication, physical printer operation or desktop parity.

The combined **52 real native assertions passed on the device**: 32 control assertions and 20 project-fixture assertions, using actual native functions and 3MF writer/loader/config objects. The installed APK is **221,194,096 bytes**, SHA-256 `9FD3374C652D926F5887068C4618B75F999B0FAC4A5F68ADD4B314307B6CECF0`; packaged ARM64 native SHA-256 is `8C146C742B8A3B23B4ADE5C5E0E54A3EAE0705A52EDDFAC8CD64406D249DE0E1`. Android Studio installation through computer use completed in 5 s 106 ms. This is observed JNI execution, separate from host stubs and compile/link success. It does not exhaust every nullable/type combination or prove all desktop configurations. The dedicated release verification report owns screenshots, detailed execution evidence and final UI scenario results.

Historical computer-use scenarios remain attached to their original builds. The first two-PLA fixture stopped at the real “Prime tower is partially outside printable area” guard. Fixture v2 supplied explicit test bed/tower coordinates; **native DA652B… then completed full/current-printer mode 1 slicing: 745,554 G-code bytes**. The same historical native build completed the STEP slice noted above. APK 1E2F… passed the OBJ clone/Arrange action noted above. These observations are not re-labelled as UI evidence for final APK 9FD3374C…; those repeat scenarios are pending at this cut. The 3MF fixtures contain partial test settings resolved against the selected base, not certified production printer profiles. No physical printer or cloud-account validation, desktop configuration/G-code parity or complete Phase 2 acceptance is claimed.

The global preset and object override stores do not share a cross-store transaction. Both candidates are validated before writing; a post-commit failure keeps the object draft, advances the committed preset baseline and stops navigation. Retry can complete the remaining operation.
