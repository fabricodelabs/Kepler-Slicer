# UI-006: settings search states and target highlight

Continue Phase 2 of the existing Java/Views app from 258ebf2. Read AGENTS.md,
CLAUDE.md, WORKFLOW.md, ownership.json and handoff SPEC sections 5.4 and 9.
Sonnet, medium, USD1 estimated cap, 12 turns. No shell/Git/build/device work.
UI-005 is completed; its MainActivity lease is closed.

Named presentation assignment to Claude Code, confined to:
- SettingsActivity.java: hub/editor search presentation and empty/count UI only.
- settings/SettingsOptionAdapter.java: visible search-target highlight only.
- res/values*/glass_entry_strings.xml: localized new display strings.
- docs/ui/UI-006-HANDOFF.md.

Current defects found by source inspection (await actual APK visual testing):
hub search with zero matches hides cards and leaves a blank list; editor has
only a zero count. The jumped-to row changes bold text only, because its drawable
ignores activation. SPEC requires understandable empty states and visible highlight.

Implement concise native empty states with a clear-search action in hub AND
editor; show a result count for active hub search. Keep count/empty message
accessible (polite live region), labels in English/default and Russian resources.
Preserve the scrolling header strategy for landscape, short height and large font;
never use an overlay obscuring controls/results/IME. Empty state must not hide
ListView header/search in scrollHeader mode. Give actions >=44dp targets.
Use existing tokens, typography and glass design. Add restrained tonal/border
highlight for the target option, preserving rounded group joins and dynamic row
height. Keep full accessibility descriptions and the existing default,
approximate, override and project read-only value markers.

Do not change query matching, filter semantics, advanced/developer visibility,
schema/value handling, row callbacks, worker lifecycle/generation guards, draft
restore/save, preset selection, config origins or read-only behavior. No new API,
native, state, network, MainActivity or frozen Activity edits. Technical agent is
updating NativeConfigValues independently; no dependency on its new enum is needed.

Deliver implementation and docs/ui/UI-006-HANDOFF.md with changed files, behavior
impact, static checks and required integrator QA. Do not claim build/visual pass.
Codex will run scope/diff checks, compile, then computer-use hub/editor no-results,
clear recovery, search->jump highlight, numeric/bool/read-only regression checks,
portrait/landscape, default/enlarged font and keyboard/card-join inspections.

Run stopped at its turn limit. Claude must not restart/edit; see UI-006-INTEGRATION-LEASE.md for sequential Codex repair and required acceptance.
