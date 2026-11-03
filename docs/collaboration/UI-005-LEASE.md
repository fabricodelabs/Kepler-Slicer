# Sequential presentation handoff: UI-005

Closed after scoped visual acceptance on 2026-11-03. UI commit 2f3e91c and
combined checkpoint 9cab5b2 were reviewed and checked in the installed APK.
MainActivity has returned to mixedFrozen; the historical scope below grants no
further editing permission. See INCREMENT-2026-11-03-UI005.md for evidence and
the explicitly untested cases.

Starting checkpoint f4d2e6f. Claude alone may edit MainActivity.java presentation
within navigation() and the bedHint/hintPosition layout in workspace(). Codex
must not edit MainActivity concurrently. Every callback, model/project state,
renderer, worker/lifecycle guard, persistence and slicing path stays frozen.

Fix the confirmed large-font workspace caption wrap and reserve the import FAB
area from the asynchronous bed warning label. Do not redesign the workspace or
open later product phases. Preserve default/landscape dimensions, font scaling,
full accessibility labels and at least 44dp navigation targets.

Correction to the previous increment report: this older workspace navigation is
implemented in MainActivity.java, not SliceActivity.java. SliceActivity is the
separate slicing screen and remains frozen for this UI task.

Other existing Claude paths retain presentation ownership. Technical work this
increment is outside this Activity. Codex reviews, builds, installs and performs
computer-use QA before integration. Sonnet/medium/USD1/12 controls remain.
