#include "process_settings.hpp"
#include "libslic3r/PrintConfig.hpp"
#include "libslic3r/Preset.hpp"
#include "libslic3r/PlaceholderParser.hpp"
#include "nlohmann/json.hpp"

#include <algorithm>
#include <cfloat>
#include <cmath>
#include <limits>
#include <set>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <vector>

namespace kepler {
namespace {
using json = nlohmann::json;
using namespace Slic3r;

struct TabGroup { const char *key, *page, *section; };
const TabGroup tab_groups[] = {
#include "tab_process_groups.inc"
};
const std::unordered_map<std::string, std::pair<std::string, std::string>> &groups() {
    static const auto map = [] {
        std::unordered_map<std::string, std::pair<std::string, std::string>> result;
        for (const auto &row : tab_groups) result.emplace(row.key, std::make_pair(row.page, row.section));
        return result;
    }();
    return map;
}

bool editable(const ConfigOptionDef &def) {
    if (!def.is_scalar() || def.nullable || def.readonly) return false;
    switch (def.type) {
    case coFloat: case coPercent: case coFloatOrPercent: case coInt: case coBool: return true;
    case coEnum: return def.enum_keys_map && (!def.enum_values.empty() || !def.enum_keys_map->empty());
    // Source-code, expression and multiline fields require a dedicated editor.
    case coString: return !def.is_code && !def.multiline && def.gui_type == ConfigOptionDef::GUIType::undefined && def.opt_key.find("condition") == std::string::npos;
    default: return false;
    }
}

const ConfigOptionDef &definition(const DynamicPrintConfig &config, const std::string &key) {
    const auto &process_keys = Preset::print_options();
    if (std::find(process_keys.begin(), process_keys.end(), key) == process_keys.end())
        throw std::invalid_argument("Unsupported process setting: " + key);
    const ConfigOptionDef *def = config.def()->get(key);
    const ConfigOption *value = config.option(key);
    if (!def || !value || value->type() != def->type || !editable(*def))
        throw std::runtime_error("Missing, read-only or unsupported process setting: " + key);
    return *def;
}

const char *type_name(ConfigOptionType type) {
    switch (type) {
    case coFloat: return "float"; case coPercent: return "percent";
    case coInt: return "integer"; case coBool: return "boolean";
    case coEnum: return "enum"; case coFloatOrPercent: return "float_or_percent";
    case coString: return "string"; default: return "readonly";
    }
}

json option_value(const ConfigOption *v, const ConfigOptionDef &def) {
    if (!v || v->type() != def.type) throw std::runtime_error("Invalid option value: " + def.opt_key);
    switch (def.type) {
    case coFloat: case coPercent: {
        const double number = static_cast<const ConfigOptionFloat *>(v)->value;
        if (!std::isfinite(number)) throw std::runtime_error("Non-finite option value: " + def.opt_key);
        return number;
    }
    case coInt: return static_cast<const ConfigOptionInt *>(v)->value;
    case coBool: return static_cast<const ConfigOptionBool *>(v)->value;
    case coFloatOrPercent: return v->serialize();
    case coString: return static_cast<const ConfigOptionString *>(v)->value;
    case coEnum: return v->serialize();
    default: return v->serialize();
    }
}

void check_numeric(const ConfigOptionDef &def, double value) {
    // Explicit range without ConfigOptionDef's tolerance, and reject NaN/Inf.
    if (!std::isfinite(value) || value < def.min || value > def.max)
        throw std::invalid_argument("Value outside range for " + def.opt_key);
}

// Orca's convenience predicates catch malformed conditions and return true.
// Pre-evaluate precisely the expressions they would use, failing closed on error.
void check_conditions(const Preset &preset, const Preset &printer, const Preset &process, bool check_print) {
    const auto *printers = preset.config.option<ConfigOptionStrings>("compatible_printers");
    const auto &printer_condition = preset.compatible_printers_condition();
    if ((!printers || printers->values.empty()) && !printer_condition.empty()) {
        DynamicPrintConfig extra;
        extra.set_key_value("printer_preset", new ConfigOptionString(printer.name));
        const auto *nozzles = printer.config.option<ConfigOptionFloats>("nozzle_diameter");
        if (nozzles) extra.set_key_value("num_extruders", new ConfigOptionInt(static_cast<int>(nozzles->values.size())));
        PlaceholderParser::evaluate_boolean_expression(printer_condition, printer.config, &extra);
    }
    if (check_print && preset.type == Preset::TYPE_FILAMENT) {
        const auto *prints = preset.config.option<ConfigOptionStrings>("compatible_prints");
        const auto &condition = preset.compatible_prints_condition();
        if ((!prints || prints->values.empty()) && !condition.empty())
            PlaceholderParser::evaluate_boolean_expression(condition, process.config);
    }
}
}

std::string process_settings_metadata_json(const Slic3r::DynamicPrintConfig &resolved)
{
    json out = {{"version", 2}, {"scope", "process_options"}, {"options", json::array()}};
    const auto &process_keys = Preset::print_options();
    const std::set<std::string> allowed(process_keys.begin(), process_keys.end());
    std::set<std::string> seen;
    std::vector<std::string> ordered;
    ordered.reserve(process_keys.size());
    // Preserve TabPrint::build page and subgroup order, then expose process
    // options not present as literal desktop rows at the end (Others).
    for (const auto &row : tab_groups)
        if (allowed.count(row.key) && seen.insert(row.key).second) ordered.emplace_back(row.key);
    for (const auto &key : process_keys)
        if (seen.insert(key).second) ordered.push_back(key);
    for (const auto &key : ordered) {
        const ConfigOptionDef *definition = resolved.def()->get(key);
        const ConfigOption *value = resolved.option(key);
        if (!definition || !value || value->type() != definition->type) continue;
        const auto &def = *definition;
        bool can_edit = editable(def);
        if (def.type == coEnum && can_edit && (def.enum_values.empty() ? !def.enum_keys_map->count(value->serialize()) : !def.has_enum_value(value->serialize()))) can_edit = false;
        auto group = groups().find(key);
        json row = {{"key", key}, {"type", type_name(def.type)}, {"label", def.label},
                    {"category", group == groups().end() ? "Others" : group->second.first},
                    {"section", group == groups().end() ? "Other settings" : group->second.second},
                    {"tooltip", def.tooltip}, {"unit", def.sidetext}, {"editable", can_edit},
                    {"mode", static_cast<int>(def.mode)}, {"value", option_value(value, def)},
                    {"default", def.default_value && def.default_value->type() == def.type ? option_value(def.default_value.get(), def) : option_value(value, def)},
                    {"min", nullptr}, {"max", nullptr}, {"max_literal", nullptr}, {"choices", json::array()}};
        if (def.min > -FLT_MAX) row["min"] = def.min;
        if (def.max < FLT_MAX) row["max"] = def.max;
        if (def.type == coFloatOrPercent && def.sidetext.find("mm ") != std::string::npos && std::isfinite(def.max_literal)) row["max_literal"] = def.max_literal;
        if (def.type == coEnum) {
            for (std::size_t i = 0; i < def.enum_values.size(); ++i)
                row["choices"].push_back({{"value", def.enum_values[i]},
                    {"label", i < def.enum_labels.size() ? def.enum_labels[i] : def.enum_values[i]}});
            if (def.enum_values.empty() && def.enum_keys_map)
                for (const auto &entry : *def.enum_keys_map) row["choices"].push_back({{"value", entry.first}, {"label", entry.first}});
        }
        out["options"].push_back(std::move(row));
    }
    return out.dump();
}

Slic3r::DynamicPrintConfig apply_process_overrides(const Slic3r::DynamicPrintConfig &resolved,
                                                  const std::string &overrides_json)
{
    if (overrides_json.size() > 65536) throw std::invalid_argument("Process override payload is too large");
    std::set<std::string> seen;
    auto callback = [&](int depth, json::parse_event_t event, json &value) {
        if (event == json::parse_event_t::key && depth == 1 && !seen.insert(value.get<std::string>()).second)
            throw std::invalid_argument("Duplicate process setting");
        return true;
    };
    json changes = json::parse(overrides_json, callback);
    if (!changes.is_object() || changes.size() > Preset::print_options().size()) throw std::invalid_argument("Expected a process settings object");
    DynamicPrintConfig candidate(resolved);
    for (auto it = changes.begin(); it != changes.end(); ++it) {
        const auto &def = definition(candidate, it.key());
        const json &value = it.value();
        switch (def.type) {
        case coFloat: case coPercent: {
            if (!value.is_number()) throw std::invalid_argument("Expected a numeric value for " + it.key());
            const double number = value.get<double>(); check_numeric(def, number);
            if (def.type == coFloat) candidate.option<ConfigOptionFloat>(it.key())->value = number;
            else candidate.option<ConfigOptionPercent>(it.key())->value = number;
            break;
        }
        case coInt: {
            if (!value.is_number_integer()) throw std::invalid_argument("Expected an integer for " + it.key());
            // Range-check as floating point before narrowing, including huge JSON unsigned integers.
            const double number = value.get<double>(); check_numeric(def, number);
            if (number < std::numeric_limits<int>::min() || number > std::numeric_limits<int>::max())
                throw std::invalid_argument("Integer out of range for " + it.key());
            candidate.option<ConfigOptionInt>(it.key())->value = static_cast<int>(number);
            break;
        }
        case coBool:
            if (!value.is_boolean()) throw std::invalid_argument("Expected a boolean for " + it.key());
            candidate.option<ConfigOptionBool>(it.key())->value = value.get<bool>(); break;
        case coFloatOrPercent: {
            if (!value.is_string()) throw std::invalid_argument("Expected a value or percent for " + it.key());
            const std::string input = value.get<std::string>();
            if (input.empty() || input.size() > 64) throw std::invalid_argument("Invalid value for " + it.key());
            std::size_t end = 0;
            double number;
            try { number = std::stod(input, &end); } catch (...) { throw std::invalid_argument("Invalid number for " + it.key()); }
            bool percent = end + 1 == input.size() && input[end] == '%';
            if (end != input.size() && !percent) throw std::invalid_argument("Invalid value for " + it.key());
            check_numeric(def, number);
            if (!percent && def.sidetext.find("mm ") != std::string::npos && number > def.max_literal)
                throw std::invalid_argument("Absolute value exceeds the allowed limit for " + it.key());
            auto *option = candidate.option<ConfigOptionFloatOrPercent>(it.key()); option->value = number; option->percent = percent;
            break;
        }
        case coString: {
            if (!value.is_string()) throw std::invalid_argument("Expected text for " + it.key());
            const std::string input = value.get<std::string>();
            if (input.size() > 4096 || input.find('\0') != std::string::npos || input.find('\n') != std::string::npos || input.find('\r') != std::string::npos)
                throw std::invalid_argument("Invalid text for " + it.key());
            candidate.option<ConfigOptionString>(it.key())->value = input; break;
        }
        case coEnum: {
            if (!value.is_string()) throw std::invalid_argument("Expected an enum key for " + it.key());
            const std::string choice = value.get<std::string>();
            if (!def.enum_keys_map || !def.enum_keys_map->count(choice) || (!def.enum_values.empty() && !def.has_enum_value(choice)))
                throw std::invalid_argument("Invalid enum value for " + it.key());
            candidate.set_deserialize_strict(it.key(), choice);
            if (candidate.option(it.key())->serialize() != choice) throw std::runtime_error("Enum did not round-trip for " + it.key());
            break;
        }
        default: throw std::invalid_argument("Unsupported scalar type for " + it.key());
        }
    }
    auto errors = candidate.validate(true);
    if (!errors.empty()) throw std::invalid_argument("Invalid resolved config: " + errors.begin()->first + ": " + errors.begin()->second);
    return candidate;
}

Slic3r::DynamicPrintConfig apply_process_overrides_checked(const Slic3r::DynamicPrintConfig &resolved,
    const std::string &overrides_json, const Slic3r::Preset &printer,
    const Slic3r::Preset &process, const Slic3r::Preset &filament)
{
    if (printer.type != Preset::TYPE_PRINTER || process.type != Preset::TYPE_PRINT || filament.type != Preset::TYPE_FILAMENT)
        throw std::invalid_argument("Incorrect preset types for process compatibility");
    DynamicPrintConfig candidate = apply_process_overrides(resolved, overrides_json);
    // After strict validation, reconstruct the exact changed key set from the
    // input rather than diff(): float equality in Orca may be approximate.
    const json changes = json::parse(overrides_json);
    t_config_option_keys keys; keys.reserve(changes.size());
    for (auto it = changes.begin(); it != changes.end(); ++it) keys.push_back(it.key());
    Preset changed_process(process);
    Preset printer_copy(printer), filament_copy(filament);
    changed_process.config.apply_only(candidate, keys, false);
    check_conditions(changed_process, printer_copy, changed_process, false);
    check_conditions(filament_copy, printer_copy, changed_process, true);
    const PresetWithVendorProfile p(printer_copy, printer_copy.vendor), process_view(changed_process, changed_process.vendor), f(filament_copy, filament_copy.vendor);
    if (!is_compatible_with_printer(process_view, p) || !is_compatible_with_printer(f, p) || !is_compatible_with_print(f, process_view, p))
        throw std::invalid_argument("The edited process is incompatible with the selected printer or filament");
    return candidate;
}
}
