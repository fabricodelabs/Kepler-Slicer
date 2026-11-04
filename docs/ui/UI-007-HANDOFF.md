# UI-007 Figma v3 bottom navigation

## Scope / changed paths
- `glass/GlassNavigation.java`: rewritten; four equal-width vertical tabs (icon above caption), views built once and updated in place on selection/reduced-transparency change (accessibility focus stays attached). Haptic, `setSelectedItem` then listener ordering, selected a11y state and `", selected"` content description preserved.
- `glass/GlassTokens.java`, `assets/design/glass_tokens.json`: new nav-scoped tokens (`itemH, pad, gap, radius, selectedRadius, strokeWidth, labelSize, lineHeight, labelGap, iconSize, fill, stroke, activeText, inactiveText`); `nav.h` 64→68, `nav.inset` 20→16. `activeFlex`/`activeCircle` kept but now unused.
- `SettingsActivity`, `PreferencesActivity`: footer margins 16/8/16/24 only. Settings IME inset union and resize mode untouched.
- `GlassGalleryActivity`: normal mode gets 16dp inset via token; reference mode replaces the fixed-height bottom overlay with a measured footer in the column (-4dp side margins cancel the column's 20dp padding). Scroll content bottom padding 110→16 since the overlay clearance is no longer needed.

## Assets
`R.drawable.kepler_v3_nav_{workspace,settings,export,preferences}` and `_selected` variants, FIT_CENTER in 22dp ImageViews, no tint.

## Remaining deviations
- No backdrop blur; fill rgba(255,255,255,.11) and stroke are plain drawables over the app background. Reference overlay/blur not reproduced.
- Reduced transparency uses `GlassSurface.of(..., navRadius, true)` (existing treatment, no Figma stroke).
- Label uses fixed 1.3 line spacing multiplier with font padding off; verify vs. Manrope metrics.
- Item height is min 54dp and grows with font scale, so the bar exceeds 68dp at large fonts by design.

## Pending Codex checks (nothing run here)
Compile; `tools/check_team_scope.ps1`; `git diff --check`; emulator QA: 390dp portrait (expect 358x68), narrow width, landscape, font scale 1.3/2.0 (ellipsis, no clipping), reduced transparency, keyboard open in Settings, TalkBack selected state/focus retention, gallery reference mode scroll state and routes.
