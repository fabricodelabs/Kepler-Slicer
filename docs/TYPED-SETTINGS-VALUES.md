# Resolved typed settings — Phase 2

The existing JNI `resolvedSettingsSnapshot` already returns each resolved option's
direct native value alongside its serialized preset representation. Previously,
the Settings consumer used older Process metadata and stream-formatted INI for
Filament/Printer. Numeric values in those serialized representations can round;
unresolved values could fall through to clearly marked definition defaults.

`NativeConfigValues` now uses the existing typed API for all three schema kinds.
`NativeTypedConfigSnapshot` validates the response and converts direct values to
the editor's existing Orca string/string-array representation. It does not feed
raw typed objects or numeric arrays into the editor. Examples:

| Native data | Editor value |
| --- | --- |
| float `0.234567891234567` | `"0.234567891234567"` |
| percent `15.12345678912345` | `"15.12345678912345%"` |
| float-or-percent `{value:120.23456789123456,percent:true}` | `"120.23456789123456%"` |
| scalar point `[1.5,2.5]` / point3 `[1,2,3]` | `"1.5,2.5"` / `"1,2,3"` |
| point vector `[[0,0],[200,0]]` | `["0x0","200x0"]` |
| point groups `[[[0,0],[2,2]],[]]` | `["0x0,2x2",""]` |
| nullable bool vector `[true,false,null]` | `["1","0","nil"]` |

Integer groups use one comma-separated string per group, matching upstream
`ConfigOptionIntsGroups::vserialize`; string values retain decoded Unicode,
newlines and escapes. Numeric strings are derived from direct JSON numbers,
without the native stream serialization's precision loss. The resulting values
have `Source.RESOLVED_TYPED`. Missing native options may still have explicit
`DEFINITION_DEFAULT` provenance; only a returned native value is labelled typed.

## Current fixed ABI and validation

The pinned native API returns exactly `{options,preset_values}`. It has no version
or scope field, and this change does not invent one or change native code. A
future shape/version must be explicitly supported, rather than silently accepted.
Each option contains exactly `type`, `nullable`, `serialized`, `value` and
`preset_value`. The decoder checks both key sets, typed shapes, native integer
ranges, finite/representable numbers, bool/string types, nullable entries,
float-or-percent flags, point dimensions and vector/group members. It checks
known selected-schema types/nullability and exposes only that schema section.
Unknown options are validated, then omitted from the selected editor section.

`preset_value` must agree with the same key in `preset_values`, and its scalar or
flat string-array shape must match the native option kind. Its numeric contents
are deliberately not used for precision: pinned `ConfigBase::save_to_json`
delegates to `ConfigOptionFloat::serialize`, whose `ostringstream` can round.
The parser therefore validates the wire echo without substituting it for direct
values. UTF-8 and JSON syntax are checked strictly, including duplicate keys,
trailing data, Unicode escapes and paired surrogates; Android org.json's lenient
object parsing is not used at this boundary.

## Freshness and fallback

Profiles, saved overrides and model selection are captured before native work and
checked again afterward. A changed profile identity, overrides, model/plate/mode
or cancellation rejects publication. The existing profile resource snapshot is
used throughout. Returned data is detached, and values are not persisted merely
because they were read for display.

Preset editors also work in an empty workspace. `CurrentModelStore.settingsSelectionKey`
reads under the existing model publication lock and returns an explicit empty
token only for a validated empty selection. A model-required result fingerprint
still uses the existing strict `selectionKey`. Corrupt saved metadata, a missing
nonempty model file, or orphaned project/plate/material settings remain errors;
no caught IOException is converted into an empty token. Opening a model or
project during a typed read changes this token and rejects the pending result.

Only `UnsatisfiedLinkError` for an older missing typed JNI entry point permits the
old metadata/INI display path. That result reports API unavailability; its existing
`PROCESS_METADATA_SERIALIZED`, `VALIDATED_INI` and definition provenance remain.
A malformed/null available typed response, schema mismatch or native rejection
does not invoke an older decoder or claim exact defaults. Normal Settings error
handling remains responsible for displaying the failure.

Full-project mode stays on the separate project snapshot path and remains
read-only. It now also rejects changed captured profiles/overrides after native
work. Project errors never fall through to selected-preset metadata/INI/typed
defaults, and its immutable source copy is cleaned up on every checked outcome.
The project snapshot's current wire-value display contract is otherwise unchanged;
this increment does not add editable project drafts, scope mapping or export.

## Verification

Commands use Android Studio JBR and the existing workspace `org.json` jar:

```
tools/test_native_config_values.ps1 -JsonJar <workspace>/work/process-settings-test/json.jar
tools/test_project_settings.ps1 -JsonJar <workspace>/work/process-settings-test/json.jar
tools/test_general_preferences.ps1 -JsonJar <workspace>/work/process-settings-test/json.jar
```

The first command runs 72 strict parser/conversion checks and 78 consumer checks.
The separate project snapshot suite passes 52 checks. The general suite passes
82 backup/project-policy assertions and 70 actual CurrentModelStore assertions
(geometry/JNI stubbed), including the empty-selection helper and metadata errors.
Coverage includes rounded
wire versus high-precision direct numbers; all supported native value kinds;
Unicode/escapes/nullables; malformed syntax/types/key sets; schema filtering;
stale profiles, overrides and model selection; empty workspace for all three
editor sections; empty-to-model/project transitions; cancellation; legacy approximate
fallback; and project failure/temporary-file cleanup.

These are host checks against real Java production code with explicit JNI and
Android stubs. They do not execute JNI or prove the current native payload for all
profiles. APK compilation, actual JNI execution and computer-use visual checks
remain the integrator's acceptance work. No native library or ABI, UI files,
printer/cloud behavior or later product phase was changed here.
