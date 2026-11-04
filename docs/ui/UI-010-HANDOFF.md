# UI-010 — Full Kepler Glass UI v3 port

Owner request (2026-11-04): port the whole [Kepler Slicer — Glass UI v3](https://www.figma.com/design/OBNMhh3YDB0kRzvPnSERYW)
design 1:1, including icons, with the earlier file-ownership limits lifted for this task. Layouts, colours,
radii and type come from Figma MCP `get_design_context` for each node; behaviour and data paths are unchanged
unless noted.

## Screens

| Figma node | Android | Notes |
| --- | --- | --- |
| Library 2:32 | `res/drawable/kv3_*.xml` | All 52 Library icons + Switch states as VectorDrawables, converted path-for-path by `tools/SvgToVector.java`. |
| Workspace 3:34 | `MainActivity` | Printer header (52dp thumb, 17/700 name, nozzle badge), mode toolbar, plate chip, add button, object card, white Slice button, v3 nav. |
| Workspace · Tools 3:139 | `MainActivity` tools sheet | 12-tool grid; Move/Rotate/Scale edit the real `ModelTransform` through coloured X/Y/Z fields. The other nine tools show "not available yet". |
| Workspace · Preview 3:300 | `MainActivity` preview sheet, `glass/LayerRail` | Sliced status pill, vertical layer rail, colour-mode chips, legend, Time/Filament/Cost read from Orca's G-code summary, layer scrubber with playback, Print → connections, Export. |
| Import model 4:156 | `ImportActivity`, `KeplerScreen` | Format chips filter the real history; Files/Drive open the system picker; Recent scrolls to history; Quick Share/URL/Shapes are not wired. |
| Device · printing 4:336 | new `DeviceActivity` (Workspace Wi-Fi button) | No live telemetry exists: camera, job, temperatures and controls show empty states; slot 1 is the active filament. |
| Printers & profiles 5:245 | `PrinterPickerActivity` | Active printer card with real nozzle variants (tap = save selection), active process, Edit printer (opens the printer editor), Connection; catalogue moved to "Printer library". |
| Filament library 5:406 | `ProfilePickerActivity` | AMS row, search, type/favourite/compatible chips, vendor groups, local favourites. Engine compatibility logic unchanged. |
| Connection 5:594 | `NetworkActivity` via `MdUi` | Shared v3 list, fields and selects; the host-type chip layout is not reproduced (form keeps its protocol selector). |
| Settings 6:368 | `SettingsActivity` hub | Already ported in UI-008. |
| Export 6:588 | `GcodeExportActivity` | Summary from G-code, type segment, G-code options, editable file name used by Save to Files, Send to printer, Preset bundle → transfer. |
| Process 7:474 | `SettingsActivity` editor, `SettingsOptionAdapter` | Top bar with subtitle/search/more/Save, Global/Objects segment, Advanced switch, page title, value fields with units, #FFB454 changed dot. |
| Preferences 7:997 | `PreferencesActivity`, `GlassUi` | Large title + search, section title, select fields, Figma switch. |

Shared: `glass/KeplerUi` (v3 primitives), `KeplerScreen` (sub-screen shell), `MdUi` (legacy list screens now render
in v3), `GlassSwitch` (Figma Switch 46×28), `GlassUi.chip/action/select`, dark launch theme (#383D46).

## Known deviations

- Backdrop blur is not reproduced (glass fill/stroke only); the GL viewport draws the glow backdrop itself.
- (Resolved below) toolpath colouring.
- No plate label in 3D (gizmo and alphabet index added below).
- Project 3MF export, camera, AMS sync and the remaining nine Tools stay "not connected" (see the end).
- The light theme preference no longer changes screens (v3 is a single dark theme).

## Validation

`assembleDebug` (offline) passes. Emulator Medium Phone API 37, Russian, portrait: Workspace, Tools, Preview
after a real slice, Import, Printers, process library, Export, Device, Process editor, Preferences, Connection list,
Slice screen; Workspace landscape. TalkBack was not run.

## Follow-up (same day): previously missing parts

- Preview colour modes are real: `GcodeAnalysis` records Orca `;TYPE:` feature, feed (F), extrusion per mm and
  M104/M109 temperature per segment; `ToolpathGeometry.colors` builds per-vertex colours (Line type uses the
  Figma legend colours, Speed/Flow/Layer height/Temperature use a five-step scale shown in the legend);
  `StlView` draws them with a per-vertex colour shader.
- Tools move gizmo: dashed bounding box and X/Y/Z arrows follow the model while Move is active.
- Export: Share uses the new read-only `ShareProvider` (cache/share, cache/shapes, cache/url-import only; URI grants; not exported);
  "Plate as STL" / the STL segment save the stored STL mesh (as imported); "Toolpaths as OBJ" writes the
  G-code extrusion paths as OBJ polylines.
- Import: From URL downloads STL/3MF/OBJ/STEP over HTTP(S) (offline/stealth policy enforced, 512 MB cap);
  Shapes generates 20 mm cube/cylinder/cone/sphere STLs; Quick Share explains the system flow.
- Device: read-only status of the selected saved printer via `PrinterHostClient.status` (temperatures for
  OctoPrint/PrusaLink, progress for PrusaLink/Moonraker). Pause/Stop stay disabled: no job control is authorised.
- Connection form: host-type chips drive the existing protocol selector. Filament library: alphabet index;
  opened from the Workspace material line. Alert dialogs use the glass surface and Manrope.
- Large font (1.3×) checked on Workspace and Preferences: no overlap or clipping.

Still not implemented (no engine/backend support): Lay on face, Cut, paint tools, Text, Measure, Assembly,
Brim ears; Project 3MF export; camera stream; AMS sync; backdrop blur; plate label in 3D.
