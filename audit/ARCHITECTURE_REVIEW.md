# Architecture Review

## Component map

```
Android app (app/, Java, Views built in code, "Kepler Glass" UI)
├─ LaunchActivity (exported: launcher, VIEW/SEND content://) ─► MainActivity (workspace, singleTask)
│    ├─ StlView (GLSurfaceView: plate, model, toolpaths)        ├─ TabletSidebar / tablet workspace
│    ├─ ProcessSettingsPanel (embedded editor)                  └─ DesktopWindow (tablet dialogs)
│    └─ SliceActivity.start (background slicing, Progress listener)
├─ Secondary screens: Settings*, ProfilePicker, PrinterPicker, Network, Device, GcodeExport, Preferences, Import…
├─ Storage: SharedPreferences (settings, current model), files/models (imported copies), files/slicer-engine
│    (engine resources, jobs, results), NetworkSecrets (AES-GCM + Android Keystore), cache/share (ShareProvider)
└─ KeplerApplication: lifecycle hooks (orientation, desktop windows, transitions), Bambu transport install

core/ (plain Java, JVM-tested)
├─ Printer protocols: PrinterHostClient, OctoPrint/Moonraker/PrusaLink/Duet/…, BambuLan (payloads + checks),
│    BambuCloud (REST login), BambuSsdp, NetworkHttp (policy-checked HTTP client)
├─ Model data: StlMesh, GcodeAnalysis, ArchiveGuard, ModelTransform, ToolpathGeometry
└─ Settings: PresetRepository, ProjectConfigArchive, PresetJson

engine/ (Android library)
├─ java: ExperimentalSlicer, NativeModelIO, NativePreferences, BambuNetworking (JNI declarations)
├─ src: JNI bridge to OrcaSlicer libslic3r (slice_jni, model_io, process_settings, project_*, step_import),
│    jni_text.hpp (Modified UTF-8 at the boundary)
├─ jniLibs/arm64-v8a/libkepler_slicer_experimental.so (63 MB: libslic3r, OCCT 8.0, TBB, Boost 1.84, expat 2.10.0,
│    libpng 1.6.59, libjpeg-turbo 3.2.0)
├─ jniLibs/arm64-v8a/libbambu_networking.so (5 MB: open-bamboo-networking + JNI bridge, OpenSSL 1.1.1w,
│    curl 7.75.0, mosquitto, cJSON)
├─ third_party/expat (vendored, built into the engine)
└─ obn/ (build script + JNI bridge for the Bambu plugin)
```

## Data and trust boundaries
| Boundary | Untrusted input | Guard |
|---|---|---|
| Intents into `LaunchActivity` | URI, MIME, ClipData | content:// only (SEC-01); file name normalised; content validated by parsers |
| Model files | STL, OBJ, 3MF (ZIP+XML), STEP | 2 GiB copy cap, ArchiveGuard (3MF), StlMesh checks, native preview limits (200 000 triangles, finite coordinates, index checks), expat 2.10 |
| Model links | HTTP responses | NetworkPolicy, manual redirects, 512 MB cap, HTML refused |
| Printers (HTTP/WebSocket/TCP) | JSON, status | response caps, protocol parsers in core (host-tested against simulators) |
| Bambu (MQTT/FTPS via plugin) | MQTT JSON, plugin text | plugin TLS verification, report parsing in core, Modified UTF-8 sanitising (SEC-07) |
| JNI | strings/arrays both ways | length checks, `standard_utf8` decode, `jni_text.hpp` encode, exceptions mapped to Java |

## Observations
- **Separation**: protocol and parsing logic sits in `core` (JVM-testable), Android-specific code in `app`, native in
  `engine` — good for testing; the Bambu transport interface (`BambuLan.Transport`) keeps the plugin replaceable.
- **Large classes**: `MainActivity` (~2 000 lines), `SettingsActivity`, `NetworkActivity` combine UI, state and
  background work; many lines are very long one-liners. This raises review cost and was the main obstacle in this
  audit. Not refactored (no proven defect; high regression risk).
- **Threads**: background work uses raw `Thread`/`Executor` with sequence counters and `isDestroyed()` checks to drop
  stale results — consistent and adequate. A few activities keep running threads after `onDestroy` until the work
  ends (temporary Activity retention, no crash).
- **Engine concurrency**: a global `engine_mutex` serialises native calls — safe, but long slices block previews.
- **Upstream patches outside the repository**: the engine is built from `work/OrcaHeadless`; any upstream fix must be
  scripted (as done for expat in `build-native.ps1`) or it is lost on a fresh build.
- **Desktop/tablet layering** (`Desktop.windowed`, `DesktopWindow`) is applied from one lifecycle hook — new
  secondary screens must be added to that list.
