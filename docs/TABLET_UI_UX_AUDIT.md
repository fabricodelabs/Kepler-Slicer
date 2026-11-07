# Tablet workspace — UI/UX audit (2026-11-07)

Branch `fix/tablet-ui-ux-audit`. Emulator: Pixel API 37, set to 1600×2560 @ 320 dpi (10" tablet) and the stock
1080×2400 phone. Checked by screenshots, UI Automator dumps, `dumpsys gfxinfo/meminfo` and logcat after every fix.

## Architecture (as found)

| Question | Answer |
|---|---|
| Mode choice | `InterfaceMode` reads `interface_mode` from `app_preferences`; set in Preferences → Interface |
| Layout init | `MainActivity.render()` → `workspace()`; with `tabletMode` it adds the top bar, `TabletSidebar`, canvas toolbar |
| Panel | `TabletSidebar`: strip (collapsed), panel with fixed Prepare column or scrolling sections, resize handle |
| States | expanded / collapsed / hidden, saved with width and section (`core/TabletLayout`, JUnit) |
| Sections | Top tabs and strip call `openTabletSection`; tools/preview reuse the mobile workspace modes |
| 3D | Same `StlView`; the sidebar changes the viewport's left margin (surface resized, not recreated) |
| Project state | Model, selection, plate and camera live in shared workspace state and storage; the layout is rebuilt on every return to the workspace |
| Orientation / resize | No `configChanges`: the activity is recreated; orientation is requested per mode |
| Input | Touch, mouse wheel (existing), Ctrl shortcuts; no context menu |

## Problems found

| # | Priority | Problem | Cause | Fix | Status |
|---|---|---|---|---|---|
| 1 | P0 | Unsaved edits in the embedded print-settings editor were lost on returning to the workspace (from any screen) or on recreation | `render()` rebuilt the panel with a new `ProcessSettingsPanel` and dropped the old one | Draft carried into the new editor and saved in `onSaveInstanceState`; editor disposed in `onDestroy` | Fixed, verified (0.16 kept, "Settings changed: 1") |
| 2 | P0 | Crash when choosing a display mode: `IllegalArgumentException … not attached to window manager` (seen in logcat) | The screen rotates and the activity is recreated while the choice sheet slides out; `dismiss()` ran on a removed window | `SheetDialog` dismisses at once when its activity is finishing and skips a window that is already gone | Fixed, verified (3 mode switches in a row, no crash) |
| 3 | P1 | Tablet mode stayed portrait on a large screen | Android 16 ignores orientation requests on large displays (`ignoreOrientationRequest=true`), a platform policy | Request kept; manifest opt-out property for API 36 added; layout made to work in portrait as well | Platform limit, documented |
| 4 | P1 | Hidden panel always came back expanded; no way to hide from the collapsed strip | Restore was hard-wired to EXPANDED | `TabletSidebar.restore()` returns to the state before hiding; Hide button in the strip | Fixed, verified |
| 5 | P2 | Tool bar above the plate ran past the right edge | Centred wrap-content child with unequal margins in a `FrameLayout` | Full-width scroller with margins, bar centred inside | Fixed, verified |
| 6 | P2 | No top tab highlighted while Tools are open | Tools have no tab | Tools count as Prepare (as in OrcaSlicer) | Fixed, verified |
| 7 | P3 | "Settings changed:1" without a space | Translation string | Fixed in `kepler_messages.json` | Fixed |

| Draft kept after leaving the workspace | Tool bar before → after |
|:---:|:---:|
| <img src="tablet-audit/draft-kept.jpg" width="440"> | <img src="tablet-audit/toolbar-before.jpg" width="220"> <img src="tablet-audit/toolbar-after.jpg" width="220"> |

| Hidden | Restored (collapsed, as before hiding) | Tools → Prepare tab |
|:---:|:---:|:---:|
| <img src="tablet-audit/hidden.jpg" width="290"> | <img src="tablet-audit/restored-collapsed.jpg" width="290"> | <img src="tablet-audit/tools-tab.jpg" width="290"> |

## 3D editor checks

| Check | Result |
|---|---|
| Drag the X arrow with the expanded panel (viewport offset 506 dp) | Model moved by X 72.04 mm under the finger |
| Collapse the panel, drag the Y arrow (viewport resized) | Y 68.00 mm, gizmo stays on the model |
| Camera after expand / collapse / hide | Kept (same view in all screenshots) |
| Model and selection after state changes | Kept; Move tool stays active |
| Numeric X/Y/Z input | Works (values typed back to 0) |
| Rapid taps on the tool bar and 9× Ctrl+B, Ctrl+1…5 | No crash, no ANR, state saved correctly |

| Move after offset | Move after collapse |
|:---:|:---:|
| <img src="tablet-audit/move-after-offset.jpg" width="440"> | <img src="tablet-audit/collapsed-move.jpg" width="440"> |

## Window sizes

| Configuration | Result |
|---|---|
| 10" tablet landscape | OK (all sections, three states) |
| 10" tablet portrait (platform ignores the lock) | Layout works; see the forced-portrait shot below |
| Phone, tablet mode, landscape | Panel floats over the scene, Prepare scrolls as a whole |
| Phone, mobile mode | Portrait only; mobile layout unchanged |

<img src="tablet-audit/before-portrait-forced.jpg" width="240"> <img src="tablet-audit/phone-landscape.jpg" width="560">

## Performance (emulator, software-rendered GPU: relative numbers only)

- 10 panel toggles: 85 frames, 22% janky, 90th percentile 34 ms. The cost is the GL surface resize and the
  re-measure of the settings editor when the panel expands.
- Memory after the tests: 443 MB PSS, 1 activity, 1 view root (no leaked activities).
- 0 crashes and 0 ANRs from the app during this audit (`logcat -b crash`, `am_anr`).

## Tests and build

- `./gradlew assembleDebug :core:test` — OK. `tools/run_host_tests.ps1` — all 17 suites passed.
- No instrumentation or screenshot-regression suite: the Maestro and adb MCP servers did not connect; checks were done
  with adb, UI Automator dumps and screenshots, which are stored in `docs/tablet-audit/` as references.

## Remaining and not checked

- Android 16+ large screens may keep the app in portrait regardless of the lock (platform policy).
- Panel resize updates the 3D area on release, not while dragging.
- Collapsed state hides the numeric X/Y/Z fields; they come back when the panel is expanded.
- Not checked: 7–8" and 12–14" tablets, foldables, split screen, freeform/desktop windows, stylus, mouse right-click
  (no context menu exists), large system font in tablet mode, a real printer.
- Settings, profile pickers, slicing and export still open as separate screens.

## Changed files

`MainActivity.java` (draft carry-over, restore, tool bar layout, tab highlight), `TabletSidebar.java` (restore,
Hide in the strip), `glass/SheetDialog.java` (safe dismiss), `AndroidManifest.xml` (API 36 opt-out),
`assets/kepler_messages.json` (string), `docs/TABLET_UI_UX_AUDIT.md`, `docs/tablet-audit/*`.

## Round 2: unified design and plate tools (2026-11-07)

| Owner remark | Change |
|---|---|
| Elements stuck together in the panel | Blocks of the Prepare column: 16–20 dp around headers, 40 dp header buttons 8 dp apart, 52 dp selectors, 12 dp between cards |
| Design must match the mobile interface; input fields differ | The embedded settings editor now uses the mobile Glass UI: glass chips, grouped glass cards, label left and the value in the 40 dp field pill with its unit (as in the mobile settings rows), glass switches, mobile buttons |
| Plate tools beside the plate, as in OrcaSlicer | Column at the right of the 3D area: delete plate, put models on the plate, arrange, lock plate, plate settings, move plate to the front; object tools stay in the bar above the plate |

- Engine: `editModel` op 5 puts every object on the plate (`ensure_on_bed`), op 6 arranges with OrcaSlicer
  `arrange_objects` on the printer's printable area, 6 mm apart. No JNI signature change; `build-native.ps1` verified
  the exports. Checked: Arrange on the vase ran without errors and rewrote the model as 3MF.
- Lock: per plate (model file + plate index); tools and plate edits refuse with a message while locked.
- Plate settings: name (stored on the device, shown in the plate chip), print sequence, spiral vase through the same
  validated overrides as the editor. Bed type and the first/other-layer filament order are not exposed by the engine's
  process metadata, so they are not in the sheet yet.
- Delete plate and Move to the front are disabled with one plate (as in OrcaSlicer). For multi-plate projects the
  engine now does them on the whole project (`kepler::edit_project_plates`, `editModel` ops 7 and 8): the plate list
  is reordered or shortened, every kept instance moves with its plate to the new place of the desktop plate grid
  (`plate_grid_origin`, the `PartPlateList` formula), and the project is written back with each plate's name, lock and
  own settings. Deleting asks for confirmation; the neighbouring plate stays open.
- Fixed while checking: glass cards drew past scroll bounds (rows under the Reset/Save buttons, tool bar past the
  edge); scrollers now clip to their outline.

<img src="tablet-audit/unified-design.jpg" width="640">

<img src="tablet-audit/plate-locked.jpg" width="320"> <img src="tablet-audit/plate-settings.jpg" width="320">

### Multi-plate actions (verified on the Pixel_Tablet emulator, Android 16, 2560×1600)

Fixture `tools/tests/fixtures/ThreePlates.3mf` (OrcaSlicer badge split into plates "Logo", "Vertical", "Horizontal").

| Step | Result |
|---|---|
| Open plate 2 ("Vertical"), **Move plate to the front** | Plates became Vertical, Logo, Horizontal; plate 1 opened with its model on the bed; the button is disabled on plate 1 |
| **Delete plate** on plate 1 (confirmation shown) | Two plates left (Logo, Horizontal); Logo moved onto the bed; plate names kept |
| Crashes / ANR during the run | 0 |

All plate tools exist only in the tablet workspace; the plate lock is ignored in the mobile interface.

<img src="tablet-audit/plate2-before.jpg" width="300"> <img src="tablet-audit/plate-moved-first.jpg" width="300"> <img src="tablet-audit/plate-deleted.jpg" width="300">
