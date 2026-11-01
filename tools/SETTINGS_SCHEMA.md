# Static Orca settings inventory

The generator reads the local upstream checkout without running or modifying Orca. It writes `app/src/main/assets/orca_settings_schema.json`:

```powershell
python tools/build_settings_schema.py --orca ../../work/OrcaSlicer
python tools/check_settings_coverage.py --orca ../../work/OrcaSlicer
```

Paths above are relative to `outputs/OrcaMobile`; alternatively pass absolute `--orca`, `--output` and `--schema` paths. The generated file identifies the expected upstream revision, explicitly marks revision verification as not performed, and records SHA-256 of the actual source bytes. It is deterministic for the same inputs.

The same run also generates the compact `app/src/main/assets/orca_settings_runtime.json` for `SettingsSchema.load()`. It includes all section entries and UI metadata, but omits source proofs: consult the detailed file for provenance. `SettingsSchema.uiInventory()` identifies itself as a model inventory; it does not prove that the Android UI rendered or saved those keys. Missing pages/modes are presented under Other options/Expert with `pageKnown`/`modeKnown` false. Default nested values are immutable; `defaultJsonValue()` makes a fresh JSON copy.

Scalar numeric and boolean inputs receive local syntax, finite-number, literal min/max and known-enum checks. Vector inputs use strict structured JSON, preserve array members and are not fully validated against Orca-specific vector, nullable, cross-option or printer constraints. Unknown definitions/defaults stay unknown. Applying the source engine's own validation is still required; do not equate the static Android checks with full Orca validation.

For reproducible host checks, run `tools/test_native_config_values.ps1 -JsonJar <org.json.jar>` and `python tools/tests/test_settings_coverage.py --orca <pinned-OrcaSlicer-checkout>`. The first compiles the real Java normalization class with explicit Android/JNI stubs: it validates source labels, conversion and cleanup but does not execute the native engine. The second checks stale-audit rejection and missing UI-key reporting; a passing test confirms those checks work, not that the coverage audit is complete.

## Contents

`sections.process`, `sections.filament`, and `sections.printer` contain literal references from the selected Tab builder methods followed by additional keys from upstream `Preset.cpp` and the printer extruder-key list. A definition may appear in more than one section. Presence in a preset list does not prove that a row is visible, editable, or applicable to the selected printer. Entries with no literal Tab placement have `page` and `group` set to null; the mobile presentation may group those as Other options but must not misrepresent that as an upstream Tab placement.

Each option contains:

- `key`, `base_key`, `index`, source labels, tooltip, raw Orca `co...` type, unit, literal min/max and `mode`. For a single resolved definition with no explicit mode assignment, `mode` comes from the `ConfigOptionDef` initializer in `Config.hpp`; an unconditional `comSimple`, `comAdvanced`, `comExpert`, or `comDevelop` assignment overrides it. Conditional, ambiguous and missing cases stay null. `mode_source` records where the value came from. Tab-specific mode overrides and runtime visibility still require separate review.
- `default` with `value`, unmodified `expression`, and `resolved`; unresolved never means zero, empty or absent. The current preset can override any default.
- `placements[]` in source order, each with method, line, page/group expressions, source condition snippets, preprocessor context, and `base_key`, numeric `index`, `index_expression`, `option_id`. A dynamic index stays unknown. `get_option` is a reference, not proof of attachment to a visible row.
- `preset_membership[]` with FFF/SLA/machine/nozzle provenance; `definition_candidates[]` retains duplicate candidates, field assignment expressions, enum sources and completion flags. `resolution.fields` distinguishes literal, unresolved, missing or ambiguous fields.
- `ui_accessibility.status` remains `unknown` until the current app independently supplies evidence that it actually displays and can use the entry.

`unmapped_definitions[]` retains definitions not assigned by the extracted Tab or preset lists. `diagnostics.dynamic_references` and `dynamic_definitions` include expression, source line and context. No entry may be described as desktop-equivalent merely because it appears here.

The extractor deliberately does **not** generally evaluate C++, global constants, macros, upstream enum maps, vector-index generation, active conditional branches, printer technology, helper methods outside the declared builder set, dynamically generated controls, GUI attachment, translation or profile inheritance. The 37 reviewed dynamic preset definitions are an explicit, source-hash-bound exception: the 19 filament overrides copy verified base definition fields and are marked nullable; unproven inherited defaults remain unknown. Their evidence and the remaining scope review are in `settings_dynamic_audit.json` and `SETTINGS_DYNAMIC_AUDIT.md`. Page/group assignment follows literal builder calls lexically; complex branches can need manual review. Runtime config may differ from default definitions. Malformed/unavailable fields remain unknown and must not be converted into guessed values.

## Coverage checker

`check_settings_coverage.py` uses a separate scanner and compares section/key and exact source callsite against the JSON. `get_option` references and direct calls are reported separately; neither proves runtime visibility. The checker prints dynamic calls explicitly and compares the source file SHA-256 with the schema. `--json` outputs the full report. To compare a **separately exported** list of currently UI-accessible keys, pass `--ui-keys <file>` containing `{ "sections": { "process": ["key", ...], "filament": [...], "printer": [...] } }`. Such a declaration alone still does not prove that editing, persistence, compatibility or G-code application works.

The checker reconciles dynamic calls and definitions against the hash-bound manual audit and scans extra object, plate and SLA builders separately. Use `--json` for the machine-readable report. The 57 additional PrintConfig definitions have documented static usage or bounded absence findings, with unresolved behavior and Android implementation gaps kept explicit. Zero unexplained within that reviewed set does not prove full coverage: other helpers, object/plate application and runtime UI attachment remain open. The checker exits 1 for an incomplete audit even when all literal callsites match; exit 2 means input/parse error. This intentionally does not claim the Phase 2 acceptance goal of zero unexplained omissions has been achieved.
