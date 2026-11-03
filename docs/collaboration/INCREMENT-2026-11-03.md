# Joint development increment — 2026-11-03

Status: technical increment and UI-001 through UI-004 integrated after scope review
and computer-use checks.
This report does not complete Phase 2, the remaining 39 Preferences items, or
later product phases. Android remains the current platform.

## Roles and source checkpoints

All copies are under the existing workspace's `outputs` directory:

| Worktree | Branch | Responsibility | Reviewed checkpoint |
| --- | --- | --- | --- |
| `OrcaMobile` | `master` | Codex integration | `b70d4e8` tested code |
| `Kepler-Codex` | `codex/core` | Technical implementation, tests, builds | `b70d4e8` tested code |
| `Kepler-Claude` | `claude/ui` | Claude Code native Android presentation | `b70d4e8` tested source |

The existing Java/Android Views app is preserved. Claude owns presentation in
SettingsActivity and PreferencesActivity through an explicit sequential handoff;
their callbacks, storage and business contracts remain unchanged. Only Codex
installs builds and drives the emulator. Master is an ancestor of the UI branch;
the UI changes were integrated by fast-forward after the final scope review.

## Codex: engine resource service

Commits `14d33d4`, `d9695ec` and `09cd0bb` introduce the shared worker-only
`EngineResources.prepare(Context[, Progress])` service, used by slicing, profile
metadata, imported model preview and STEP preferences. Resource extraction no
longer belongs to SliceActivity. JNI declarations and the packaged native library
are unchanged.

Preparation uses an interruptible process-wide lock, private staging, ZIP central
directory/CRC/size checks, a versioned manifest and checked publication/rollback.
It rebuilds legacy or structurally damaged caches and removes only owned numeric
staging orphans. Permission failures and symlinks fail closed. Bounds apply to the
packaged resource archive, not STL/OBJ/3MF/STEP imports.

Per-file `fsync` was removed after slow emulator initialization exposed its cost
across 17,706 files. Archive and manifest flushing remain. The cache fast path
checks paths and lengths, not same-length content corruption; power-loss
durability, process-death/ENOSPC and failed-rollback fault injection are not
claimed. See `docs/ENGINE-RESOURCES-SERVICE.md` for the full contract and limits.

Verification completed:

- Production archive host suite: **95 checks passed**, including the real
  packaged archive; 17,706 files, latest Windows-host extraction **8.900 s** and
  cache check **0.537 s**. These are host measurements, not Android timings.
- STEP Java host suite: **50 checks passed**; this does not simulate the native
  boundary.
- `assembleDebug` and `lintDebug` passed for the combined candidate. Lint reported
  **0 errors and 120 warnings** on final UI-004; existing warnings remain.
- Static review and ownership check passed; UI range `4008656..908fc7e` passes
  `git diff --check` and the Claude scope guard for all six paths.
- Installed APK restored and rendered the saved STEP cone. Profile metadata also
  loaded during the resource-service emulator checks. A fresh, forced-empty-cache
  Android timing measurement has not been performed.

## Claude Code: UI continuation and review fixes

Actual Claude Code runs used **Sonnet**, **medium** effort, file tools only,
`--max-turns 12` and `--max-budget-usd 1.00` per bounded run. There is no automatic
budget-reset retry. CLI cost estimates are not an account-wide billing cap.

| Assignment | Result | Estimated cost, USD |
| --- | --- | ---: |
| UI-002 | Reserve measured navigation space in Settings and Preferences | 0.1468075 |
| UI-003 | Adapt editor headers to short screens and enlarged fonts | 0.1138960 |
| UI-003B | Correct implementation after review | 0.0956866 |
| UI-004 | Keep active navigation caption on one line at enlarged font | 0.0419200 |
| Total for these four runs | Completed | 0.3983101 |

