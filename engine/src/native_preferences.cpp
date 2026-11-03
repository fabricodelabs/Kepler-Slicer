#include "native_preferences.hpp"

#include "libslic3r/Arrange.hpp"
#include "libslic3r/BuildVolume.hpp"
#include "libslic3r/ClipperUtils.hpp"
#include "libslic3r/FlushVolCalc.hpp"
#include "libslic3r/ModelArrange.hpp"
#include "libslic3r/Print.hpp"
#include "libslic3r/TriangleMesh.hpp"
#include "libslic3r/Utils.hpp"
#include "nlohmann/json.hpp"

#include <algorithm>
#include <array>
#include <cmath>
#include <filesystem>
#include <limits>
#include <set>
#include <stdexcept>
#include <utility>

namespace kepler {
namespace {
using namespace Slic3r;
using json = nlohmann::json;

struct Rgba { unsigned char red, green, blue, alpha; };
Rgba parse_colour(const std::string &value)
{
    // Orca preset colours use #RRGGBB. Preserve alpha for imported spool data
    // using the explicit CSS #RRGGBBAA form; never guess Android #AARRGGBB.
    if ((value.size() != 7 && value.size() != 9) || value.front() != '#')
        throw std::invalid_argument("Filament colour must be #RRGGBB or #RRGGBBAA");
    auto digit = [](char c) -> unsigned {
        if (c >= '0' && c <= '9') return unsigned(c - '0');
        if (c >= 'a' && c <= 'f') return unsigned(c - 'a' + 10);
        if (c >= 'A' && c <= 'F') return unsigned(c - 'A' + 10);
        throw std::invalid_argument("Invalid filament colour");
    };
    auto channel = [&](std::size_t pos) { return static_cast<unsigned char>((digit(value[pos]) << 4) | digit(value[pos + 1])); };
    return {channel(1), channel(3), channel(5), value.size() == 9 ? channel(7) : static_cast<unsigned char>(255)};
}

template<class Option>
const Option &required_vector(const DynamicPrintConfig &cfg, const char *key)
{
    const auto *value = cfg.option<Option>(key);
    if (!value || value->values.empty()) throw std::invalid_argument(std::string("Missing material option: ") + key);
    return *value;
}

std::size_t material_count(const DynamicPrintConfig &cfg)
{
    const auto &colors = required_vector<ConfigOptionStrings>(cfg, "filament_colour");
    const auto &diameters = required_vector<ConfigOptionFloats>(cfg, "filament_diameter");
    if (diameters.values.size() != colors.values.size())
        throw std::invalid_argument("Material slot vectors have different lengths");
    for (double diameter : diameters.values)
        if (!std::isfinite(diameter) || diameter <= 0.) throw std::invalid_argument("Invalid filament diameter");
    return colors.values.size();
}

std::size_t extruder_count(const DynamicPrintConfig &cfg)
{
    const auto &diameters = required_vector<ConfigOptionFloats>(cfg, "nozzle_diameter");
    for (double diameter : diameters.values)
        if (!std::isfinite(diameter) || diameter <= 0.) throw std::invalid_argument("Invalid nozzle diameter");
    return diameters.values.size();
}

void require_flush_dataset(int dataset)
{
    static const std::array<const char *, 3> names = {{"flush_data_standard.txt", "flush_data_dual_standard.txt", "flush_data_dual_highflow.txt"}};
    if (dataset < 0 || static_cast<std::size_t>(dataset) >= names.size())
        throw std::invalid_argument("Unknown or unresolved nozzle flush dataset");
    const auto path = std::filesystem::path(resources_dir()) / "flush" / names[static_cast<std::size_t>(dataset)];
    // GenericFlushPredictor caches each dataset after its first construction,
    // including a failed file read. Fail before that can poison the process-wide
    // predictor cache and silently substitute the colour heuristic for desktop data.
    if (!std::filesystem::is_regular_file(path) || std::filesystem::file_size(path) == 0)
        throw std::runtime_error("Native flushing dataset is missing from the prepared Orca resources");
}

// The arithmetic follows get_min_flush_volumes in desktop GUI/Plater.cpp.
// get_at has Orca's broadcast-first semantics for singleton option vectors.
std::vector<int> minimum_flush_volumes(const DynamicPrintConfig &cfg, std::size_t nozzle, std::size_t slots)
{
    const auto *nozzle_volume = cfg.option<ConfigOptionFloatsNullable>("nozzle_volume");
    double nozzle_volume_value = nozzle_volume && !nozzle_volume->values.empty() ? nozzle_volume->get_at(nozzle) : 0.;
    if (!std::isfinite(nozzle_volume_value) || std::abs(nozzle_volume_value) > 1000000.)
        throw std::invalid_argument("Unresolved or invalid nozzle volume");
    const auto *enabled = cfg.option<ConfigOptionInt>("enable_long_retraction_when_cut");
    const int machine_level = enabled ? enabled->value : 0;
    const auto *machine_active = cfg.option<ConfigOptionBools>("long_retractions_when_cut");
    const bool activated = machine_active && !machine_active->values.empty() && machine_active->get_at(nozzle);
    const auto *machine_distances = cfg.option<ConfigOptionFloats>("retraction_distances_when_cut");
    const auto *filament_active = cfg.option<ConfigOptionBools>("filament_long_retractions_when_cut");
    const auto *filament_distances = cfg.option<ConfigOptionFloats>("filament_retraction_distances_when_cut");
    const double machine_distance = machine_distances && !machine_distances->values.empty() ? machine_distances->get_at(nozzle) : 18.;
    if (!std::isfinite(machine_distance) || machine_distance < 0. || machine_distance > 1000000.)
        throw std::invalid_argument("Invalid machine cut retraction distance");
    std::vector<int> result;
    result.reserve(slots);
    for (std::size_t slot = 0; slot < slots; ++slot) {
        int retract = machine_level && activated ? static_cast<int>(machine_distance) : 0;
        const bool filament_enabled = filament_active && !filament_active->values.empty() && filament_active->get_at(slot);
        if (!filament_enabled) retract = 0;
        else if (machine_level == LongRectrationLevel::EnableFilament) {
            double distance = filament_distances && !filament_distances->values.empty() ? filament_distances->get_at(slot) : 18.;
            if (std::isnan(distance)) distance = machine_distance;
            if (!std::isfinite(distance) || distance < 0. || distance > 1000000.)
                throw std::invalid_argument("Invalid filament cut retraction distance");
            retract = static_cast<int>(distance);
        }
        // The pinned desktop uses the 1.75 mm cutter-path cross-section here.
        int extra = static_cast<int>(nozzle_volume_value);
        extra -= PI * 1.75 * 1.75 / 4 * retract;
        result.push_back(extra);
    }
    return result;
}

std::vector<std::string> expanded_strings(const DynamicPrintConfig &cfg, const char *key, std::size_t count)
{
    const auto &option = required_vector<ConfigOptionStrings>(cfg, key);
    std::vector<std::string> values;
    values.reserve(count);
    for (std::size_t i = 0; i < count; ++i) values.push_back(option.get_at(i));
    return values;
}
std::vector<int> expanded_ints(const DynamicPrintConfig &cfg, const char *key, std::size_t count)
{
    const auto &option = required_vector<ConfigOptionInts>(cfg, key);
    std::vector<int> values;
    values.reserve(count);
    for (std::size_t i = 0; i < count; ++i) values.push_back(option.get_at(i));
    return values;
}

BuildVolume build_volume(const DynamicPrintConfig &cfg)
{
    const auto &area = required_vector<ConfigOptionPoints>(cfg, "printable_area");
    const auto *height = cfg.option<ConfigOptionFloat>("printable_height");
    if (area.values.size() < 3 || !height || !std::isfinite(height->value) || height->value <= 0.)
        throw std::invalid_argument("Invalid printer build volume");
    for (const auto &point : area.values) if (!point.allFinite()) throw std::invalid_argument("Non-finite bed vertex");
    const auto *nozzle_areas = cfg.option<ConfigOptionPointsGroups>("extruder_printable_area");
    const auto *nozzle_heights = cfg.option<ConfigOptionFloatsNullable>("extruder_printable_height");
    BuildVolume volume(area.values, height->value,
        nozzle_areas ? nozzle_areas->values : std::vector<std::vector<Vec2d>>{},
        nozzle_heights ? nozzle_heights->values : std::vector<double>{});
    if (!volume.valid()) throw std::invalid_argument("Invalid printer bed shape");
    return volume;
}

arrangement::ArrangePolygons exclusion_polygons(const DynamicPrintConfig &cfg)
{
    arrangement::ArrangePolygons result;
    const auto mode = active_bed_exclude_volume_mode(cfg);
    const auto by_nozzle = get_bed_excluded_regions_by_extruder(cfg);
    const std::size_t count = mode == BedExcludeVolumeMode::Shared ? std::min<std::size_t>(1, by_nozzle.size()) : by_nozzle.size();
    for (std::size_t nozzle = 0; nozzle < count; ++nozzle) {
        for (const BedExcludeRegion &region : by_nozzle[nozzle]) {
            if (region.polygon.points.size() < 3 || (region.has_z_range && region.z_max <= region.z_min + EPSILON)) continue;
            arrangement::ArrangePolygon blocker;
            blocker.poly.contour = region.polygon;
            blocker.bed_idx = 0;
            blocker.is_virt_object = true;
            blocker.is_bed_exclusion = true;
            blocker.bed_exclusion_extruder_id = mode == BedExcludeVolumeMode::Shared ? -1 : static_cast<int>(nozzle);
            blocker.z_min = region.z_min;
            blocker.z_max = region.z_max;
            blocker.has_z_range = region.has_z_range;
            blocker.height = std::max(0., region.z_max - region.z_min);
            blocker.name = "Bed exclusion";
            result.push_back(std::move(blocker));
        }
    }
    // The resolved parser includes legacy bed_exclude_area and explicit volume
    // definitions; do not replace those with an axis-aligned bounding rectangle.
    return result;
}

double scalar_float(const DynamicPrintConfig &cfg, const char *key, double fallback)
{
    const auto *option = cfg.option<ConfigOptionFloat>(key);
    const double value = option ? option->value : fallback;
    if (!std::isfinite(value) || value < 0. || value > 1000000.) throw std::invalid_argument(std::string("Invalid arrange setting: ") + key);
    return value;
}

void require_arrange_fit(Model &candidate, const DynamicPrintConfig &cfg,
    const BuildVolume &volume, const arrangement::ArrangePolygons &items,
    const arrangement::ArrangePolygons &excludes)
{
    candidate.update_print_volume_state(volume);
    const Polygon bed(get_bed_shape(cfg));
    for (const auto *object : candidate.objects) {
        if (!object->printable) continue;
        for (const auto *instance : object->instances)
            if (instance->printable && instance->print_volume_state != ModelInstancePVS_Inside)
                throw std::runtime_error("A cloned instance does not fit the printer build volume");
    }
    for (std::size_t i = 0; i < items.size(); ++i) {
        const auto actual = items[i].transformed_poly();
        if (!diff(actual.contour, bed).empty()) throw std::runtime_error("An arranged instance exceeds the real bed polygon");
        for (const auto &exclusion : excludes) {
            if (arrangement::bed_exclusion_applies(items[i], exclusion) &&
                !intersection(actual, exclusion.transformed_poly()).empty())
                throw std::runtime_error("An arranged instance intersects a printer exclusion region");
        }
        for (std::size_t j = 0; j < i; ++j)
            if (!intersection(actual, items[j].transformed_poly()).empty())
                throw std::runtime_error("Arranged instances overlap");
    }
}

}

Slic3r::DynamicPrintConfig recalculate_flushing(const DynamicPrintConfig &config,
    int changed_slot, int extruder, const std::vector<std::vector<std::string>> &physical_colours,
    const std::vector<bool> &virtual_mixed_slots)
{
    const std::size_t slots = material_count(config);
    const std::size_t nozzles = extruder_count(config);
    if (changed_slot < -1 || (changed_slot >= 0 && static_cast<std::size_t>(changed_slot) >= slots))
        throw std::invalid_argument("Changed material slot is outside the project");
    if (extruder < -1 || (extruder >= 0 && static_cast<std::size_t>(extruder) >= nozzles))
        throw std::invalid_argument("Extruder is outside the printer");
    if ((!physical_colours.empty() && physical_colours.size() != slots) ||
        (!virtual_mixed_slots.empty() && virtual_mixed_slots.size() != slots))
        throw std::invalid_argument("Physical spool metadata does not match material slots");
    if (slots > std::numeric_limits<std::size_t>::max() / slots ||
        nozzles > std::numeric_limits<std::size_t>::max() / (slots * slots))
        throw std::invalid_argument("Flush matrix dimensions overflow");
    const auto &original = required_vector<ConfigOptionFloats>(config, "flush_volumes_matrix");
    if (original.values.size() != slots * slots * nozzles)
        throw std::invalid_argument("Flush matrix dimensions do not match material slots and nozzles");
    for (double value : original.values)
        if (!std::isfinite(value) || value < 0. || value > g_max_flush_volume)
            throw std::invalid_argument("Invalid flushing volume");
    const auto &colors = required_vector<ConfigOptionStrings>(config, "filament_colour");
    const auto *supports = config.option<ConfigOptionBools>("filament_is_support");
    const auto &datasets = required_vector<ConfigOptionIntsNullable>(config, "nozzle_flush_dataset");
    std::vector<std::vector<Rgba>> spools(slots);
    for (std::size_t i = 0; i < slots; ++i) {
        const auto values = physical_colours.empty() ? std::vector<std::string>{colors.values[i]} : physical_colours[i];
        if (values.empty()) throw std::invalid_argument("Physical spool has no colours");
        for (const auto &value : values) spools[i].push_back(parse_colour(value));
    }
    const auto *native_mixed = config.option<ConfigOptionBools>("filament_is_mixed");
    auto mixed = [&](std::size_t slot) {
        return !virtual_mixed_slots.empty() ? virtual_mixed_slots[slot] :
            native_mixed && slot < native_mixed->values.size() && native_mixed->values[slot];
    };
    auto support = [&](std::size_t slot) { return supports && !supports->values.empty() && supports->get_at(slot); };
    DynamicPrintConfig candidate(config);
    auto &output = candidate.option<ConfigOptionFloats>("flush_volumes_matrix")->values;
    for (std::size_t nozzle = 0; nozzle < nozzles; ++nozzle) {
        if (extruder >= 0 && nozzle != static_cast<std::size_t>(extruder)) continue;
        const auto minimum = minimum_flush_volumes(config, nozzle, slots);
        const int dataset = datasets.get_at(nozzle);
        require_flush_dataset(dataset);
        auto matrix = get_flush_volumes_matrix(original.values, nozzle, nozzles);
        for (std::size_t from = 0; from < slots; ++from) {
            for (std::size_t to = 0; to < slots; ++to) {
                if (from == to || mixed(from) || mixed(to) ||
                    (changed_slot >= 0 && from != static_cast<std::size_t>(changed_slot) && to != static_cast<std::size_t>(changed_slot))) continue;
                int volume = 0;
                if (support(to)) volume = g_flush_volume_to_support;
                else {
                    FlushVolCalculator calculator(minimum[from], g_max_flush_volume, dataset);
                    for (const auto &src : spools[from]) for (const auto &dst : spools[to])
                        volume = std::max(volume, calculator.calc_flush_vol(src.alpha, src.red, src.green, src.blue,
                            dst.alpha, dst.red, dst.green, dst.blue));
                    if (support(from)) volume = std::max(volume, g_min_flush_volume_from_support);
                }
                matrix[slots * from + to] = volume;
            }
        }
        set_flush_volumes_matrix(output, matrix, nozzle, nozzles);
    }
    // flush_multiplier is deliberately unchanged: the desktop computes an
    // unmultiplied matrix and applies the multiplier later in slicing.
    return candidate;
}

Slic3r::DynamicPrintConfig apply_material_edits(const DynamicPrintConfig &config, const std::string &edits_json)
{
    std::set<std::string> seen;
    auto callback = [&](int depth, json::parse_event_t event, json &value) {
        if (event == json::parse_event_t::key && depth == 1 && !seen.insert(value.get<std::string>()).second)
            throw std::invalid_argument("Duplicate saved material setting");
        return true;
    };
    const json edits = json::parse(edits_json, callback);
    if (!edits.is_object()) throw std::invalid_argument("Saved material settings must be an object");
    if (edits.empty()) return DynamicPrintConfig(config);
    if (edits.size() != 2 || !edits.contains("colours") || !edits.contains("flush_volumes") ||
        !edits.at("colours").is_array() || !edits.at("flush_volumes").is_array())
        throw std::invalid_argument("Saved material settings require colours and flush_volumes only");
    const auto slots = material_count(config), nozzles = extruder_count(config);
    if (slots > std::numeric_limits<std::size_t>::max() / slots || nozzles > std::numeric_limits<std::size_t>::max() / (slots * slots))
        throw std::invalid_argument("Saved flush matrix dimensions overflow");
    if (edits.at("colours").size() != slots || edits.at("flush_volumes").size() != slots * slots * nozzles)
        throw std::invalid_argument("Saved material settings no longer match the project slots or printer nozzles");
    std::vector<std::string> colors;colors.reserve(slots);
    for (const auto &value : edits.at("colours")) {
        if (!value.is_string()) throw std::invalid_argument("Saved filament colour must be a string");
        const auto color = value.get<std::string>();parse_colour(color);colors.push_back(color);
    }
    std::vector<double> volumes;volumes.reserve(slots * slots * nozzles);
    for (const auto &value : edits.at("flush_volumes")) {
        if (!value.is_number()) throw std::invalid_argument("Saved flush volume must be numeric");
        const auto volume = value.get<double>();
        if (!std::isfinite(volume) || volume < 0. || volume > g_max_flush_volume)
            throw std::invalid_argument("Saved flush volume is outside the native range");
        volumes.push_back(volume);
    }
    DynamicPrintConfig candidate(config);
    candidate.set_key_value("filament_colour", new ConfigOptionStrings(colors));
    candidate.set_key_value("flush_volumes_matrix", new ConfigOptionFloats(volumes));
    return candidate;
}

std::string material_snapshot_json(const DynamicPrintConfig &config)
{
    const auto count = material_count(config);
    const auto nozzles = extruder_count(config);
    const auto types = expanded_strings(config, "filament_type", count);
    const auto temperatures = expanded_ints(config, "nozzle_temperature", count);
    const auto lows = expanded_ints(config, "nozzle_temperature_range_low", count);
    const auto highs = expanded_ints(config, "nozzle_temperature_range_high", count);
    const auto compatibility = Print::check_multi_filaments_compatibility(types, temperatures, lows, highs);
    const auto &colors = required_vector<ConfigOptionStrings>(config, "filament_colour");
    const auto *supports = config.option<ConfigOptionBools>("filament_is_support");
    const auto *mixed = config.option<ConfigOptionBools>("filament_is_mixed");
    json out = {{"version", 1}, {"slots", json::array()}, {"nozzle_count", nozzles},
        {"compatibility", compatibility == Compatible ? "compatible" : compatibility == HighLowMixed ? "mixed_temperatures" : "invalid_temperature_range"},
        {"flush_volumes_matrix", required_vector<ConfigOptionFloats>(config, "flush_volumes_matrix").values},
        {"minimum_flush_volumes", json::array()}};
    for (std::size_t i = 0; i < count; ++i)
        out["slots"].push_back({{"index", i}, {"color", colors.values[i]}, {"type", types[i]},
            {"support", supports && !supports->values.empty() && supports->get_at(i)},
            {"virtual_mixed", mixed && i < mixed->values.size() && mixed->values[i]},
            {"temperature", temperatures[i]}, {"range_low", lows[i]}, {"range_high", highs[i]}});
    for (std::size_t i = 0; i < nozzles; ++i) out["minimum_flush_volumes"].push_back(minimum_flush_volumes(config, i, count));
    if (const auto *multiplier = config.option<ConfigOptionFloats>("flush_multiplier")) out["flush_multiplier"] = multiplier->values;
    return out.dump();
}

Slic3r::Model clone_and_arrange(const Model &source, const DynamicPrintConfig &config,
    std::size_t object_index, std::size_t instance_index, std::size_t copies, bool auto_arrange)
{
    if (object_index >= source.objects.size() || instance_index >= source.objects[object_index]->instances.size() || copies == 0)
        throw std::invalid_argument("A source object, instance and positive clone count are required");
    if (copies > std::numeric_limits<std::size_t>::max() - source.objects[object_index]->instances.size())
        throw std::invalid_argument("Clone count overflows the model");
    if (!source.objects[object_index]->printable || !source.objects[object_index]->instances[instance_index]->printable)
        throw std::invalid_argument("The source instance is not printable");
    const auto volume = build_volume(config);
    Model candidate = Model::make_copy(source);
    auto *object = candidate.objects[object_index];
    const auto *original = object->instances[instance_index];
    const auto original_offset = original->get_offset();
    const auto bed_size = volume.bounding_volume2d().size();
    const double offset_base = .05 * std::max(bed_size.x(), bed_size.y());
    // add_instance copies placement but allocates a fresh native ObjectID.
    for (std::size_t i = 0; i < copies; ++i) {
        const double offset = offset_base * static_cast<double>(i + 1);
        const Vec3d copy_offset = original_offset + Vec3d(offset, offset, 0.);
        if (!copy_offset.allFinite()) throw std::invalid_argument("Clone position overflows the model");
        object->add_instance(*original)->set_offset(copy_offset);
    }
    if (!auto_arrange) return candidate;
    if (const auto *belt = config.option<ConfigOptionBool>("belt_printer"); belt && belt->value)
        throw std::invalid_argument("Belt clone arrangement requires the belt plate session; source model preserved");

    arrangement::ArrangePolygons items;
    std::vector<ModelInstance *> instances;
    for (auto *obj : candidate.objects) {
        if (!obj->printable) continue;
        for (auto *instance : obj->instances) {
            if (!instance->printable) continue;
            auto polygon = get_instance_arrange_poly(instance, config);
            if (polygon.poly.contour.points.size() < 3) throw std::invalid_argument("An instance has no arrangeable footprint");
            polygon.itemid = static_cast<int>(items.size());
            // Empty mapping deliberately means all physical-nozzle exclusions
            // apply, matching desktop's unresolved automatic mapping rule.
            polygon.bed_exclusion_extruder_ids.clear();
            items.push_back(std::move(polygon));
            instances.push_back(instance);
        }
    }
    if (items.empty()) throw std::invalid_argument("No printable instances to arrange");
    auto excludes = exclusion_polygons(config);
    arrangement::ArrangeParams params;
    params.parallel = false;
    params.allow_rotations = false;
    params.progressind = [](unsigned, std::string) {};
    params.clearance_radius = scalar_float(config, "extruder_clearance_radius", 0.);
    params.clearance_height_to_rod = scalar_float(config, "extruder_clearance_height_to_rod", 0.);
    params.clearance_height_to_lid = scalar_float(config, "extruder_clearance_height_to_lid", 0.);
    params.nozzle_height = scalar_float(config, "nozzle_height", 0.);
    params.printable_height = volume.printable_height();
    if (const auto *sequence = config.option<ConfigOptionEnum<PrintSequence>>("print_sequence")) params.is_seq_print = sequence->value == PrintSequence::ByObject;
    if (const auto *center = config.option<ConfigOptionPoint>("best_object_pos")) params.align_center = center->value;
    if (params.is_seq_print) params.bed_shrink_x = params.bed_shrink_y = BED_SHRINK_SEQ_PRINT;
    arrangement::update_arrange_params(params, &config, items);
    arrangement::update_selected_items_inflation(items, &config, params);
    arrangement::update_unselected_items_inflation(excludes, &config, params);
    auto bed = arrangement::get_shrink_bedpts(&config, params);
    if (bed.size() < 3) throw std::invalid_argument("No usable area remains on the bed");
    arrangement::arrange(items, excludes, bed, params);
    for (const auto &item : items) {
        if (item.bed_idx != 0) throw std::runtime_error("The cloned model does not fit on the current plate");
        if (item.itemid < 0 || static_cast<std::size_t>(item.itemid) >= instances.size()) throw std::runtime_error("Invalid native arrange result");
        instances[static_cast<std::size_t>(item.itemid)]->apply_arrange_result(item.translation.cast<double>(), item.rotation);
        instances[static_cast<std::size_t>(item.itemid)]->arrange_order = item.itemid;
    }
    require_arrange_fit(candidate, config, volume, items, excludes);
    return candidate;
}

std::string model_placements_json(const Model &model)
{
    json out = {{"version", 1}, {"model_id", model.id().id}, {"objects", json::array()}};
    for (std::size_t oi = 0; oi < model.objects.size(); ++oi) {
        const auto *object = model.objects[oi];
        json row = {{"index", oi}, {"id", object->id().id}, {"name", object->name}, {"instances", json::array()}};
        for (std::size_t ii = 0; ii < object->instances.size(); ++ii) {
            const auto *instance = object->instances[ii];
            const auto offset = instance->get_offset(), rotation = instance->get_rotation(), scale = instance->get_scaling_factor(), mirror = instance->get_mirror();
            if (!offset.allFinite() || !rotation.allFinite() || !scale.allFinite() || !mirror.allFinite()) throw std::runtime_error("Non-finite native instance transform");
            row["instances"].push_back({{"index", ii}, {"id", instance->id().id},
                {"offset", {offset.x(), offset.y(), offset.z()}}, {"rotation", {rotation.x(), rotation.y(), rotation.z()}},
                {"scale", {scale.x(), scale.y(), scale.z()}}, {"mirror", {mirror.x(), mirror.y(), mirror.z()}}});
        }
        out["objects"].push_back(std::move(row));
    }
    return out.dump();
}

std::string native_log_level() { return get_string_logging_level(get_logging_level()); }
std::string set_native_log_level(const std::string &level)
{
    static const std::array<const char *, 6> levels = {{"fatal", "error", "warning", "info", "debug", "trace"}};
    const auto found = std::find_if(levels.begin(), levels.end(), [&](const char *entry) { return level == entry; });
    if (found == levels.end()) throw std::invalid_argument("Unknown native log level");
    set_logging_level(static_cast<unsigned>(std::distance(levels.begin(), found)));
    return native_log_level();
}

std::string native_preferences_self_test()
{
    json results = {{"version", 1}, {"checks", json::array()}};
    auto check = [&](const char *name, bool passed) {
        results["checks"].push_back({{"name", name}, {"passed", passed}});
        if (!passed) throw std::runtime_error(std::string("Native preferences test failed: ") + name);
    };
    auto cfg = DynamicPrintConfig::full_print_config();
    cfg.set_key_value("filament_colour", new ConfigOptionStrings{"#000000", "#FFFFFF", "#FF0000"});
    cfg.set_key_value("filament_diameter", new ConfigOptionFloats{1.75, 1.75, 1.75});
    cfg.set_key_value("filament_type", new ConfigOptionStrings{"PLA", "PLA", "PLA"});
    cfg.set_key_value("filament_is_support", new ConfigOptionBools{false, false, true});
    cfg.set_key_value("flush_volumes_matrix", new ConfigOptionFloats{0., 123., 124., 125., 0., 126., 127., 128., 0.});
    cfg.set_key_value("nozzle_diameter", new ConfigOptionFloats{.4});
    cfg.set_key_value("nozzle_flush_dataset", new ConfigOptionIntsNullable{0});
    cfg.set_key_value("nozzle_volume", new ConfigOptionFloatsNullable{0.});
    cfg.set_key_value("enable_long_retraction_when_cut", new ConfigOptionInt(0));
    const auto before = cfg.option<ConfigOptionFloats>("flush_volumes_matrix")->values;
    const auto changed = recalculate_flushing(cfg, 0);
    const auto &matrix = changed.option<ConfigOptionFloats>("flush_volumes_matrix")->values;
    FlushVolCalculator calculator(0, g_max_flush_volume, 0);
    check("flush_uses_upstream_calculator", matrix[1] == calculator.calc_flush_vol(255, 0, 0, 0, 255, 255, 255, 255));
    check("flush_directional", matrix[1] != matrix[3]);
    check("preserve_unmodified_manual_cells", matrix[5] == before[5] && matrix[7] == before[7]);
    check("preserve_diagonal", matrix[0] == 0. && matrix[4] == 0. && matrix[8] == 0.);
    check("source_matrix_unchanged", cfg.option<ConfigOptionFloats>("flush_volumes_matrix")->values == before);
    const auto all = recalculate_flushing(cfg, -1);
    const auto &all_matrix = all.option<ConfigOptionFloats>("flush_volumes_matrix")->values;
    check("flush_to_support", all_matrix[2] == g_flush_volume_to_support && all_matrix[5] == g_flush_volume_to_support);
    check("flush_from_support", all_matrix[6] >= g_min_flush_volume_from_support && all_matrix[7] >= g_min_flush_volume_from_support);
    const auto mixed = recalculate_flushing(cfg, -1, -1, {}, {false, true, false});
    check("virtual_mixed_slot_preserved", mixed.option<ConfigOptionFloats>("flush_volumes_matrix")->values[1] == before[1] && mixed.option<ConfigOptionFloats>("flush_volumes_matrix")->values[7] == before[7]);
    auto mixed_config = DynamicPrintConfig(cfg);
    mixed_config.set_key_value("filament_is_mixed", new ConfigOptionBools{false, true, false});
    const auto native_mixed = recalculate_flushing(mixed_config, -1);
    check("native_virtual_mixed_metadata_preserved", native_mixed.option<ConfigOptionFloats>("flush_volumes_matrix")->values[1] == before[1] && native_mixed.option<ConfigOptionFloats>("flush_volumes_matrix")->values[7] == before[7]);
    bool rejected = false;
    try { recalculate_flushing(cfg, 3); } catch (const std::invalid_argument &) { rejected = true; }
    check("invalid_slot_rejected", rejected);
    auto invalid = DynamicPrintConfig(cfg);
    invalid.option<ConfigOptionStrings>("filament_colour")->values[0] = "red";
    rejected = false;
    try { recalculate_flushing(invalid, -1); } catch (const std::invalid_argument &) { rejected = true; }
    check("invalid_colour_rejected", rejected);
    check("invalid_candidate_preserves_source", cfg.option<ConfigOptionFloats>("flush_volumes_matrix")->values == before);
    const json edits = {{"colours", {"#112233", "#FFFFFF", "#FF0000"}}, {"flush_volumes", before}};
    const auto edited = apply_material_edits(cfg, edits.dump());
    check("saved_material_colour_applied", edited.option<ConfigOptionStrings>("filament_colour")->values[0] == "#112233");
    check("saved_manual_flush_cells_preserved", edited.option<ConfigOptionFloats>("flush_volumes_matrix")->values == before && cfg.option<ConfigOptionStrings>("filament_colour")->values[0] == "#000000");
    auto wrong_slots = edits;wrong_slots["colours"] = json::array({"#112233"});rejected=false;
    try { apply_material_edits(cfg, wrong_slots.dump()); } catch (const std::invalid_argument &) { rejected=true; }
    check("saved_material_wrong_slot_count_rejected", rejected);
    auto negative_volume = edits;negative_volume["flush_volumes"][1]=-1;rejected=false;
    try { apply_material_edits(cfg, negative_volume.dump()); } catch (const std::invalid_argument &) { rejected=true; }
    check("saved_negative_flush_volume_rejected", rejected);
    auto multi = DynamicPrintConfig(cfg);
    multi.set_key_value("nozzle_diameter", new ConfigOptionFloats{.4, .6});
    auto combined = before; combined.insert(combined.end(), before.begin(), before.end());
    multi.set_key_value("flush_volumes_matrix", new ConfigOptionFloats(combined));
    const auto multi_changed = recalculate_flushing(multi, 0, 1);
    const auto &multi_matrix = multi_changed.option<ConfigOptionFloats>("flush_volumes_matrix")->values;
    check("other_nozzle_preserved", std::equal(before.begin(), before.end(), multi_matrix.begin()));
    check("selected_nozzle_recalculated", multi_matrix[10] == matrix[1]);
    check("compatible_materials", Print::check_multi_filaments_compatibility({"PLA", "PLA"}, {210, 210}, {190, 190}, {230, 230}) == Compatible);
    check("mixed_temperatures_detected", Print::check_multi_filaments_compatibility({"PLA", "ABS"}, {210, 260}, {190, 240}, {230, 280}) == HighLowMixed);
    check("invalid_temperature_range_detected", Print::check_multi_filaments_compatibility({"PLA", "PLA"}, {210, 210}, {230, 190}, {190, 230}) == InvalidTemperatureRange);

    auto arrange_cfg = DynamicPrintConfig::full_print_config();
    arrange_cfg.set_key_value("printable_area", new ConfigOptionPoints{{0., 0.}, {100., 0.}, {100., 100.}, {0., 100.}});
    arrange_cfg.set_key_value("printable_height", new ConfigOptionFloat(100.));
    arrange_cfg.set_key_value("belt_printer", new ConfigOptionBool(false));
    arrange_cfg.set_key_value("bed_exclude_area", new ConfigOptionPoints{});
    arrange_cfg.set_key_value("bed_exclude_volumes", new ConfigOptionString(""));
    arrange_cfg.set_key_value("extruder_bed_exclude_volumes", new ConfigOptionStrings{});
    arrange_cfg.set_key_value("enable_support", new ConfigOptionBool(false));
    arrange_cfg.set_key_value("print_sequence", new ConfigOptionEnum<PrintSequence>(PrintSequence::ByLayer));
    Model source;
    auto *object = source.add_object();
    object->name = "Native preference test cube";
    object->add_volume(make_cube(10., 10., 10.));
    auto *instance = object->add_instance();
    instance->set_offset(Vec3d(20., 20., 0.));
    const auto original_id = instance->id();
    auto raw_copy = clone_and_arrange(source, arrange_cfg, 0, 0, 1, false);
    check("clone_without_arrange_desktop_offset", raw_copy.objects[0]->instances[1]->get_offset().isApprox(instance->get_offset() + Vec3d(5., 5., 0.)));
    check("clone_new_identity", raw_copy.objects[0]->instances[0]->id() == original_id && raw_copy.objects[0]->instances[1]->id() != original_id);
    auto arranged = clone_and_arrange(source, arrange_cfg, 0, 0, 2, true);
    check("upstream_arrange_three_instances", arranged.objects[0]->instances.size() == 3);
    check("arrange_source_unchanged", source.objects[0]->instances.size() == 1 && source.objects[0]->instances[0]->get_offset().isApprox(Vec3d(20., 20., 0.)));
    auto excluded = DynamicPrintConfig(arrange_cfg);
    excluded.set_key_value("bed_exclude_area", new ConfigOptionPoints{{40., 0.}, {100., 0.}, {100., 100.}, {40., 100.}});
    auto safe = clone_and_arrange(source, excluded, 0, 0, 1, true);
    check("arrange_avoids_bed_exclusion", safe.objects[0]->instances.size() == 2);
    auto impossible = DynamicPrintConfig(arrange_cfg);
    impossible.set_key_value("printable_area", new ConfigOptionPoints{{0., 0.}, {12., 0.}, {12., 12.}, {0., 12.}});
    rejected = false;
    try { clone_and_arrange(source, impossible, 0, 0, 1, true); } catch (const std::exception &) { rejected = true; }
    check("arrange_no_fit_rejected", rejected);
    check("failed_arrange_preserves_source", source.objects[0]->instances.size() == 1 && source.objects[0]->instances[0]->id() == original_id);
    const auto placements = json::parse(model_placements_json(arranged));
    check("placement_snapshot_real_instances", placements.at("objects")[0].at("instances").size() == 3);
    const auto old_level = native_log_level();
    try {
        check("native_log_setter", set_native_log_level("debug") == "debug");
        check("native_log_actual_level", native_log_level() == "debug");
        set_native_log_level(old_level);
    } catch (...) { set_native_log_level(old_level); throw; }
    check("native_log_restored", native_log_level() == old_level);
    results["passed"] = true;
    results["count"] = results["checks"].size();
    return results.dump();
}

}
