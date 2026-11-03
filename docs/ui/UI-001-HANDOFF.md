# UI-001 handoff: native interface map, priorities and mixed-file requests

Author: Claude Code (claude/ui). Reviewer/integrator: Codex.
Basis: static inspection only. Nothing was compiled, installed or run, so **no visual or runtime claim is made**. Phase 2 stays `incomplete` per PHASE2-STATUS.md; phases 3-7 are not touched.

Not read in full for this run (budget): handoff SPEC.md, `tokens.json`, `SettingsOptionAdapter`, `SettingsEditorDialog` bodies, `GlassBottomSheet`, `GlassSurface`, `GlassSwitch`, `GlassBackground`, and the Activity bodies. Sections that depend on them are marked *unverified*.

## 1. Current UI map

| Layer | Files | Notes |
|---|---|---|
| Tokens | `glass/GlassTokens.java`, `assets/design/glass_tokens.json` | Loaded once, dp values. Relevant: `minTouch=44`, `nav.h=64`, `nav.activeFlex=2.2`, `nav.activeCircle=40`, fonts 18/16/14/12. |
| Typography | `glass/GlassTypography.java` | Manrope 400/500/600/700, falls back to sans-serif. |
| Primitives | `glass/GlassUi.java` | `text/heading/value/row/stackedRow/edit/action/chip/section/divider/surface`. Used by about 12 files. |
| Navigation | `glass/GlassNavigation.java` | 4 items (Workspace, Settings, Export, Preferences). Emits `Listener.selected(int)`; the host navigates. |
| Other glass | `GlassBackground`, `GlassSurface`, `GlassSwitch`, `GlassIcon`, `GlassBottomSheet`, `GlassGalleryActivity` | *Unverified in this run.* |
| Settings | `settings/SettingsOptionAdapter`, `settings/SettingsEditorDialog` | Virtualized ListView rows, editor dialog uses `SOFT_INPUT_ADJUST_RESIZE` plus an insets listener (`SettingsEditorDialog.java:174,219`). |
| Hosts (mixed, frozen) | Main, Settings, Preferences, SettingsTransfer, SettingsPresetPicker, ProfilePicker, PrinterPicker, ProjectMaterialEditor, StepImportPreferences, MdUi, etc. | Each builds its own root and nav. |

## 2. Findings

Fixed in this run (owned file, demonstrated by code):

- **F1 chip touch width.** `GlassUi.chip()` set only `minimumHeight(minTouch)` on its touch target. A short label such as "1" gives a target about 30 dp wide, below the 44 dp token. Added `setMinimumWidth(minTouch)`. Callback, selected state and content description are unchanged. The chip's visual size is unchanged because the inner pill is still `WRAP_CONTENT` and centred. *Needs a visual check* that chip rows do not reflow.

Established by code but not fixable inside the allowlist (mixed files, see section 4):

- **F2 fixed-height nav host.** `SettingsActivity:57` and `PreferencesActivity:65` (and the Gallery at :71) place `GlassNavigation` with a fixed `LayoutParams(-1, dp(navHeight=64))`. The component itself wraps content (`item.minHeight=50`, label `maxLines=2`). At large system font a two-line label plus its padding exceeds 64 dp and will clip. The Gallery's first layout (:51) uses `-2` and does not have this problem. Request: host passes `-2` with `minimumHeight`, which `GlassNavigation` already sets.
- **F3 insets only on API 35+.** Every host registers `setOnApplyWindowInsetsListener` only `if SDK_INT >= 35` (e.g. Settings:54, Preferences:57, MdUi:45). I did not verify how API 26-34 get system-bar padding (theme or `fitsSystemWindows`). Codex should confirm on an older emulator. Then decide whether a shared helper is needed.

Observed, not changed (needs a decision or visual evidence):