The earlier UI-001 touch-target change is also included: chips have a minimum
touch width without changing their visual pill. UI-002 replaces the floating
footer/content overlap with a measured vertical layout. UI-003 keeps full editor
controls pinned in spacious portrait and makes them a focusable ListView header
in landscape, short screens or font scale >= 1.3. Row virtualization is retained;
highlighted-row positions account for the optional header. Search, dirty-state,
read-only, save and navigation callbacks were preserved in static review.

## Installed candidate and visual checks

Tested source: `claude/ui` commit
`b70d4e80150fd27d913f21577f827381283d26ff`.

APK: `outputs/Kepler-UI002-review/Kepler-Slicer-UI004-candidate.apk`.

SHA-256:
`49513915505BE47DC3C095A77E00073CD8E8403A3B2269948208923B6D6A8FE4`

Exact size is recorded in `candidate-ui004.json`. Native library SHA-256:
`8C146C742B8A3B23B4ADE5C5E0E54A3EAE0705A52EDDFAC8CD64406D249DE0E1`.

Codex installed the candidate through Android Studio and checked the actual app
with computer use on **Medium Phone API 37.0**, in portrait and landscape. Current
evidence and observations:

- **VIS-001:** Settings landscape content previously ran beneath the footer.
  The measured footer fix was visually confirmed; the final rows are reachable.
- **VIS-002:** After fixing the footer, the landscape editor's fixed controls
  left no usable row area. This was captured and returned to Claude. With UI-003,
  the controls scroll and settings rows are reachable down to the bottom.
  Evidence: `screenshots/UI003-editor-landscape-bottom.png`.
- Portrait search was checked with the software keyboard visible: query
  `height` returned **9 results**. This checks the focus/IME path rather than
  inferring it from compilation.
- The saved STEP model was restored and visibly rendered after reinstalling the
  final candidate.
- Preferences General was checked in portrait/landscape and Control in portrait
  on the preceding UI-002 build; footer separation and readable rows were
  observed. These are historical observations, not a complete final-build matrix.

Additional final checks on UI-004 (installed successfully through Android Studio,
414 ms install message):

- Enlarged Android font was visibly applied. Final Settings inspection confirmed
  slider stop **5 of 7** (not the initially inferred fourth stop); the original
  **2 of 7** was restored and verified after QA. Display size was unchanged.
- **VIS-003 fixed:** enlarged-font portrait active navigation caption broke
  mid-word. Claude's UI-004 keeps one line with trailing ellipsis, and the full
  accessibility label. Verified portrait ellipsis and full landscape caption;
  neither overlaps the icon. Evidence: `screenshots/UI004-settings-large-font.png`.
- Enlarged-font editor controls scroll away and final rows remain above navigation
  on UI-003. Evidence: `screenshots/UI003-editor-large-font.png`. UI-004 changes
  only navigation title wrapping, so the editor code is unchanged.
- Preferences General, Control, Associations and Developer were inspected in
  landscape at enlarged font; Control also in portrait. Network was inspected in
  portrait at enlarged font on UI-003. Visible text, switches, card joins and
  footer boundaries were separate; full row-by-row coverage is not claimed.
- Numeric auto-slice delay sheet at enlarged font: field, Save and Cancel remain
  visible with numeric IME. Cancel returned to the original **0 seconds**.
- The final APK again restored and rendered the saved STEP cone.

Acceptance is limited to this increment's Settings/Preferences/navigation changes,
not the whole app. **VIS-004 remains:** the older workspace navigation (a different
component in frozen SliceActivity) still wraps its Parameters caption at enlarged
font; its bed caption also needs spacing review. A later scoped Claude handoff is
required before changing that mixed Activity. Other locales, maximum font and a
complete dialog/orientation matrix remain follow-up coverage. System Android
Settings repeatedly showed its own ANR while configuring fonts; it recovered and
the font test and restoration completed. No Kepler ANR was observed in these checks.

No physical printer, real cloud account or print job was used for this increment.
