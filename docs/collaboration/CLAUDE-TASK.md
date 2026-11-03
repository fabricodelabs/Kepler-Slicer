# UI-004: stable navigation caption at enlarged system font

Continue existing UI; do not audit/rewrite the app. Sonnet/medium, USD1 estimated
and 12 agentic turns remain. Previous UI tasks completed successfully.

Actual computer-use on UI-003 candidate 5F772D73 at enlarged Android system font
(slider fourth of seven stops, original second) confirmed editor scrolling and
footer separation. One remaining visual defect VIS-003: portrait active caption
"Параметры" breaks mid-word into "Парамет" / "ры". It fits the pill but looks poor.
Screenshot: docs/collaboration/screenshots/UI003-editor-large-font.png.

Fix only GlassNavigation active title presentation: keep one line and ellipsize
at END when space is insufficient; preserve system font scaling, full content
description, selected state, icon size, touch targets, weights and callbacks.
No token/font-size reductions or behavior changes. Existing UI-002/003 fixes stay.
Allowed edits: app/src/main/java/com/orcamobile/app/glass/GlassNavigation.java
and docs/ui/UI-004-HANDOFF.md only. No shell, Git, build or device actions.
Document static changes; visual QA is Codex's responsibility. Finish the small fix.