- **N1 nav label at narrow width / large font.** On a 320 dp screen the active item gets about 2.2/5.2 of roughly 290 dp, minus the 40 dp circle, leaving about 80 dp for the label. "Preferences" at 14 sp semibold is close to that. At font scale 1.3+ it will break mid-word onto 2 lines and then ellipsize. The content description carries the full label, so TalkBack is fine. Fix options (need screenshots first): `setAutoSizeTextType` with a 11 sp floor, or a lower `activeFlex`. Both alter the reference token behaviour, so this is a **proposal, not applied**.
- **N2 nav title typography.** `GlassNavigation` hard-codes `setTextSize(14)` instead of `t.bodySize` and does not use `setIncludeFontPadding(false)` like `GlassUi.text`. Values are equal today. Aligning would remove drift but may shift text by 1-2 dp vertically, so it is deferred to visual QA.
- **N3 token proposal: `minTouch` 44 -> 48.** Android guidance is 48 dp. The reference token is 44, so I did **not** change it. If accepted, the inactive nav circle (`diameter=minTouch`), `action`, `row`, `edit` and `chip` follow automatically.
- **N4 nav accessibility role.** Nav items report `selected` but no button class or role, so TalkBack may announce them as plain views. Candidate for a later small change (`info.setClassName(Button)`) after Codex confirms the TalkBack output.
- **N5 `GlassUi.value()`** caps width at a fixed 150 dp with no line limit. Long values at large fonts grow tall. Not a defect, but worth a visual pass in rows with trailing values.
- **N6 `GlassUi.row()`** gives a clickable row `minTouch`/`rowMinHeight` but no role description. Same TalkBack question as N4.

## 3. Prioritized remaining visual work

1. Nav at 320 dp, font 1.3/2.0, portrait + landscape (F2, N1, N2).
2. Insets on API 26-34 vs 35+, keyboard with editor dialogs (F3; `SettingsEditorDialog` already handles IME on the numeric editor per PHASE2-STATUS, other editor types and rotation were not verified).
3. Settings rows / editors: value types, read-only project values, `† / ≈` provenance legend legibility at large fonts.
4. Preferences "Не перенесено" rows: confirm they remain visibly non-working at all sizes.
5. Picker/Transfer/Import screens against references 23, 35.
6. Dialog/bottom sheet clipping and system-bar insets.
7. Token proposals N3 and any nav sizing change after screenshots.

## 4. Mixed-file ownership / API requests (sequential, one author per file)

| Order | Request | Files to transfer | Reason |
|---|---|---|---|
| 1 | UI-002: nav host sizing (F2) and shared insets helper (F3) | `SettingsActivity`, `PreferencesActivity`, `MainActivity` (nav placement and root insets only) | Smallest transfer, unblocks nav QA. |
| 2 | Settings hub and Process/Filament/Printer screens | `SettingsActivity`, `ProcessSettingsActivity`, `ProcessSettingsPanel`, `SettingsPresetPickerActivity` | Largest user-visible surface. |
| 3 | Preferences | `PreferencesActivity`, `StepImportPreferences` | 39 unavailable rows must stay explicit. |
| 4 | Import, Export, transfer, pickers | `ImportActivity`, `GcodeExportActivity`, `SettingsTransferActivity`, `ProfilePickerActivity`, `PrinterPickerActivity` | |
| 5 | Workspace/preview/Model details/Network/Cloud | remaining mixed files, `MdUi` | Keep any behavior calls untouched. |

Each transfer needs a named task and a Codex-reviewed commit first. Requested API contracts: none new. The existing `GlassNavigation.Listener.selected(int)` and the `GlassUi` signatures stay unchanged. Any helper I add will be new and additive in `glass/`.

## 5. Changed paths and behavior impact

- `app/src/main/java/com/orcamobile/app/glass/GlassUi.java`: `chip()` touch target minimum width. Behavior impact: touch area only; no callback, value or navigation change.
- `docs/ui/UI-001-HANDOFF.md`: this report.

## 6. Validation requests for Codex (none performed by me)

1. `:app:compileDebugJavaWithJavac`. The edit is one added call on an existing `FrameLayout`.
2. Computer-use screenshots, portrait and landscape, default and enlarged font, at about 320 dp and 360 dp widths: Settings hub with chips (Advanced filter / tab chips), editor dialogs with IME, and the nav bar on Settings and Preferences. Confirm chip rows do not reflow (F1) and record whether nav labels clip (F2, N1).
3. One API 26-34 emulator run to answer F3.
4. TalkBack on the nav bar (N4).

## Open integration requests

- Decide whether to approve the N3 token change (44 -> 48).
- Approve UI-002 file transfer (section 4, row 1).
- Return numbered defects with screenshots; I will correct only what is in the allowlist.
