#pragma once

#include <string>
#include <vector>

namespace Slic3r { class DynamicPrintConfig; }

namespace kepler {
// Unverified experimental bridge, not linked into the APK.
// Call while holding the same process-wide lock as the slicer and after setting
// upstream's resource/data directories. Inputs identify exact upstream system
// presets and their vendor manifests, not arbitrary user profile JSON files.
struct SystemProfileSelection {
    std::string profiles_root;
    std::string printer_vendor, process_vendor, filament_vendor;
    std::string printer_name, process_name, filament_name;
};

struct ProfileCompatibility {
    std::string vendor, name, status;
};
// height, then x/y coordinate pairs in millimetres; vendor and name must
// identify an exact system printer preset.
std::vector<double> printer_build_volume(const std::string &profiles_root,
                                         const std::string &vendor, const std::string &name);

// Caller holds the engine mutex and has set resource/data directories. The
// returned list covers only vendors present in data/system; caller must label
// that scope. "unknown" means Orca's condition could not be evaluated.
std::vector<ProfileCompatibility> list_system_compatibility(
    const std::string &profiles_root, const std::string &printer_vendor,
    const std::string &printer_name, const std::string &kind,
    const std::string &process_vendor, const std::string &process_name);

// Resolves exact system presets in memory; caller holds the process-wide engine lock.
Slic3r::DynamicPrintConfig resolve_system_profile_config(const SystemProfileSelection &selection);
// Same exact profile resolution, with edits applied and compatibility rechecked
// against the original three presets while their owning bundle remains alive.
Slic3r::DynamicPrintConfig resolve_system_profile_config_with_overrides(const SystemProfileSelection &selection,
                                                                       const std::string &overrides_json);

// Writes a new, resolved INI for ExperimentalSlicer.sliceStl. Throws on any
// error; does not overwrite an existing destination. One FFF extruder only.
void write_system_profile_ini(const SystemProfileSelection &selection,
                              const std::string &output_ini);
}
