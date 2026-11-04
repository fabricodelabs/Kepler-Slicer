# Native Preferences control contracts

Implemented in `src/native_preferences.hpp/.cpp` against upstream commit
`6d2869c430284714f0a22570c38d3b359965a802`. The owner approved this extension on
2026-11-02; it does not open later UI phases or supply a Bambu provider.

All entry points run under the existing JNI engine mutex. Config/model helpers
return detached candidates. They do not publish Java selection or overwrite
source files. Android publishes a validated derived model only after checking
its frozen model/placement/plate and profile/override identities again.

## Flushing and material compatibility

`recalculate_flushing` uses the actual `FlushVolCalculator`, the native
`get_flush_volumes_matrix` / `set_flush_volumes_matrix` layout and the minimum
cutter-path volume arithmetic from desktop `GUI/Plater.cpp:1251`. It supports
multiple nozzles, several colours in one physical spool, support material and
virtual mixed slots. Without explicit mixed flags it uses `filament_is_mixed`
with the same per-index bounds rule as `PresetBundle::is_mixed_filament`.

A changed slot updates only that row and column in the selected nozzle matrix.
Other manual values and the diagonal stay unchanged. `changed_slot == -1` means
all physical pairs; `extruder == -1` means all physical nozzles. The stored
`flush_multiplier` is not multiplied into the matrix: Upstream applies it later.

Colours are `#RRGGBB` or explicitly CSS `#RRGGBBAA`. Matrix dimensions, finite
values, nozzle/material counts and cutter-path values are validated before a
candidate is returned. Missing prediction data fails before the upstream
process-wide `GenericFlushPredictor` cache can initialize with an empty file.
The producer must prepare all three original `resources/flush/*.txt` datasets
before invoking this helper. They are already in `upstream_resources.zip`.

One intentional deviation from the pinned GUI is the per-slot outgoing-to-support
branch: the GUI computes its 230 mm³ value but writes the matrix cell only in its
`else` block. This helper writes the support constant for every affected ordered
pair. A complete desktop all-slot recalculation produces the same rule; a
single-slot update can differ at this buggy branch. This is not G-code parity
evidence.

`material_snapshot_json` reports actual slot values, support/mixed flags, nozzle
count, minimum flushing volumes and native compatibility from
`Print::check_multi_filaments_compatibility`. The JNI slicing pipeline owns
`Print::set_check_multi_filaments_compatibility`. The Android preference is off
by default and shows a warning before enabling mixed temperatures. Invalid
temperature ranges should remain errors independently of allowing the mixed
range result.

`apply_material_edits` accepts an empty overlay or exactly the `colours` and
`flush_volumes` vectors. It rejects duplicate/unknown keys, mismatched slot/nozzle
dimensions, non-hex colours, nonnumeric/nonfinite/out-of-range volumes. It changes
only those two native options in a detached candidate. `ProjectMaterialEditor`
exposes the actual slots of a loaded configured 3MF (modes 1/2), edits physical
slot colours and requests the real recalculation only when a colour changed and
the preference is enabled. Virtual mixed slots are read-only in this limited UI.
An unchanged colour Save is a no-op, preserving manual matrix cells. Frozen
source/profile/preference guards run again before the overlay is committed.
The original archive is never rewritten. Saved overlays participate in model
fingerprints and are included in the original-model backup with its placement,
plate and project mode.

The current material UI changes physical slot colours in an already configured
3MF. It does not create a new multi-material project, assign another filament
preset to a slot, change AMS mappings or re-run flushing for every external
printer/material event. Those broader desktop triggers remain outside this
limited consumer. Monochrome touch presets supplement the HEX editor; each
preset has a 48 dp target, localized colour/HEX label and selected state.

## Clone and arrange

`clone_and_arrange` copies the model with existing native IDs preserved, then
allocates fresh IDs for new instances. Initial clone offsets follow desktop
`Plater::increase_instances`: each clone moves diagonally by another 5% of the
maximum XY bed dimension. If auto-arrange is off, these placements are retained;
this does not promise that every instance is inside the bed.

