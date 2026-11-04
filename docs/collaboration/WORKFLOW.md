# Codex + Claude Code

## Locations and baseline
All working copies are siblings under outputs: OrcaMobile (master integration),
Kepler-Codex (codex/core), Kepler-Claude (claude/ui). Original native Preferences
work was preserved at dddf664, with 85 changed/new files independently backed up
under outputs/Kepler-Team-Backup-20261103-193404. The backup also contains the
three modified OrcaHeadless CMake files. This is a checkpoint, not acceptance of
all remaining Preferences or all product phases.

## Ownership and handoff
1. Codex gives Claude a bounded task, read-only technical contracts, acceptance
   checks and owned paths. See CLAUDE-TASK.md for the initial assignment.
2. Claude edits only its worktree/allowlist and writes its handoff in docs/ui/.
3. Codex inspects diff and scope; runs relevant compilation/lint/technical checks.
   Android behavior and visuals are checked with computer-use.
4. Codex commits the reviewed UI result on claude/ui and integrates using an
   ordinary merge in the integration copy. No blind copy/overwrite of trees.
5. Fast-forward clean worktrees to a reviewed integration baseline sequentially
   before the next task. Never overwrite an agent's uncommitted work.

Push each reviewed commit to its corresponding branch on origin promptly.
Push master after its required checks; do not wait for APK distribution.

One author per mixed file per task. To transfer a file, first commit/review the
current work, then update ownership.json and the task through integration and
propagate that coordination commit. The other agent must stop editing that file.
Callbacks, config types/units, snapshot publication and network behavior are
technical contracts; visual changes must not alter them accidentally.

## Isolation
Git worktrees share refs/object storage, not working files/index. They are not a
security boundary. Pre-commit scope checking is installed via core.hooksPath.
Use tools/check_team_scope.ps1 to check staged and unstaged/untracked paths.
Master is the reviewed integration path and is not restricted by that checker.

local.properties is ignored: configure the existing Android SDK in each worktree.
Each worktree has its own Gradle build output. SDK/NDK/dependency prefixes may be
read together. Only Codex builds/publishes native code. Existing native cache
work/orca-headless-fresh points to OrcaMobile; do not reuse it for another source
tree. Configure a separate cache for native changes from Kepler-Codex. Gradle
packages the .so; it does not rebuild it. Keep both ExperimentalSlicer.java copies,
C++ exports and packaged .so synchronized. Native upstream remains pinned at
6d2869c430284714f0a22570c38d3b359965a802.

Both worktrees retain applicationId com.orcamobile.app. Only Codex installs APKs
and drives the emulator. Give package_source.py distinct --output and explicit
--orca paths. Do not share writable native build caches or packaging targets.

## Starting Claude
Use tools/start_claude_ui.ps1 from Kepler-Claude. It verifies the branch and login,
then runs the explicit assignment with file tools only. Shell/MCP automation is
not supplied to this first UI assignment. Claude login is personal; the user must
complete browser sign-in. No credentials are committed or requested in chat.
The launcher is deliberately bounded, not an unattended polling daemon.
Owner-requested consumption controls: Sonnet, medium reasoning, 12 agentic turns
and USD 1.00 estimated API spend per print-mode run. No automatic restart or
interactive mode that bypasses these print-only limits. The CLI cost estimate is
not an account-wide billing cap and may overshoot on the final request. Each new
run gets its own budget; do not chain retries to bypass a reached limit.

Read docs/handoff/SPEC.md, docs/handoff/design/index.md and
docs/handoff/design/tokens.json.
HTML in design/source is reference material, not the application frontend.
Read docs/PHASE2-STATUS.md and docs/PREFERENCES-39-STATUS.md for actual feature limitations.

## Required visual acceptance
Codex must run the changed UI in the actual APK with computer-use before merging
it into master. Capture phone screenshots, record screen/orientation/font scale,
and inspect navigation labels, text/icon overlap and clipping, card joins and
spacing, dialogs, scrolling, keyboard and system-bar insets. Include portrait,
landscape, default font and enlarged system font. Compare against handoff
references. Send numbered defects and screenshots back to Claude for correction,
then repeat affected checks. An untested candidate remains on claude/ui; build
success and static review do not constitute visual acceptance.
