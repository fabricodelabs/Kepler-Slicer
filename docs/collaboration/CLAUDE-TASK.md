# UI-005: workspace navigation and bed-caption spacing

Completed and lease closed on 2026-11-03. Do not rerun this assignment or edit
MainActivity under it. Await the next bounded presentation task. Historical
assignment follows; integrator acceptance is in INCREMENT-2026-11-03-UI005.md.

Continue existing UI from f4d2e6f; do not audit/rewrite the app. Read the small
UI-005-LEASE.md and shared ownership contract. Sonnet/medium, estimated USD1,
12 agentic turns, no shell/Git/build/device work.

Confirmed CU defect VIS-004 on previous APK 495139... at enlarged Android font
(final stop5/7; original2/7): workspace navigation label Parameters/Параметры
wraps mid-word. Its bed warning caption also enters the import + FAB area.
Correction: actual source is MainActivity.java, not SliceActivity mentioned in
previous report. See MainActivity.navigation() and hintPosition in workspace().

Allowed edits only:
- MainActivity.java: navigation() label presentation/touch sizing, and only
  bedHint/hintPosition layout statements within workspace().
- docs/ui/UI-005-HANDOFF.md.

Use one line + END ellipsis for nav labels, preserving the full contentDescription,
selected state, icon sizes, callbacks and system font scaling. Keep item touch
height >=44dp including compact landscape. Reserve horizontal space to the right
of bedHint for the 56dp import FAB plus existing margins (layerPanel uses88dp).
Constrain bedHint width, allow legible wrapping if needed and keep async setText
working. Do not change bedDescription(), its warnings, or add stale descriptions.
No gesture, renderer, state, threading, persistence, import, layer or slicing edits.
No font shrinking or token changes. Existing Settings/Preferences fixes remain.

Finish the small fix and write changed paths, behavior impact, static validation,
open requests in docs/ui/UI-005-HANDOFF.md. Codex will perform visual QA on actual
APK: default/large font, portrait/landscape, model+empty workspace, warning/+FAB
separation, nav destinations, gestures and slice action availability.