With auto-arrange enabled, the helper uses the actual `get_instance_arrange_poly`,
`ArrangeParams` inflation/shrunken-bed helpers and `arrangement::arrange`.
Resolved legacy and explicit bed exclusions are supplied as native blockers.
An unresolved physical-nozzle mapping conservatively applies all nozzle
exclusions. Every printable instance must fit the current plate, actual bed
polygon and build height; overlap or a spill onto a virtual plate fails without
publishing the candidate. No centering loop replaces Arrange.

This limited model contract has no full belt/IMEX plate session, wipe-tower GUI
session or multi-plate registry. Belt auto-arrangement fails explicitly. No
desktop-equivalence claim is made for those sessions. The initial Android
consumer only clones a single plain STL/OBJ object into a derived geometry 3MF.
It rejects original embedded 3MF projects until their complete project-session
export is available. The original configuration must never be silently stripped
to make that operation pass.

`NativePreferenceActions.clonePlainModel` copies the original and its transform
under the model publication lock, uses a frozen `EngineProfileSession`, validates
the result path as a direct child of its private native job, and performs final
publication in process→model lock order. It cleans only its own temporary input
and native job. Model Details exposes 1, 2 or 3 new copies; `arrange_on_clone`
controls this real operation and defaults to true.

## Native logging and verification

`set_native_log_level` invokes the actual upstream Boost severity setter and
returns `get_logging_level`. The UI stores the requested Java level and actual
native level separately: prerelease upstream may force a more verbose native minimum.
The setter does not create a log file or upload diagnostics.

The native self-test executes **32 assertions** against real library functions:
directional flushing, support rules, preserved manual/diagonal/nozzle cells,
mixed-slot metadata, invalid slot/colour rejection, source rollback, material
compatibility, clone identities/desktop offset, actual Arrange, exclusion and
no-fit checks, placement serialization and severity setter/restoration. It
returns its actual count and result as JSON; a failed assertion throws with its
name. Native compilation alone does not prove those assertions ran. The final
Android verification report must record execution, JNI/APK hashes and observed
results separately.

The Android developer action validates the returned count and every passed
check, saves the exact native JSON atomically to the private
`files/slicer-engine/native-preferences-checks.json`, and displays the count and
individual check names on separate lines. This is a local diagnostic artifact;
the action does not upload it.

## Scope and remaining provider boundaries

The authorized JNI extension is not a Bambu network plug-in. It does not add
AMS/device state, cloud authentication, regional Bambu endpoints, abnormal
storage bypass or provider reload/version lifecycle. No physical printer or
real cloud-account verification is claimed by this component document.

Host General tests execute the production archive, import-decision and
CurrentModelStore publication code with explicit geometry/STEP/JNI fakes. They
cover nested object/volume/layer/adaptive-profile detection; STEP source/options
preservation; project-mode and material-overlay fingerprints; stale operations;
and atomic model/material rollback. Archive restore rejects full-project mode
for non-3MF sources, nonempty material overlays outside full 3MF modes, malformed
material metadata and noninteger loading modes. Those policy checks cannot prove
native geometry, desktop G-code parity or physical print suitability.

Restoring a nonempty material overlay additionally uses the same strict
colours/matrix schema as the native editor and preflights it against the actual
frozen 3MF via `projectMaterialSettings`. The restored colours, matrix and slot
counts must match the native result. A usable printer/process/filament selection
is required for that validation; an unavailable profile context fails restore
without publishing unchecked settings. The final commit checks the frozen
profile context in process→model lock order, alongside the existing workspace
guard. Host tests use real structural/publication code and an explicit native
preflight fake to prove that native failure or late profile/workspace changes
preserve the previous usable model.

The STEP slice snapshot reserves mode 3 for the transient, verified derivative
only; persisted project modes still reject that value. The host model-store test
checks that both the frozen original CAD and the 3MF copy remain in the job and
that CMS calls the provenance helper with the exact import-time options. Its
STEP stub records that request; it does not claim to perform OCCT or native
provenance validation itself.
