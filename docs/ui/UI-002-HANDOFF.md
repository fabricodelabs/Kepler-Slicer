# UI-002 handoff: real navigation footer in Settings and Preferences

## Changes (presentation only)
- `SettingsActivity.shell()` and `PreferencesActivity.render()`: `page` is now a
  weighted (0dp, weight 1) child of a vertical `column` LinearLayout that fills `root`.
  `GlassNavigation` is the next child of `column` with `WRAP_CONTENT` height
  (its own `minimumHeight` still applies) and margins 20/8/20/24dp.
  Scroll/list content is therefore laid out and clipped above the nav's top edge,
  and the nav grows with enlarged fonts instead of a fixed `navHeight` dp.
- Removed the end-of-list spacer hack: 110/112dp bottom padding became 16dp
  (Settings hub results list and card scroll, editor list, Preferences content).
- Unchanged: nav styling, destinations, callbacks, API35 inset listener on `root`,
  IME/soft-input mode, Preferences scroll restoration (`bodyScroll`, `restoreScroll`),
  workers, lifecycle guards, validation and preset/project semantics.
- `GlassNavigation.java` was not touched; no helper added. Chip-width candidate kept.

## Acceptance matrix (NOT yet run; needs Codex build + computer-use)
| Case | Expected | Status |
|---|---|---|
| Settings hub portrait/landscape | rows end above nav, none behind it | untested |
| Settings editor portrait/landscape | list clipped above nav; last row reachable | untested |
| Preferences all 5 sections | same; chips/scroll restored after section switch | untested |
| Rotation / section rebuild | footer re-reserved, scroll position kept | untested |
| Enlarged font (portrait+landscape) | nav grows, labels not clipped, title/back/actions no overlap | untested |
| Editor with IME open | text field visible; nav may sit above keyboard | untested |

Only code inspection was done; no build, device or screenshots.

## Remaining risks / defects
- Landscape: nav now permanently consumes ~(navHeight + 32dp) of height, leaving less
  list area on short screens. If too tight, reduce the 24dp bottom margin or hide the
  nav when the IME is shown (needs a decision; not done).
- With IME open and adjustResize, the nav now stays above the keyboard and reduces
  editor space; previously it overlaid. Please check in the editor.
- Settings header (back/title/menu) was not modified; verify no title overlap at large font.
- The 16dp end padding is a cosmetic gap only; other overlays added directly to `root`
  (message/dialog hosts) are unchanged.
- Nav item label text is 14sp with max 2 lines; very large scales may still need a
  GlassNavigation follow-up (out of this scope).
