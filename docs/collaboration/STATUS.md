# Current increment — 2026-11-04 (lead handover)

The owner appointed **Claude Code lead developer and integrator**, replacing Codex. `codex/core` was
fully merged (91dcf06) and is retired; all paths are Claude-owned (`ownership.json`), and AGENTS.md,
CLAUDE.md and WORKFLOW.md describe the single-lead process. UI-010 (full Figma Glass UI v3 port) is on
master at d27f0ab: see [UI-010 handoff](../ui/UI-010-HANDOFF.md) and [session notes](../ui/CLAUDE-SESSION-NOTES.md).

Host suites at handover (JDK from Android Studio, `-JsonJar ..\..\work\json-test.jar`): camera 11, control 24,
engine resources 92, cloud presets 87, general 82 + model restore 70, native config 72 + 78, online 40 + 25,
presets 78, project settings 52, settings schema + INI codec, settings state 87, STEP import Java — pass.
`test_process_overrides` fails on the inherited baseline too (stale 12-field digest assertion);
`test_step_import_options` needs `-Cxx` (no C++ compiler configured). Phase 2 remains open.

# Current increment — 2026-11-04

The UI-008 Settings hub is integrated at `aa38e26`. The [increment report](INCREMENT-2026-11-04-UI008.md)
records the build, device screenshots and remaining design differences.
The [JNI precision fixture](JNI-PRECISION-2026-11-04.md) passed on Android
and is included. Phase 2 remains in progress.

## Accepted UI-007 increment — 2026-11-04

UI-007 is accepted on Android and integrated at `180e64b`. The shared
bottom navigation uses the original Kepler Glass UI v3 icons in Settings,
Preferences and the developer gallery. The [increment report](INCREMENT-2026-11-04-UI007.md)
records the APK, scope and portrait and landscape visual checks. Claude
has stopped. Phase 2 remains in progress.

## Previous UI-006 increment — 2026-11-03

Owner's new primary visual target: [Kepler Glass UI v3 in Figma](https://www.figma.com/design/OBNMhh3YDB0kRzvPnSERYW).
Library/Settings/Preferences browser inspection found concrete icon/navigation/
switch/control differences. See [Figma source audit](FIGMA-SOURCE-AUDIT.md).
On 2026-11-04 Figma MCP returned Settings and Library contexts after the
subscription update. The source screenshots and 84 SVG exports are saved
under `outputs/Kepler-Figma-v3`. The audit identifies the first navigation
assignment. The UI-006 APK below predates implementation of the v3 design.

Latest increment: typed preset values and UI-006 search presentation are accepted
at combined code checkpoint `b44b9ef`. See [UI-006 report](INCREMENT-2026-11-03-UI006.md)
for APK `251F9F26…`, tests, six screenshots and exact acceptance limits.
VIS-006 keyboard clipping is fixed in the checked API37/default-large scope.
Sequential UI-006 repair lease is closed, Claude stopped, consumption cap intact.
Phase 2 remains incomplete; later phases remain gated. Next technical task is
actual-JNI precision conformance, followed by the remaining project settings and
Preferences work. Target highlight persistence after rotation remains open.

## Previous UI-005 increment

Latest increment: UI-005 and the cloud-profile name fallback are accepted at
combined code checkpoint 9cab5b2. See
[UI-005 increment report](INCREMENT-2026-11-03-UI005.md) for APK hash, tests,
four workspace screenshots, acceptance limits and next work. MainActivity's
sequential presentation lease is closed and the file is frozen again.
VIS-004 is fixed in the checked workspace/default-large/portrait-landscape scope.

The following paragraphs describe the earlier UI-001..UI-004 increment.

Technical resource-service work and Claude UI-001..UI-004 were integrated through
tested code checkpoint `b70d4e8`. Actual Claude Code used Sonnet/medium and the
existing bounded launcher. Final APK SHA-256 begins `49513915505BE47D`.
See [the increment report](INCREMENT-2026-11-03.md) for the full hash, tests,
computer-use evidence, acceptance scope and remaining defects.

VIS-001/002/003 are fixed within the checked Settings/Preferences scope. The older
workspace navigation still has an enlarged-font caption wrap (VIS-004); it needs
a separate scoped Claude handoff (subsequently resolved by UI-005 above). Phase 2 and all 39 Preferences are not declared
complete. No printer or real cloud action was performed.

# Historical team setup checkpoint — 2026-11-03

The observations below predate UI-002 through UI-004; their then-open defects were
subsequently checked and fixed as recorded in the current increment report.

- Preserved 85 existing modified/new source files in dddf664 and a separately
  hash-verified backup. Original native library is unchanged across worktrees.
- Separate worktrees/branches: OrcaMobile/master integration,
  Kepler-Codex/codex/core technical work, Kepler-Claude/claude/ui presentation.
- Claude Code 2.1.302 installed through WinGet, browser login completed by owner.
  No credentials were copied into the repository.
- Ownership pre-commit hook tested in all three worktrees; representative allowed
  and forbidden paths tested. Rename detection disabled so both moved endpoints
  are checked. Whitespace is checked before commit as well.
- Baseline APK assembled successfully from Kepler-Codex.
- Initial default-model delegation was stopped when the owner requested reduced
  consumption. The completed replacement ran claude-sonnet-5-5, medium reasoning,
  --max-budget-usd 1.00 and --max-turns 12. CLI estimated cost: USD 0.2956292.
  The CLI result reports num_turns=17 (not identical to the max-turns control's
  agentic-turn counter); no stronger message-count guarantee is claimed.
- UI-001 completed: docs/ui/UI-001-HANDOFF.md and a chip minimum touch-width fix
  on claude/ui. Scope review and assembleDebug passed. It remains a candidate:
  no UI code has been merged into master before visual acceptance.

## Actual computer-use observations
Existing installed native-Preferences APK on Medium Phone API 37 was inspected
in portrait and landscape. Workspace navigation labels were separate and readable
at the current font setting. Enlarged-font checks were not performed yet.

**VIS-001, open:** Settings hub in landscape: the translucent floating bottom
navigation overlaps the Material row; its workspace icon and the Prusament PLA
label are visibly superimposed. Screenshot exported via Android Studio camera:
screenshots/VIS-001-settings-landscape.png (2400x1080). This is evidence from the
existing installed baseline, not verification of the new chip candidate.

Next bounded UI task: give Claude sequential ownership of navigation placement in
SettingsActivity/PreferencesActivity, with this screenshot and existing callbacks
frozen. First require a proposed fix for VIS-001 and large-font/nav host sizing;
Codex reviews technical paths, builds and checks the actual APK through
computer-use before merging. Full portrait/landscape, enlarged-font, keyboard,
card joins and label/icon collision matrix remains required. No automatic
budget-reset retry is scheduled.
