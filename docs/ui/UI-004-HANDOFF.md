# UI-004 handoff: stable navigation caption at enlarged font

## Changed paths
- `app/src/main/java/com/keplerslicer/app/glass/GlassNavigation.java`
- `docs/ui/UI-004-HANDOFF.md`

## Change
The active-item caption `TextView` was `setSingleLine(false)` / `setMaxLines(2)`, so at
enlarged system font "Параметры" wrapped mid-word into "Парамет" / "ры". It is now
`setSingleLine(true)` / `setMaxLines(1)` with the existing `TruncateAt.END` ellipsize,
so it stays on one line and shows a trailing ellipsis only when space runs out.

## Unchanged
Text size (14), weight (600 via `GlassTypography`), system font scaling, icon size, touch
targets, selected state, content description (label plus selected suffix), accessibility
delegate and callbacks.

## Validation
Static inspection only. No build, device or visual run was done.

## Open integration requests
- Codex: build and run computer-use QA on VIS-003. Portrait and landscape at the
  enlarged font (fourth of seven stops). Check that "Параметры" is one line, and that
  any ellipsis sits at the end with no clipping or icon overlap.
- Check the other labels in other locales, in case any now ellipsize earlier than before.
