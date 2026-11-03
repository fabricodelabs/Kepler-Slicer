# UI-003: restore usable editor space on short screens

Claude Code owns SettingsActivity presentation under UI-002 lease. Existing
footer collision fix MUST remain. Do not read the whole repo or redo the audit.
Budget/model/effort unchanged: Sonnet/medium/USD1 estimated/12 agentic turns.

CU confirmed VIS-002 on installed candidate E191789E: landscape Printer editor
shows title, preset/save, Global/Advanced, search and tabs, then the nav; the
ListView has ZERO visible rows because fixed header controls consume all height.
See docs/collaboration/screenshots/VIS-002-editor-landscape.png (2400x1080).

Fix SettingsActivity editor presentation so list rows remain reachable in short
landscape, large system fonts and keyboard situations. Keep back/title/menu and
navigation reachable, with no row rendering behind the nav. Preserve virtualization.
A ListView header for preset/filter controls that can scroll away is acceptable
when space is limited (document adaptive deviation from pinned full headers).
Do not put a full ListView in an unbounded ScrollView. Keep portrait usability.
If header views are added, offset adapter highlight positions by the actual header
count so search/highlight continues to target the correct row. Existing listeners,
workers, preset/project semantics, read-only behavior and validation stay intact.
Do not shrink system font or change design tokens to conceal the issue.

Allowed: SettingsActivity.java, existing glass helpers if essential, docs/ui report.
Do not edit PreferencesActivity or any technical source in this follow-up unless
reporting a specific dependency to Codex first. No shell/Git/builds/device actions.
Write docs/ui/UI-003-HANDOFF.md. Codex builds and performs computer-use QA.
This is a new bounded defect-fix assignment after successful UI-002, not an
automatic restart after a budget cap. Finish the small code fix and report.
