# UI-008 — Figma v3 Settings hub (6:368)

Implement only the native Settings hub visual layout, including its scrolling
cards and actions. Do this yourself in the Kepler-Claude worktree with the
existing bounded launcher. Read AGENTS.md, CLAUDE.md and the relevant parts of
SettingsActivity and GlassUi. The app is Java/Android Views.

Use the high-fidelity source at docs/ui/figma-v3/settings/figma-reference.tsx and preview.png in the same directory. All input files are inside this worktree. Codex refreshed node 6:368 through Figma MCP and staged original
SVGs and Android PNGs in docs/ui/figma-v3/settings/manifest.json. Use those exact
drawables without tint or substitution; reuse kepler_v3_nav_settings for white
sliders and kepler_v3_nav_export for upload. Asset sizes are in the TSX slots.
Use all three original glows at the Figma positions, base #383d46, as a native
scoped backdrop. Avoid repeating a legacy GlassBackground behind the new
glows. Apply Figma fills, strokes, radii, typography and spacing from TSX.
Use matching bundled Manrope 800 (weight XML if needed), 700/600/500.
Keep Android system bars and the bottom inset; Figma frame corner masking is
device presentation. In normal portrait target the overlay geometry (x16,
bottom24, 68dp) with scroll bottom clearance, so every row stays reachable.
Keep IME and short-screen navigation unobscured. Document any blur gap; keep
content sharp. Use measured responsive layouts with weights/wrap or scroll as
needed; do not introduce text/icon overlaps at larger system font.

At reference width 390, header x20 y52 h48 with 24sp/800 title and circular
44dp search with 22dp icon; content x20/y112. Cards r24, 11% white fill, 20%
white stroke, gap10, headers left16/right8/top8/bottom4 with icon20 and
title16/700. Right buttons have 44dp targets and 22dp art. Dark pickers r18,
black22%/white18% stroke, horizontal12/vertical10 inset and gap12, 15sp/600
name and 12sp/500 subtitle, trailing chevron20. Printer thumbnail 44/r12 with
printer icon24. Process scope group r16/p3 with 32dp selected white r13 and
13sp/700 captions. Advanced control uses the source 46x28 switch states, a
real accessible checkable target >=44dp, and the existing advanced boolean.
Process rows >=52 with icon tile32/r10 and icon18, caption15/600, trailing
original arrow and 12% divider. Implement calibration and import/export
rows from the reference with live existing click handlers. Use TSX for exact
details and lower content.

Scope: hub section of SettingsActivity.java, new scoped presentation helper
in glass/ if needed, targeted GlassTypography/font addition if needed,
glass_strings.xml in relevant locales if needed, docs/ui/UI-008-HANDOFF.md.
Do not redesign editor, Preferences, gallery, shared navigation, or frozen
MainActivity. Do not change persistence, slicing, draft or search matching,
worker/freshness/lifecycle gates, editor actions, read-only checks, or
repository contracts. You may scope a shell variant to the hub; preserve its
status bar/IME insets, startup and existing editor shell. Native Back remains
available. Search icon should reveal existing hub search directly while
retaining transfer and inventory through discoverable existing routes
(transfer row and search-mode overflow, or equivalent). Preserve the
search TextWatcher, match results, no-results, clear and row callbacks.

Use real active profile names from the existing worker and preserve printer
Wi-Fi and settings actions, nozzle value, material and process selection,
Global/Objects, all category routes, calibration info, transfer/inventory.
Use actual nozzle, vendor or preset type when available; omit unknown
metadata and do not fabricate changed counts, slot 2, color, calibration
capabilities or connection state. If a Figma icon has no legitimate route
(such as add slot or sync here), keep only truthful supported actions or
accessible disabled states and document the backend gap. An existing
single-material picker is not a create-slot action. The reference is an
English design; preserve localization and dynamic data, and keep long
translations and user names clear of controls.

Prioritize a compiling complete hub, exact visible assets, responsive
geometry and preserving functionality. Do not run shell/Git/build/device
tools. Short handoff with visual decisions, asset usage, deviations and
behavior to verify. Codex checks compile, builds and visual acceptance.
