#pragma once

#include <string>
#include "libslic3r/Model.hpp"
#include "libslic3r/PrintConfig.hpp"

namespace kepler {

// Detached native objects. No Preset/Vendor/PlateData pointer survives the
// importer that produced them. Destruction, like creation, requires the common
// process-wide engine mutex held by the JNI caller.
struct PreparedProject {
    Slic3r::Model model;
    Slic3r::DynamicPrintConfig config;
    std::string snapshot_json;
    int selected_plate = -1;
};

// Authoritative typed native values plus upstream's own JSON-preset representation.
// This is a settings snapshot, never a replacement for the original 3MF bytes.
std::string typed_config_snapshot_json(const Slic3r::ConfigBase &config);

// path must be an immutable app-private original 3MF. Both calls reopen it via
// Upstream and validate all parsed settings before returning. Snapshot reports a
// validation error as data so the UI can explain unavailable import choices.
std::string project_settings_snapshot_json(const std::string &path, int plate_index,
    const Slic3r::DynamicPrintConfig &selected, const std::string &material_edits_json = "{}");

// Full project settings -> plate settings -> model/volume/layer configs are
// retained in their original scopes. false restores the selected printer scope;
// true adopts the embedded printer after validation. Neither mode imports or
// mutates the user's preset repository. Throws without changing caller state.
PreparedProject prepare_project_settings(const std::string &path, int plate_index,
    const Slic3r::DynamicPrintConfig &selected, bool use_embedded_printer);

// Explicit geometry-only choice. The original archive is retained; only the
// detached working copy loses configuration, paint and layer-profile scopes.
// Unsafe units/plate origins still fail rather than falling back silently.
PreparedProject prepare_project_geometry(const std::string &path, int plate_index,
    const Slic3r::DynamicPrintConfig &selected);

}
