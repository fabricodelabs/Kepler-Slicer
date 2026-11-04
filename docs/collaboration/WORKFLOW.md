# Workflow — Claude Code as lead developer (since 2026-11-04)

## Locations
All working copies are siblings under `outputs/`:

| Worktree | Branch | Use |
| --- | --- | --- |
| `Kepler-Claude` | `claude/ui` | Day-to-day development (all areas) |
| `OrcaMobile` | `master` | Integration copy; merge reviewed work, push master |
| `Kepler-Codex` | `codex/core` | Retired, read-only history (fully merged at 91dcf06) |

Remote: https://github.com/nitronov/Kepler-Slicer.git. The original baseline
backup remains under `outputs/Kepler-Team-Backup-20261103-193404`.

## Cycle
1. Work on `claude/ui` (or a short `claude/<topic>` branch for risky work).
2. Build offline, run relevant host suites (`tools/test_*.ps1`), check
   `git diff --check`, verify on the emulator (portrait, landscape, large font).
3. Commit with the attribution trailer; the pre-commit hook runs
   `tools/check_team_scope.ps1` (needs pwsh; in Git Bash add
   `/c/Users/tails/AppData/Local/Microsoft/WindowsApps` to PATH).
4. Push `claude/ui`, then in `OrcaMobile`: `git merge --ff-only origin/master`,
   `git merge claude/ui`, build if the merge was not trivial, push master.
5. Update `docs/ui/CLAUDE-SESSION-NOTES.md` (and STATUS.md for milestones).
Never overwrite uncommitted work, force push, or rewrite published history.

## Native code
Native upstream stays pinned at 6d2869c430284714f0a22570c38d3b359965a802.
Gradle packages the prebuilt .so; it does not rebuild it. Keep both
ExperimentalSlicer.java copies, the C++ exports and the packaged .so in sync.
The native cache `work/orca-headless-fresh` points to OrcaMobile; use a separate
cache for native work from another tree. Record the .so SHA-256 when it changes.

## Builds and devices
`local.properties` is ignored; each worktree configures the Android SDK.
Use Android Studio's JBR: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`.
applicationId stays `com.orcamobile.app`. Emulator: `Medium_Phone_API_37.0`.
`tools/start_claude_ui.ps1` is the legacy bounded launcher from the two-agent
setup and is no longer required.

## Visual acceptance
Run changed UI in the actual APK: navigation labels, text/icon overlap and
clipping, card joins, spacing, dialogs, scrolling, keyboard and system bars, in
portrait, landscape, default and enlarged font. Record device, orientation, font
scale and remaining defects in the increment notes.
