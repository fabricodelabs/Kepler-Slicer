# UI-007 Figma v3 bottom navigation

Completed and integrated at `180e64b`. See the [QA report](INCREMENT-2026-11-04-UI007.md). The assignment below records the original handoff.

Implement this bounded Phase 2 presentation task in Kepler-Claude on claude/ui.
Read AGENTS.md and CLAUDE.md. Use Java/Android Views. This is a new task, separate
from the completed UI-006 run. Launcher remains Sonnet, medium, USD1, 12 turns.
Codex builds, performs device QA and integrates. No shell, Git, device or MCP work.

High-fidelity references are in sibling ../Kepler-Figma-v3, Library/figma-reference.tsx
and Library/preview.png, Settings/figma-reference.tsx and Settings/preview.png.
Library nav variants are nodes 2:234, 2:255, 2:276, 2:297. Settings is 6:368.
The four source icon pairs are already staged. Use resource IDs
R.drawable.kepler_v3_nav_workspace, _settings, _export, _preferences and
each corresponding _selected suffix. These are exact whole-SVG renders from
Figma with transparent padding and original aspect ratio, in drawable-nodpi.
Use FIT_CENTER in 22dp-square ImageViews, no recoloring and no new assets.
Originals and source mapping are in docs/ui/figma-v3/nav/manifest.json.

Scope is GlassNavigation.java, nav-only fields in GlassTokens.java and
app/src/main/assets/design/glass_tokens.json, footer layout only in
SettingsActivity.java, PreferencesActivity.java and GlassGalleryActivity.java,
and a short docs/ui/UI-007-HANDOFF.md. Preserve callers and all callbacks.
Do not edit MainActivity, GlassIcon, GlassSurface, other shared palettes,
other screen content, search/model/profile/state logic, manifests or build files.

Implement the four equal-width vertical tabs, ordered Workspace, Settings,
Export, Preferences. Use existing localized captions and selected accessibility
state. Icons above captions, 3dp vertical gap. Manrope semibold 600, 11sp,
1.3 line height, centered, single-line END ellipsis with full accessible label.
Respect Android font scale and maintain at least 44dp touch height. Keep existing
haptics and callback ordering. Prefer retaining the views when selection changes
so accessibility focus stays attached. Preserve the reduced transparency setting.

At 390dp usable width the bar is 358dp by 68dp, outer radius 34dp, 1dp stroke
rgba(255,255,255,.20), fill rgba(255,255,255,.11), outer content padding 6dp
inside the stroke, 4dp between equal-width items. At reference scale the inner
item height is 54dp and selected white pill radius 28dp. Active text #16181C,
other text white, inactive items transparent. Use scoped nav tokens, preserve
ordinary Android scrolling and existing background treatment. Report the
reference's backdrop blur/overlay differences, keep the nav a measured footer
with no overlap or keyboard clipping. Host inset 16dp horizontally and 24dp
bottom beyond safe insets. Keep existing separation above nav. Keep dynamic
width under other screen sizes. In the gallery reference mode, replace the
fixed-height overlay with a measured footer, retaining other content spacing,
scroll state and sample navigation routes. In Settings preserve the API35+
IME inset union and resize behavior.

Provide the concise handoff with scope, asset IDs, remaining visual deviations
and QA cases. Use the tool budget on implementation. Report compilation and
actual emulator verification as pending Codex checks.
