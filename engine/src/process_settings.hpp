#pragma once
#include <string>

namespace Slic3r { class DynamicPrintConfig; class Preset; }
namespace kepler {
// Caller resolves exact compatible system presets under the engine mutex first.
// Values and definitions come from that same in-memory full configuration.
std::string process_settings_metadata_json(const Slic3r::DynamicPrintConfig &resolved);
// Throws on unknown/duplicate keys, wrong JSON types, invalid options or invalid
// resulting config. The original config is never modified on error or success.
Slic3r::DynamicPrintConfig apply_process_overrides(const Slic3r::DynamicPrintConfig &resolved,
                                                  const std::string &overrides_json);
// In addition, check the exact printer/process/filament context after changes.
// All preset objects (including their vendor pointers) must remain alive.
Slic3r::DynamicPrintConfig apply_process_overrides_checked(const Slic3r::DynamicPrintConfig &resolved,
    const std::string &overrides_json, const Slic3r::Preset &printer,
    const Slic3r::Preset &process, const Slic3r::Preset &filament);
}
