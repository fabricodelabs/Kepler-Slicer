# UI-001: native interface handoff and focused component improvements

Owner: Claude Code. Reviewer/integrator: Codex. Scope: existing Phase 1/2 UI.

1. Read AGENTS.md, CLAUDE.md, ownership.json, WORKFLOW.md, the handoff SPEC/design
   index/tokens and current Phase 2 status. Inspect the existing glass components,
   settings adapter/editor and relevant Activity call sites without editing them.
2. Create docs/ui/UI-001-HANDOFF.md: map current UI, prioritize remaining visual
   work, list exact mixed-file ownership/API requests for the next assignments.
3. Within the owned presentation components, fix concrete layout/accessibility
   issues found by inspection: navigation labels at narrow widths/large fonts,
   touch targets, keyboard/insets handling and consistent component typography.
   Preserve reference tokens unless a documented accessibility fix requires a
   proposed token change; propose such changes rather than silently applying.
   Make a small reviewable change only where the issue is demonstrated by code.
4. Preserve all callbacks, value types, nullable/enum handling, read-only state,
   provenance indicators, settings meaning, navigation destinations and behavior.
   Do not change functionality or mark unimplemented capabilities complete.
5. Record changed paths and validation requests. No Git mutations, shell tools,
   app installation, network calls or account access. Codex performs compilation
   and computer-use QA before integrating. If no safe defect is established,
   deliver the handoff report and explain instead of making speculative edits.

Future UI work across all existing screens belongs to Claude after sequential
mixed-file handoffs. Product phases 3-7 are not opened by this assignment.
