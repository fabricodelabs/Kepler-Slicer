# UI-005 handoff: workspace navigation and bed-caption spacing

## Changed paths
- `app/src/main/java/com/keplerslicer/app/MainActivity.java`
  - `navigation()`: label `setMaxLines(2)` -> `setMaxLines(1)` (END ellipsis kept, so
    "Параметры" can no longer wrap mid-word); `item.setMinimumHeight(dp(44))` so each
    item keeps a >=44dp touch target, including compact landscape (26dp pill + 2dp gap
    + one text line is otherwise ~40dp).
  - `workspace()`: `hintPosition` right margin 0 -> `dp(88)` (56dp FAB + 16dp margin + 16dp
    clearance, same reserve as `layerPanel`). The wrap_content `bedHint` is now measured
    inside the remaining width and wraps at word boundaries when long.
- `docs/ui/UI-005-HANDOFF.md` (this file)

## Behavior impact
Presentation only. Label text, full `contentDescription`, selected state, icon sizes,
font sizes (sp, system scaling), click callbacks, `bedDescription()` and async
`setText` via `bedHint` are untouched. No tokens, gesture, renderer, state, threading,
persistence, import, layer or slicing changes.

## Static validation
Code inspection only; no build was run. `setMinimumHeight` on a vertical LinearLayout
with wrap_content only raises height when content is smaller. `bedHint` has no
maxLines, so long warnings wrap rather than clip. Nav label at enlarged font may now
show an ellipsis on narrow screens (intended; the full description remains for
accessibility).

## Open requests (Codex)
Visual QA on the actual APK: default/large font, portrait/landscape, model + empty
workspace, warning vs. +FAB separation (including the long "Пластина не определена ·
все объекты проекта" text), nav destinations, gestures and slice action availability.
Check that the taller minimum item height does not crowd the bottom bar in compact
landscape. No visual pass is claimed here.
