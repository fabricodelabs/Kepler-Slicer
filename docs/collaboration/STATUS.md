# Team setup status — 2026-11-03

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
