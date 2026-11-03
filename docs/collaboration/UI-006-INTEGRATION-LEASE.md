# UI-006 sequential integration repair lease

**Closed after bounded visual acceptance**, 2026-11-03. Repairs `7639705` and
`5fcd61f` integrated at `b44b9ef`. Normal UI ownership resumes; Claude remains
stopped. See [the final evidence and limits](INCREMENT-2026-11-03-UI006.md).
The following text records the scope while the lease was active.

The bounded Claude Code run ended at the configured 12-turn limit, USD0.3363753.
Candidate 32e59cd contains SettingsActivity and
SettingsOptionAdapter edits, but resources/handoff were not written. It is not
accepted or merged to master. No automatic Claude restart is authorized by this
lease; keep its consumption cap intact.

Claude is stopped. Codex integrator temporarily owns only UI-006 compile/resource
completion and actual-QA defect correction in Kepler-Claude on claude/ui:
SettingsActivity.java search presentation, SettingsOptionAdapter highlight,
res/values*/glass_entry_strings.xml, docs/ui/UI-006-HANDOFF.md. No callbacks,
filter/worker/state/config semantics, other UI, or native changes. This is a
sequential review/repair handoff, not a competing UI author. The task remains
Phase 2, with original UI-006 visual acceptance required before master merge.
Record Codex repair authorship and all test/visual limitations. Close this lease
when the reviewed increment is integrated; Claude must not edit during it.

CU defect VIS-006: on combined APK 155AE7D49F3FBA28, API37, font stop5/7,
portrait+IME, the clear button is partly covered and the list cannot scroll to
it. The root consumes system-bar/cutout insets but omits IME bounds. The same
sequential QA correction includes SettingsActivity.shell keyboard sizing only:
union IME in API35+ safe insets and request adjustResize on older versions.
Keep list headers, search callbacks, state/config and read-only semantics intact.
