# Figma source audit: Kepler Slicer Glass UI v3

Status: source identified through read-only browser inspection. Library,
Settings and Preferences were visually inspected; precise layer parameters,
source assets and tokens have not been retrieved. This is not a complete visual
audit or an implementation handoff.

## Owner requirement and source

The owner requires the Kepler Android UI to match the supplied Figma project
1:1, including its icons, components and screens. This Figma source is the primary
visual reference for the requested work. Existing local PNG/HTML/token material
remains useful historical reference; it must not be used to invent details or
override the supplied Figma design where the two differ.

Source supplied by the owner:
[Kepler Slicer Figma design](https://www.figma.com/design/OBNMhh3YDB0kRzvPnSERYW).

Read-only browser observations recorded by the integrator:

- Browser title: `Kepler Slicer — Glass UI v3`.
- Page: `Kepler v3`.
- `Library` node: `2:32`.
- Library child labels include `Icons · 24 grid, 1.8 stroke`, `Icons`, and
  `Components` (the latter exposed as both a heading and a group).

Observed node links, selected through the file's layer list:

| Entry | Node | Direct reference |
| --- | --- | --- |
| Library | `2:32` | [Library](https://www.figma.com/design/OBNMhh3YDB0kRzvPnSERYW?node-id=2-32) |
| Settings | `6:368` | [Settings](https://www.figma.com/design/OBNMhh3YDB0kRzvPnSERYW?node-id=6-368) |
| Process · all tabs | `7:474` | [Process](https://www.figma.com/design/OBNMhh3YDB0kRzvPnSERYW?node-id=7-474) |
| Preferences · all tabs | `7:997` | [Preferences](https://www.figma.com/design/OBNMhh3YDB0kRzvPnSERYW?node-id=7-997) |
| Process · all tabs · full length | `8:1274` | [Full Process](https://www.figma.com/design/OBNMhh3YDB0kRzvPnSERYW?node-id=8-1274) |
| Preferences · all tabs · full length | `8:2418` | [Full Preferences](https://www.figma.com/design/OBNMhh3YDB0kRzvPnSERYW?node-id=8-2418) |

These are labels exposed through the browser accessibility tree. They do not
establish individual icon paths, component variants, measured bounds, variable
values or instance relationships.

The browser accessibility tree exposed these source entries:

1. Library
2. Workspace
3. Workspace · Tools
4. Workspace · Preview
5. Import model
6. Device · printing
7. Printers & profiles
8. Filament library
9. Connection
10. Settings
11. Export
12. Process · all tabs
13. Preferences · all tabs
14. Process · all tabs · full length
15. Preferences · all tabs · full length

No claim is made that every listed entry has been opened or visually reviewed.

## Initial visual findings

Library was rendered through its direct node link at 64% browser canvas zoom.
Settings and Preferences were rendered at 55%, with adjacent Process/Export and
full-length columns visible. These are browser renders, not exported vector
assets or measured design specifications.

- Library shows a dedicated outline icon set and four bottom-navigation states.
  All navigation captions sit below the icons; the selected item has a light
  rounded pill. Existing Settings uses a dark expanded horizontal selected item,
  so current UI-006 visual acceptance is not acceptance against this v3 source.
- Active switches have a mint/turquoise accent, inactive switches gray. Follow
  this source under the latest 1:1 instruction rather than forcing earlier
  monochrome assumptions onto it. Exact colors remain unmeasured.
- Settings uses compact printer/material/process cards, header search icon,
  inline value controls, changed-state markers and the shared navigation.
  It shows two filament slots; sample counts/values are not evidence that the
  corresponding runtime functionality already exists in the Android app.
- Process has a compact header, light Save action, Global/Objects controls,
  advanced toggle, category chips and grouped rows with dark inset fields.
- Preferences has matching header/search, category chips, compact groups and
  the same bottom-navigation component. Full-length reference columns exist.

These findings establish concrete mismatches for a later scoped handoff. Icon
paths, stroke bounds, gradients, blur, typography metrics and all dimensions
must still be obtained from high-fidelity design context before implementation.

## Available context and missing inspection

The Figma plugin is reported installed and enabled, but the session tool inventory
does not currently expose `get_design_context` or `get_screenshot`. The owner
added `@Figma` after supplying the URL; the subsequent tool inventory still
contained no Figma tools. Browser accessibility labels
are not a substitute for high-fidelity design context.

Before assigning implementation, obtain and inspect the relevant node context,
reference screenshots, component/instance relationships, variants and interaction
states; exact icon/vector assets and export bounds; typography; colors and
opacity; effects, gradients and blur; strokes and radii; layout measurements,
spacing, constraints and scrolling behavior; variables/tokens; and any available
adaptive layouts. Record the mapping from Figma nodes to existing Android
components. Details absent from the inspected source must remain explicit gaps.

Figma-driven UI implementation remains pending the required context; this audit
does not restart Claude. Prepare a bounded implementation assignment after the required
high-fidelity context is available. Do not recreate missing assets from a guess
or claim that the current Material glyphs match the Figma icons.

## Existing native UI and ownership

The existing Android project is preserved. It uses Java/Android Views, with most
UI composed programmatically. This audit does not propose Compose, HTML UI or a
replacement application.

Integration root:
`C:\Users\tails\Documents\Codex\2026-10-28\https-github-com-orcaslicer-orcaslicer-https\outputs\OrcaMobile`.

Claude's worktree is the sibling `Kepler-Claude` on `claude/ui`; Codex's technical
worktree is `Kepler-Codex` on `codex/core`. Ownership remains defined by
[ownership.json](ownership.json) and [WORKFLOW.md](WORKFLOW.md). One author owns a
mixed file for a given task; integration is performed by Codex.

Paths below are relative to the integration root. Java class names in the first
table are under `app/src/main/java/com/orcamobile/app/`.

| Figma concern | Existing reusable implementation | Current ownership |
| --- | --- | --- |
| Foundations and tokens | `glass/GlassTokens.java`; `app/src/main/assets/design/glass_tokens.json` | Claude presentation allowlist |
| Background and glass surfaces | `glass/GlassBackground.java`, `glass/GlassSurface.java` | Claude presentation allowlist |
| Typography | `glass/GlassTypography.java`; `res/font/manrope_variable.ttf` and weight XML resources | Claude presentation allowlist |
| Icons | `glass/GlassIcon.java`; `res/font/material_symbols_outlined.ttf` | Claude presentation allowlist; exact Figma asset correspondence unverified |
| Buttons, chips, fields and rows | `glass/GlassUi.java` | Claude presentation allowlist |
| Switch and bottom navigation | `glass/GlassSwitch.java`, `glass/GlassNavigation.java` | Claude presentation allowlist |
| Bottom sheets and option editors | `glass/GlassBottomSheet.java`, `settings/SettingsEditorDialog.java` | Claude presentation allowlist; technical parsing and callbacks preserved |
| Grouped option rows, value markers and target highlight | `settings/SettingsOptionAdapter.java` | Claude presentation allowlist; binding/callback contracts preserved |
| Settings hub and Process/Filament/Printer editors | `SettingsActivity.java` | Claude presentation allowlist; technical state and lifecycle contracts preserved |
| Preferences tabs | `PreferencesActivity.java` | Claude presentation allowlist; service consumers preserved |
| Component preview and QA | `glass/GlassGalleryActivity.java` | Claude presentation allowlist |

Additional existing Claude-owned paths: `app/src/main/assets/design/*`,
`app/src/main/res/font/*`, `app/src/main/res/drawable/*`,
`app/src/main/res/values*/glass_strings.xml`,
`app/src/main/res/values*/glass_entry_strings.xml`,
`app/src/main/res/values*/styles.xml`, and `docs/ui/*`. Other `phase2_*.xml` and
technical string resources are not automatically included in that allowlist.

| Source area needing a separate scoped handoff | Existing mixed/frozen files |
| --- | --- |
| Workspace, tools and model properties | `MainActivity.java`, `ModelDetailsActivity.java`, `ProcessSettingsPanel.java` |
| Import and startup | `ImportActivity.java`, `LaunchActivity.java`, `StepImportPreferences.java` |
| Printer/profile selection and settings transfer | `PrinterPickerActivity.java`, `ProfilePickerActivity.java`, `SettingsPresetPickerActivity.java`, `SettingsTransferActivity.java` |
| Slicing, preview and export | `SliceActivity.java`, `GcodePreviewActivity.java`, `GcodeExportActivity.java` |
| Connection, device and cloud flows | `NetworkActivity.java`, `CloudSyncActivity.java`, `CloudPrintLoginActivity.java` |
| Legacy/shared mixed presentation | `MdUi.java`, `ProcessSettingsActivity.java`, `ProjectMaterialEditor.java` |

This is an ownership map, not a claim that the current screens already match
the listed Figma entries. Business logic, storage, native/JNI, protocols,
validation, tests, builds and integration remain Codex's responsibility.

## Known comparison risks and bounded handoff

Current `GlassSurface` can blur a positioned copy of the static background on
API 31+, rather than arbitrary dynamic content behind a card. Its correspondence
to Figma effects needs inspection. `GlassIcon` uses Material Symbols Outlined
glyphs with an explicit name/codepoint map; those glyphs have not been compared
with the Figma Library. Shared glass changes also affect Workspace and other
mixed/frozen consumers without editing their source files, so those consumers
need regression checks when a shared component changes.

After context is available, start with one bounded mapping/foundations/icons/
component assignment, using `GlassGalleryActivity` for inspection, then assign
Settings/Preferences screen work separately. Preserve real model/profile values,
units, provenance markers, matching/filter semantics, lifecycle and freshness
guards, draft/save behavior and project read-only enforcement. Sample Figma
content does not replace live application data.

Acceptance requires computer-use inspection of the actual installed APK against
the inspected Figma references: text/icon overlap and clipping, card joins,
spacing, system bars, keyboard, portrait, landscape and enlarged font. Compilation
alone does not establish 1:1 visual correspondence. Record the precise nodes,
assets, APK hash, device settings, screenshots and remaining deviations.

The existing product scope remains Phase 2. Phases 3–7 retain their current owner
approval gates; identifying Workspace, Import, Preview, Device or Export entries
in Figma does not open those product phases or authorize their technical work.

The subagent initially added only this audit document. The integrator then
recorded the primary-source requirement in agent coordination documents and
propagated it through the worktrees. No application code, Figma source design,
APK, user model/profile or emulator state was changed for this design inspection;
no new Claude run was started.
