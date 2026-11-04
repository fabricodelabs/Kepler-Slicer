# UI-003 handoff: editor space on short screens (UI-003B correction)

## Changed paths
- `app/src/main/java/com/keplerslicer/app/SettingsActivity.java` (`renderEditor` only)
- `docs/ui/UI-003-HANDOFF.md`

## Change
- Preset/save row, Global/Objects/Advanced row, search field, page tabs and the status/count
  text are built into a local `controls` container (no temporary swap of the global `page` field).
  `status` is created directly and added to `controls`, so it is the actual status view and
  `refreshList` keeps writing value/origin messages to it.
- Branch chosen once per `renderEditor()` from configuration, never while typing, no size listeners:
  `scrollHeader = landscape || screenHeightDp < 640 || fontScale >= 1.3f`.
  - Roomy portrait (false): `controls` is added to `page` above the list, pinned as in SPEC.
  - Landscape / short portrait / enlarged fonts (true): `list.addHeaderView(controls,null,false)`
    plus `list.setItemsCanFocus(true)` so the search EditText keeps focus. Reason: pinned controls
    would otherwise consume most of the visible height and leave few or no rows.
- Back/title/menu header (from `shell()`) and bottom `GlassNavigation` stay fixed. ListView
  virtualization, footer-collision reserve (list bottom padding), listeners, workers, preset/project
  semantics, read-only and validation untouched.
- Highlight scroll uses `adapter.positionOf(key) + list.getHeaderViewsCount()` (1 or 0 by branch).

## Validation
Code inspection only; not built or run. No visual pass claimed.

## Requested Codex QA
- Roomy portrait: controls pinned, rows scroll beneath, last row clear of nav.
- Landscape (2400x1080) Printer editor and enlarged fonts (>=1.3): controls scroll away and return
  at top; nav unobstructed; last row not behind nav.
- Keyboard open while typing in search in both branches: focus kept, no re-layout jump, rows filter.
- Tab switch, highlight/search-jump to a row (offset right in both branches), Objects scope,
  project read-only view, rotation mid-editor.

## Open integration requests
None.
