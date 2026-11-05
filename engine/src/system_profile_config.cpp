#include "system_profile_config.hpp"
#include "process_settings.hpp"

#include <filesystem>
#include <memory>
#include <set>
#include <stdexcept>
#include <vector>
#include <cmath>

#include "libslic3r/PlaceholderParser.hpp"
#include "libslic3r/Preset.hpp"
#include "libslic3r/PresetBundle.hpp"
#include "libslic3r/PrintConfig.hpp"
#include "libslic3r/Utils.hpp"

namespace kepler {
namespace {
using namespace Slic3r;
namespace fs = std::filesystem;

void require_vendor(const fs::path &root, const std::string &vendor)
{
    if (vendor.empty() || vendor == "." || vendor == ".." ||
        vendor.find('/') != std::string::npos || vendor.find('\\') != std::string::npos ||
        !fs::is_regular_file(root / (vendor + ".json")))
        throw std::invalid_argument("Vendor manifest not found or invalid: " + vendor);
}

Preset &exact_preset(PresetCollection &collection, const std::string &name)
{
    if (name.empty()) throw std::invalid_argument("All three preset names are required");
    // Use the stored preset even when it is selected; the default lookup can
    // return its edited copy, whose visibility is not read by strict selection.
    Preset *preset = collection.find_preset(name, false, true);
    if (!preset || preset->name != name || !preset->is_system || preset->is_default)
        throw std::runtime_error("Exact system preset not found: " + name);
    return *preset;
}

void select_exact(PresetCollection &collection, const std::string &name)
{
    // Explicit user selection of a system preset need not already be enabled in AppConfig.
    exact_preset(collection, name).is_visible = true;
    // The public method may fall back when a name is absent, so check the result.
    collection.select_preset_by_name(name, true);
    if (collection.get_selected_preset_name() != name)
        throw std::runtime_error("Could not select exact preset: " + name);
}

// The upstream compatibility helpers catch malformed conditions and return true.
// Pre-evaluate the conditions they would use so this experiment fails closed.
void check_condition_syntax(const Preset &preset, const Preset &printer, const Preset &process, bool check_print = true)
{
    const auto *printer_list = preset.config.option<ConfigOptionStrings>("compatible_printers");
    const auto &printer_condition = preset.compatible_printers_condition();
    if ((!printer_list || printer_list->values.empty()) && !printer_condition.empty()) {
        DynamicPrintConfig extra;
        extra.set_key_value("printer_preset", new ConfigOptionString(printer.name));
        const auto *nozzles = printer.config.option<ConfigOptionFloats>("nozzle_diameter");
        if (nozzles) extra.set_key_value("num_extruders", new ConfigOptionInt(static_cast<int>(nozzles->values.size())));
        PlaceholderParser::evaluate_boolean_expression(printer_condition, printer.config, &extra);
    }
    if (check_print && preset.type == Preset::TYPE_FILAMENT) {
        const auto *print_list = preset.config.option<ConfigOptionStrings>("compatible_prints");
        const auto &print_condition = preset.compatible_prints_condition();
        if ((!print_list || print_list->values.empty()) && !print_condition.empty())
            PlaceholderParser::evaluate_boolean_expression(print_condition, process.config);
    }
}

bool checked_printer_compatible(const Preset &preset, const Preset &printer)
{
    check_condition_syntax(preset, printer, preset, false);
    return is_compatible_with_printer(PresetWithVendorProfile(preset, preset.vendor),
                                      PresetWithVendorProfile(printer, printer.vendor));
}

bool checked_filament_compatible(const Preset &filament, const Preset &printer, const Preset &process)
{
    check_condition_syntax(filament, printer, process);
    const PresetWithVendorProfile f(filament, filament.vendor), p(printer, printer.vendor);
    return is_compatible_with_printer(f, p) &&
           is_compatible_with_print(f, PresetWithVendorProfile(process, process.vendor), p);
}

std::unique_ptr<PresetBundle> load_system_bundle(const fs::path &root)
{
    if (!fs::is_directory(root)) throw std::invalid_argument("Profiles root is not a directory");
    const fs::path data_root(Slic3r::data_dir());
    if (!fs::is_directory(data_root / "system") || fs::canonical(root) != fs::canonical(data_root / "system"))
        throw std::invalid_argument("Profiles must be in the private data directory's system folder");
    if (fs::exists(data_root / "user"))
        throw std::invalid_argument("System profile resolution requires a clean data directory without user presets");
    require_vendor(root, PresetBundle::ORCA_FILAMENT_LIBRARY);
    auto bundle = std::make_unique<PresetBundle>();
    AppConfig app_config;
    std::string errors;
    const auto substitutions = bundle->load_presets(app_config, ForwardCompatibilitySubstitutionRule::Disable,
                                                     PresetBundle::PresetPreferences(), &errors, true);
    if (!errors.empty() || bundle->error_count() != 0 || !substitutions.empty())
        throw std::runtime_error("System profile loading failed or required substitutions: " + errors);
    return bundle;
}
}

Slic3r::DynamicPrintConfig resolve_system_profile_config_with_overrides(const SystemProfileSelection &s, const std::string &overrides_json)
{
    return resolve_multi_material_config(s, overrides_json, {});
}

Slic3r::DynamicPrintConfig resolve_multi_material_config(const SystemProfileSelection &s, const std::string &overrides_json,
                                                         const std::vector<MaterialSlot> &extra_slots)
{
    using namespace Slic3r;
    const fs::path root(s.profiles_root);
    const std::set<std::string> vendors{s.printer_vendor, s.process_vendor, s.filament_vendor};
    for (const auto &vendor : vendors) require_vendor(root, vendor);
    auto loaded = load_system_bundle(root);
    PresetBundle &bundle = *loaded;

    select_exact(bundle.printers, s.printer_name);
    select_exact(bundle.prints, s.process_name);
    select_exact(bundle.filaments, s.filament_name);
    const Preset &printer = bundle.printers.get_selected_preset();
    const Preset &process = bundle.prints.get_selected_preset();
    const Preset &filament = bundle.filaments.get_selected_preset();
    if (!printer.vendor || printer.vendor->id != s.printer_vendor ||
        !process.vendor || process.vendor->id != s.process_vendor ||
        !filament.vendor || filament.vendor->id != s.filament_vendor)
        throw std::runtime_error("Selected preset does not belong to the requested vendor");
    if (printer.printer_technology() != ptFFF || bundle.get_printer_extruder_count() != 1)
        throw std::runtime_error("Only single-extruder FFF profiles are supported by this experiment");

    if (!checked_printer_compatible(process, printer) || !checked_filament_compatible(filament, printer, process))
        throw std::runtime_error("Selected system presets are incompatible");

    if (extra_slots.size() > 15) throw std::invalid_argument("At most 16 material slots are supported");
    std::vector<std::string> names{filament.name};
    for (const auto &slot : extra_slots) {
        require_vendor(root, slot.vendor);
        Preset &extra = exact_preset(bundle.filaments, slot.name);
        if (!extra.vendor || extra.vendor->id != slot.vendor)
            throw std::runtime_error("Material slot preset does not belong to the requested vendor");
        if (!checked_filament_compatible(extra, printer, process))
            throw std::runtime_error("Material slot preset is incompatible with the printer or process: " + slot.name);
        names.push_back(extra.name);
    }
    const unsigned slots = static_cast<unsigned>(names.size());
    bundle.set_num_filaments(slots);
    if (bundle.filament_presets.size() != slots)
        throw std::runtime_error("Unexpected number of filament slots");
    for (unsigned i = 0; i < slots; ++i) bundle.filament_presets[i] = names[i];
    if (slots > 1) {
        bundle.update_multi_material_filament_presets();
        auto *colours = bundle.project_config.option<ConfigOptionStrings>("filament_colour");
        auto *multi = bundle.project_config.option<ConfigOptionStrings>("filament_multi_colour");
        if (!colours || colours->values.size() != slots) throw std::runtime_error("Material slot colours were not resized");
        for (unsigned i = 1; i < slots; ++i) if (!extra_slots[i - 1].colour.empty()) colours->values[i] = extra_slots[i - 1].colour;
        if (multi) { multi->values.resize(slots); for (unsigned i = 0; i < slots; ++i) multi->values[i] = colours->values[i]; }
    }
    // The desktop slicing path passes the unexpanded configuration to Print::apply,
    // which resolves extruder/filament variants itself (Plater.cpp).
    DynamicPrintConfig config = bundle.full_config(false);
    const auto errors = config.validate(true);
    if (!errors.empty()) throw std::runtime_error("Invalid resolved config: " + errors.begin()->first + ": " + errors.begin()->second);
    return kepler::apply_process_overrides_checked(config, overrides_json, printer, process, filament);
}

Slic3r::DynamicPrintConfig resolve_system_profile_config(const SystemProfileSelection &s)
{
    return resolve_system_profile_config_with_overrides(s, "{}");
}

std::vector<double> printer_build_volume(const std::string &profiles_root, const std::string &vendor, const std::string &name)
{
    using namespace Slic3r;
    const fs::path root(profiles_root); require_vendor(root, vendor);
    auto bundle = load_system_bundle(root);
    select_exact(bundle->printers, name);
    const Preset &printer = bundle->printers.get_selected_preset();
    if (!printer.vendor || printer.vendor->id != vendor || printer.printer_technology() != ptFFF)
        throw std::runtime_error("Expected exact FFF system printer preset");
    const auto *area = printer.config.opt<ConfigOptionPoints>("printable_area");
    const double height = printer.config.opt_float("printable_height");
    if (!area || area->values.size() < 3 || area->values.size() > 5000 || !std::isfinite(height) || height <= 0)
        throw std::runtime_error("Printer has an invalid build volume");
    std::vector<double> result; result.reserve(1 + 2 * area->values.size()); result.push_back(height);
    for (const auto &point : area->values) {
        if (!std::isfinite(point.x()) || !std::isfinite(point.y())) throw std::runtime_error("Printer bed contains invalid coordinates");
        result.push_back(point.x()); result.push_back(point.y());
    }
    return result;
}

std::vector<ProfileCompatibility> list_system_compatibility(
    const std::string &profiles_root, const std::string &printer_vendor,
    const std::string &printer_name, const std::string &kind,
    const std::string &process_vendor, const std::string &process_name)
{
    using namespace Slic3r;
    if (kind != "process" && kind != "filament") throw std::invalid_argument("Invalid profile type");
    const fs::path root(profiles_root);
    require_vendor(root, printer_vendor);
    if (!process_name.empty()) require_vendor(root, process_vendor);
    auto bundle = load_system_bundle(root);
    select_exact(bundle->printers, printer_name);
    const Preset &printer = bundle->printers.get_selected_preset();
    if (!printer.vendor || printer.vendor->id != printer_vendor || printer.printer_technology() != ptFFF ||
        bundle->get_printer_extruder_count() != 1)
        throw std::runtime_error("Only exact single-extruder FFF system printers are supported");
    const Preset *process = nullptr;
    if (!process_name.empty()) {
        select_exact(bundle->prints, process_name);
        process = &bundle->prints.get_selected_preset();
        if (!process->vendor || process->vendor->id != process_vendor)
            throw std::runtime_error("Selected process does not belong to the requested vendor");
        if (!checked_printer_compatible(*process, printer))
            throw std::runtime_error("Selected process is incompatible with the printer");
    }
    std::vector<ProfileCompatibility> result;
    const auto &presets = kind == "process" ? bundle->prints.get_presets() : bundle->filaments.get_presets();
    for (const Preset &candidate : presets) {
        if (!candidate.is_system || candidate.is_default || !candidate.vendor) continue;
        std::string status = "unknown";
        try {
            // In the absence of a selected process, printer compatibility alone
            // is insufficient to certify a filament's print compatibility.
            if (kind == "filament" && process)
                status = checked_filament_compatible(candidate, printer, *process) ? "yes" : "no";
            else if (!checked_printer_compatible(candidate, printer)) status = "no";
            else status = kind == "filament" ? "printer_only" : "yes";
        } catch (const std::exception &) { status = "unknown"; }
        result.push_back({candidate.vendor->id, candidate.name, std::move(status)});
    }
    return result;
}

void write_system_profile_ini(const SystemProfileSelection &s, const std::string &output_ini)
{
    using namespace Slic3r;
    const fs::path output(output_ini);
    if (output_ini.empty() || fs::exists(output)) throw std::invalid_argument("Output INI must be a new file");
    DynamicPrintConfig config = resolve_system_profile_config(s);
    config.save(output_ini);
    if (!fs::is_regular_file(output) || fs::file_size(output) == 0)
        throw std::runtime_error("Resolved configuration was not written");
    // ConfigBase::save() does not expose stream errors. Reload into an empty
    // config and compare the complete key/value sets before accepting the file.
    DynamicPrintConfig reloaded;
    const auto reload_substitutions = reloaded.load_from_ini(output_ini, ForwardCompatibilitySubstitutionRule::Disable);
    if (!reload_substitutions.empty() || !(reloaded == config))
        throw std::runtime_error("Resolved INI did not round-trip exactly; it must not be sliced");
}
}
