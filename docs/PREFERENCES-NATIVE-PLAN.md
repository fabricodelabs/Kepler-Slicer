# Preferences: remaining engine contracts

Scope: Phase 2 Android Preferences only. This is a proposal, not authorization to change the engine or start Phases 3–7.

SPEC §0.5: «Ядро слайсинга (libslic3r или то, что подключено в порте) не менять без согласования.»

## Proposed changes requiring agreement

Extend `engine/src/slice_jni.cpp` and `com.keplerslicer.engine.ExperimentalSlicer` with a detached, versioned project/config snapshot. Keep existing slicing entry points and preserve the current native library as a reproducible baseline. Build a replacement `.so` only after agreement.

1. **Full 3MF load / printer-sync remembered answer:** expose embedded global, plate and object config with their types and native preset identities; validate printer compatibility before committing selected settings. Present the real printer-profile synchronization decision. Geometry-only mode remains explicit and preserves the original project. Do not strip embedded configuration to bypass existing guards.
2. **STEP import preference:** expose upstream OCCT tessellation with linear/angular tolerance through the model import API. Validate and preview before committing. This requires adding the Android OCCT dependency; a stored switch without an importer does not satisfy this item.
3. **Auto flushing / mixed temperatures:** expose a real multi-filament config snapshot including colors, types, material mapping and `flush_volumes_matrix`. Adapt the upstream flushing calculation in `GUI/Plater.cpp` and expose `Print::set_check_multi_filaments_compatibility`. Recompute only on committed material/color changes, preserve manual values according to the upstream rule, and require the appropriate mixed-temperature warning.
4. **Arrange after cloning:** expose model-session object/instance IDs and plate transforms, cloning and upstream `libslic3r/Arrange.cpp` with bed exclusions. Apply arrange after a successful clone; importing or centering an object is not a substitute.
5. **Native logging:** expose the engine severity setter if present in the pinned build; keep Java and native logging scopes explicit.

The preference contracts above overlap later Workspace/File phases. Agreement for an engine contract alone does not authorize a complete new Workspace or File UI. Any prerequisite that cannot be isolated will be reported before expanding phase scope.

## Verification before acceptance

- Compare original and modified native behavior on identical STL/OBJ/3MF fixtures and presets; document G-code differences.
- Test invalid projects, missing parents, all native value types, plate/object overrides and rollback on failed validation.
- Test multi-material flush matrices and clone arrangement against the pinned desktop Orca build.
- Test STEP import at two tessellation tolerances and validate the resulting mesh.
- Build Android APK, install and inspect through computer use. No real print is started.

## Separate provider dependencies

The Bambu region, plug-in enable/version/reload, AMS bypass, abnormal storage and filament synchronization settings require a usable Android Bambu provider. The existing Java HTTP transport is not that provider. Native bridge permission does not supply a closed-source SDK or prove its Android compatibility. A concrete provider feasibility report is required before these can be marked implemented.

Automatic built-in preset replacement additionally requires catalog/schema/engine compatibility checks and atomic rollback. A desktop release query does not satisfy this preference.
