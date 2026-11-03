# Preferences: remaining engine contracts

Scope: Phase 2 Android Preferences only. **The owner approved this plan on 2026-11-02:** «Разрешаю изменения ядра по этому плану». The approval covers the native contracts below and their required Android consumers/verification. It does not authorize Phases 3–7 or declare Phase 2 complete. APK installation and repeated computer-use verification were separately authorized without asking again.

SPEC §0.5: «Ядро слайсинга (libslic3r или то, что подключено в порте) не менять без согласования.»

Verification cut, 2026-11-02: **52 real native assertions passed through computer use on installed APK 9FD3374C… / native 8C146C74…** (32 controls + 20 project contracts). Historical native DA652B… passed STEP slicing (2,371,725 bytes) and full/current-printer 3MF v2 slicing (745,554 bytes); historical APK 1E2F… passed OBJ cloning + Arrange with two added copies (36 preview triangles, 42 × 42 × 20 mm bounds). Repeating these UI scenarios on final APK 9FD3374C… remains pending at this cut. Exact hashes and boundaries are in [PREFERENCES-39-STATUS.md](PREFERENCES-39-STATUS.md); this does not close the acceptance checks below or Phase 2.

## Approved implementation scope

Extend `engine/src/slice_jni.cpp` and `com.keplerslicer.engine.ExperimentalSlicer` with a detached, versioned project/config snapshot. Keep existing slicing entry points and preserve the pre-native library/release evidence as a reproducible baseline. Replacement native builds are now authorized; their exact versions and runtime evidence must be recorded separately.

1. **Full 3MF load / printer-sync remembered answer:** expose embedded global, plate and object config with their types and native preset identities; validate printer compatibility before committing selected settings. Present the real printer-profile synchronization decision. Geometry-only mode remains explicit and preserves the original project. Do not strip embedded configuration to bypass existing guards.
2. **STEP import preference:** expose upstream OCCT tessellation with linear/angular tolerance through the model import API. Validate and preview before committing. This requires adding the Android OCCT dependency; a stored switch without an importer does not satisfy this item.
3. **Auto flushing / mixed temperatures:** expose a real multi-filament config snapshot including colors, types, material mapping and `flush_volumes_matrix`. Adapt the upstream flushing calculation in `GUI/Plater.cpp` and expose `Print::set_check_multi_filaments_compatibility`. Recompute only on committed material/color changes, preserve manual values according to the upstream rule, and require the appropriate mixed-temperature warning.
4. **Arrange after cloning:** expose model-session object/instance IDs and plate transforms, cloning and upstream `libslic3r/Arrange.cpp` with bed exclusions. Apply arrange after a successful clone; importing or centering an object is not a substitute.
5. **Native logging:** expose the engine severity setter if present in the pinned build; keep Java and native logging scopes explicit.

The preference contracts above overlap later Workspace/File phases. Agreement for an engine contract alone does not authorize a complete new Workspace or File UI. Any prerequisite that cannot be isolated will be reported before expanding phase scope.

Implementation inventory and remaining limits are tracked in [PREFERENCES-39-STATUS.md](PREFERENCES-39-STATUS.md). Full-project Settings is currently read-only; configured-3MF clone/export, multi-plate/nonzero-origin localization and a genuine project-scoped settings draft remain incomplete. STEP placement correction has a historical positive slice, while final-build UI repetitions and broader comparisons remain in progress. The acceptance checks below remain requirements, not a record that they all passed.

## Verification before acceptance

- Compare original and modified native behavior on identical STL/OBJ/3MF fixtures and presets; document G-code differences.
- Test invalid projects, missing parents, all native value types, plate/object overrides and rollback on failed validation.
- Test multi-material flush matrices and clone arrangement against the pinned desktop Orca build.
- Test STEP import at two tessellation tolerances and validate the resulting mesh.
- Build Android APK, install and inspect through computer use. No real print is started.

## Separate provider dependencies

The Bambu region, plug-in enable/version/reload, AMS bypass, abnormal storage and filament synchronization settings require a usable Android Bambu provider. The existing Java HTTP transport is not that provider. Native bridge permission does not supply a closed-source SDK or prove its Android compatibility. A concrete provider feasibility report is required before these can be marked implemented.

Automatic built-in preset replacement additionally requires catalog/schema/engine compatibility checks and atomic rollback. A desktop release query does not satisfy this preference.
