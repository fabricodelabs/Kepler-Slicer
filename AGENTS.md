# Kepler Slicer: shared agent contract

Preserve the existing Android app and functionality. Java/Android Views is the
current stack; do not replace it with a new project, Compose, or a web frontend.
Read docs/collaboration/WORKFLOW.md and docs/collaboration/ownership.json before editing.

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

## Primary visual source — owner update 2026-11-03
The owner requires 1:1 correspondence with Kepler Slicer — Glass UI v3 in Figma:
https://www.figma.com/design/OBNMhh3YDB0kRzvPnSERYW . It takes visual precedence
over older handoff PNG/HTML/tokens and earlier monochrome assumptions where they
differ. Read docs/collaboration/FIGMA-SOURCE-AUDIT.md for verified nodes, initial
mismatches and remaining context gaps. Use the design's actual icons/components
and exact inspected properties; obtain high-fidelity context and assets before
implementation. Existing component names or Material glyphs do not prove a match.
Preserve functionality and phase gates. Existing UI acceptance is not proof of
parity with this newly supplied Figma source.

Before integration Codex runs tools/check_team_scope.ps1 and git diff --check;
Claude lists those as awaiting integrator execution, because its shell is disabled. No reset,
clean, force push, history rewrite, or automatic conflict resolution by replacement.
Git hooks are review aids, not a security sandbox.

UI acceptance requires computer-use visual QA of the actual APK: inspect text and
icon overlap/clipping, spacing, card joins, system-bar/keyboard insets, portrait,
landscape and enlarged system font. Attach screenshots and record device/font/
orientation and defects. Compilation alone never proves visual correctness.
