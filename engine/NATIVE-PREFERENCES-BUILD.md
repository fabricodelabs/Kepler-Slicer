# Native Preferences extension: build and STEP contract

The owner approved [the Phase 2 native plan](../docs/PREFERENCES-NATIVE-PLAN.md)
on 2026-11-02. This approval does not open Phases 3–7 or assert an Android Bambu
provider. The original ARM64 library is preserved separately before publication.

## Reproducible source configuration

The clean upstream tree is pinned at
`6d2869c430284714f0a22570c38d3b359965a802`. Apply
`engine/android-headless.patch` to a separate checkout, not the canonical clean
upstream used by source packaging. The patch's `target_sources` explicitly lists
all JNI-side translation units, including `model_io`, `project_inspector`,
`project_settings`, `native_preferences`, `step_import` and
`color_space_headless`. `git apply --check`
against the clean pinned tree passed after regenerating this patch.

`configure-headless.ps1` documents dependency/toolchain parameters. The current
Windows workspace already has all cross-built dependencies, including OCCT
8.0.1. The working cache is `work/orca-headless-fresh`, source
`work/OrcaHeadless`, ABI `arm64-v8a`, API 26, NDK r30, CMake 4.4.4, Ninja 1.13.2.
Use the actual CMake binary at `work/android-tools/cmake/data/bin/cmake.exe`;
the neighboring `bin/cmake.exe` is a Python launcher and needs its Python module.

From the Android project directory, after the initial configuration:

```powershell
./engine/build-native.ps1 -Build ../../work/orca-headless-fresh `
    -Cmake ../../work/android-tools/cmake/data/bin/cmake.exe `
    -Ndk ../../work/android-ndk/android-ndk-r30
```

This regenerates CMake, builds only `kepler_slicer_experimental` (with its normal
dependency graph), checks ARM64 ELF/JNI exports and stages the library under
`kepler-stage`. Linker `--no-undefined` is enabled. The unstripped full library
stays in the build tree; the staged copy removes debug symbols, following the
existing APK packaging practice. No feature/source code is removed to reduce
size. `native-build.json` records hashes, size, exported JNI names and provenance.

Use `-PublishToApp` only after integration is ready. The script preserves the
previous packaged `.so` under `outputs/Kepler-Native-Phase2-baseline/<SHA256>/`
before replacing it, and verifies the copied hash. Building/publishing native is
separate from Gradle APK creation and computer-use installation/testing.

The first full link exposed `FlushVolCalc`'s dependency on global `RGB2HSV`,
normally supplied by a wxWidgets source file. `color_space_headless.cpp` contains
exactly the pinned upstream function, isolated from the unrelated `wxColour`
helpers. Its function body was compared to the pinned source and matched after
normalizing line endings; epsilon and negative-hue behavior are unchanged.

## STEP import contract

`prepareStep(source, resources, data, job, linearMm, angularRad, split)` is the
JNI-facing contract. The common JNI engine lock and private-new-job checks apply
before calling `convert_step_to_3mf`. The helper uses the desktop's implemented
`Model::read_from_step`, which calls OCCT `Step::load` / `Step::mesh`; the old
`load_step` function is merely declared in this pinned revision.

- Defaults are upstream's 0.003 mm linear and 0.5 rad angular deflection.
- Linear tolerance must be finite and positive; angular tolerance must be finite,
  greater than zero and at most pi radians. No arbitrary input file-size cap is
  introduced.
- The importer retains OCCT units, assembly transforms, object/volume boundaries
  and names. It validates finite vertices/bounds and triangle indices.
- A geometry-only 3MF is exported by upstream `store_3mf`, with no invented print
  configuration. A partial file is renamed only after a successful nonempty
  export. Existing output/partial paths are rejected, never replaced/deleted.
- The original STEP must remain a separate immutable source in the Java model
  snapshot. The generated 3MF is a derived tessellation used for preview and
  subsequent slicing, so changing the preference cannot silently retessellate a
  saved model.
- The returned `kepler.step-import.v1` JSON records applied tolerances/split mode,
  real triangle/object/volume/instance counts and bounds. It is descriptive
  metadata, not substitute mesh data.

`tools/test_step_import_options.ps1` passed 16 host guard checks. All three new
native helper objects compiled with the actual NDK/API26, upstream and OCCT
headers, `-O3` and `-Werror`. These results do not prove successful OCCT execution
on Android. The two real import/tolerance checks and source preservation checks
are listed with pinned fixtures in
[tools/tests/fixtures/step/README.md](../tools/tests/fixtures/step/README.md).
The final device/build evidence must record the final JNI/APK hashes separately.

`StepImportPreferences` is the Java consumer. It retains an atomic options
sidecar bound to the SHA-256/length of the original STEP, requires it on restore,
and derives into `cache/step-engine/data`. Cached geometry is bound to installed
engine identity, original bytes and fixed options; its digest and native
tessellation provenance are verified before reuse. Original hashing is streamed.
The STEP settings sheet uses the existing Glass controls, numeric validation,
EN/RU resources and explicit cancel callbacks. `show_step_import_settings`
controls that sheet; global preference changes do not retessellate existing
models whose saved sidecars preserve their earlier options.

