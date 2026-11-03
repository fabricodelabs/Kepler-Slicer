# Kepler Slicer: shared agent contract

Preserve the existing Android app and functionality. Java/Android Views is the
current stack; do not replace it with a new project, Compose, or a web frontend.
Read docs/collaboration/WORKFLOW.md and ownership.json before editing.

## Roles
- Codex: architecture, state/storage, native/JNI, geometry, protocols/security,
  validation, tests, builds and integration. Work in outputs/Kepler-Codex on codex/core.
- Claude Code: native presentation, design components, accessibility and adaptive
  layout. Work in outputs/Kepler-Claude on claude/ui. Read CLAUDE.md.
- outputs/OrcaMobile on master is the integration copy. Do not edit another
  agent's worktree or run concurrent Git mutations. The integrator is Codex.

## Coordination
Ownership is explicit in docs/collaboration/ownership.json. Mixed Activities
remain frozen until one named author receives a scoped task. Submit requests in
docs/ui/ rather than editing native/state/network code from the UI branch.
Never remove worker cancellation, lifecycle/generation/fingerprint guards,
read-only settings, validation, or separate upload/start actions for a redesign.
Do not change JNI declarations independently of native exports and packaged .so.
No real printer jobs, cloud publication, or credential extraction is authorized.
Existing APK installation permission persists; only Codex manages the emulator.

Phase 2 is the current product scope. Later product phases remain gated by the
owner. Team setup does not mark incomplete features complete.
Use computer-use for Android UI debugging, as requested by the owner.

Before handoff run tools/check_team_scope.ps1 and git diff --check. No reset,
clean, force push, history rewrite, or automatic conflict resolution by replacement.
Git hooks are review aids, not a security sandbox.
