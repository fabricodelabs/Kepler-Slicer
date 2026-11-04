#pragma once

#include <cstddef>
#include <string>
#include <vector>
#include "libslic3r/Model.hpp"
#include "libslic3r/PrintConfig.hpp"

namespace kepler {

// These functions do not touch app state or files. The JNI caller holds the
// shared engine mutex and commits the returned detached candidate only after
// its input/session fingerprint has been checked again.
// changed_slot == -1 recalculates all physical rows/columns. extruder == -1
// recalculates every physical nozzle. Otherwise only that row/column/nozzle
// changes; manual values elsewhere (including the diagonal) are preserved.
// physical_colours may contain multiple colours for one physical spool. Empty
// uses filament_colour. virtual_mixed_slots identifies nonphysical mixer slots
// and leaves their rows/columns untouched, as in desktop Sidebar. If omitted,
// filament_is_mixed in the native config supplies the same per-index flags.
Slic3r::DynamicPrintConfig recalculate_flushing(
    const Slic3r::DynamicPrintConfig &config, int changed_slot, int extruder = -1,
    const std::vector<std::vector<std::string>> &physical_colours = {},
    const std::vector<bool> &virtual_mixed_slots = {});

// Strict saved per-project overlay: {} or exactly {colours:[CSS hex strings],
// flush_volumes:[unmultiplied numbers]}. Vectors must match the real native
// project material/nozzle dimensions. Does not recalculate manual matrix cells.
Slic3r::DynamicPrintConfig apply_material_edits(
    const Slic3r::DynamicPrintConfig &config, const std::string &edits_json);

// A versioned, detached description of the real config used by the methods
// above, not UI defaults. Includes slot vectors, matrix and native compatibility.
std::string material_snapshot_json(const Slic3r::DynamicPrintConfig &config);

// Returns a detached copy with fresh IDs for the new instances. Clones initially
// receive desktop's diagonal offset (5% of the maximum XY bed dimension per
// copy) before optional arrangement. Existing object
// and instance IDs remain identical to the input session. All printable
// instances of the current plate are arranged when enabled. No new plate is
// created: inability to fit every instance fails without changing the source.
// Bed exclusions use the upstream resolved regions; unresolved nozzle mapping
// is conservative and checks all nozzle exclusions.
Slic3r::Model clone_and_arrange(const Slic3r::Model &source,
    const Slic3r::DynamicPrintConfig &config, std::size_t object_index,
    std::size_t instance_index, std::size_t copies, bool auto_arrange);
std::string model_placements_json(const Slic3r::Model &model);

// Uses the real upstream Boost filter. The returned value is the actual level:
// upstream prerelease builds may enforce a more verbose minimum.
std::string set_native_log_level(const std::string &level);
std::string native_log_level();

// Meaningful in-process native smoke checks: original FlushVolCalculator,
// matrix preservation/rollback, material compatibility, real instance cloning,
// upstream arrangement, collision/bed-exclusion failure and log restoration.
// Caller must initialize upstream resources/data and hold the engine mutex.
std::string native_preferences_self_test();

}