`tools/test_step_import_java.ps1` passed 50 host checks for typed options, STEP/STP
filename routing, source/sidecar identity, corruption, interruption and saved
state, including the frozen print-snapshot hash/provenance binding. Android UI, resources and OCCT operations are explicit unavailable stubs
in that suite, so these results are not native import/cache execution proof.

## First staged extension build (2026-11-02)

The full JNI target linked successfully with `--no-undefined` and all 18 JNI
exports. The initial staged copy is 65,615,656 bytes, SHA-256
`DEB8EBC84284114ABF32C05621E93EB7A795BF70C3AA4065496E17957038A7C8`.
The repeat build reported `ninja: no work to do` and produced the same hash.
This is a staging result, not an APK/device acceptance result; later native
fixes may supersede it. `build-native.ps1` records hashes of all bridge sources
and refuses to stage if any changed during compilation.

The shared-library dependencies are only Android OS libraries: `libz.so`,
`libc.so`, `liblog.so`, `libm.so`, and `libdl.so`. No new external shared library
must be added to the APK. The packaged baseline at this staging point remains
`28EBDE4D17FFA8D5EAF53C244C49DC9B3DEC46C02068FCDE0D7479EC0F4B18E3`
(64,180,952 bytes); a byte-identical backup is in
`outputs/native-baseline-28EBDE4D/` beside the project.

## Integrated native publication (2026-11-02)

After adding the final project material-edit/compatibility contracts and all
scoped metadata, the native target was rebuilt and published into the Android
project: **65,639,160 bytes**, SHA-256
`B1410270D5D5AAEF0B12DF0C0A07178C17F16569754BDA827078B0B98AA18960`.
All **19** declared JNI methods match exported ELF symbols. The native source
fingerprint guard rejected one earlier build when a source changed during
compilation; only the subsequent frozen-source build was published.

The original library was preserved by hash under
`outputs/Kepler-Native-Phase2-baseline/28EBDE4D17FFA8D5EAF53C244C49DC9B3DEC46C02068FCDE0D7479EC0F4B18E3/`.
Exact bridge source hashes and build provenance are in the configured build's
`kepler-stage/native-build.json`; this compile/link/publication result remains
separate from APK installation and actual Android native self-test/STEP import.

## STEP print-placement correction and project self-tests

The initial Android CU run (APK 0664 / JNI B141…) passed 32 native control
checks and imported the cone STEP (72.8×72.8×25 mm), but slicing rejected its CAD
coordinates as outside the printer bed. The correction keeps the immutable CAD
source/tessellation unchanged. A frozen STEP print snapshot now includes the
original `model.step`/`model.stp`, derived `model.3mf`, and `step-import.json`
binding their hashes, sizes and exact saved/native meshing options.

Internal slice mode 3 is used only for this verified derivative; it is not a
persisted 3MF import mode. Native code checks its proof and existing geometry
import guards, then places the one real CAD object (including all its volumes)
with `apply_model_transform` at the selected printer's actual bed center and on
the bed. Saved offsets/rotation/scale use the same route as STL. The STEP
preparation viewport uses that same centered preview policy. Explicit 3MF
projects and arranged clones retain their original placement policy.

`project_settings_tests.cpp` is included explicitly in the reproducible target.
It adds 20 real native project writer/loader/config checks to the 32 control
checks. The combined executable JNI self-test therefore contains 52 checks;
compilation is not reported as their runtime success.

The build including these tests and the STEP placement correction is published
at **65,680,088 bytes**, SHA-256
`555A83600CFCE77494B2CD0FC10CFE5413F95AC179E713757B649CD904F7A52E`.
All 19 declared JNI methods and 22 bridge source fingerprints were verified;
`git apply --check` for the current headless patch passed. Device evidence for
this newer build is recorded in the final application verification report.

## Project validation regression follow-up

The combined self-test on JNI 555A… encountered a native crash during its
project snapshot. Symbolizing the unstripped build mapped the reported offsets
to `DynamicConfig::opt_enum<PrinterTechnology>` / `validate_project` / `snapshot`.
Project validation now checks the option before access and creates the required
generic enum in a detached validation copy before upstream's unchecked cast.
The project test also uses safe getters, canonical vectors and two-volume
fixtures that survive the real 3MF importer.

The rebuilt/published candidate is **65,685,216 bytes**, SHA-256
`DA652B11D77CFAFD7595E0D1DC5CE3499EF2FF7D6FFB4DD7D9453C65D4BA38A3`.
Compilation, linkage, 19 JNI exports and source-stability checks passed. Runtime
acceptance of this follow-up is recorded separately after the next CU run; the
earlier 52-check crash is not counted as a successful test.
