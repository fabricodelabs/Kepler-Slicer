#include <jni.h>

#include <cmath>
#include <cstdint>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <limits>
#include <locale>
#include <mutex>
#include <sstream>
#include <stdexcept>
#include <string>
#include <vector>
#include <array>

#include "libslic3r/Model.hpp"
#include "libslic3r/BuildVolume.hpp"
#include "libslic3r/ClipperUtils.hpp"
#include "libslic3r/Print.hpp"
#include "libslic3r/PrintConfig.hpp"
#include "libslic3r/Utils.hpp"
#include "system_profile_config.hpp"
#include "process_settings.hpp"
#include "model_transform.hpp"
#include "model_io.hpp"
#include "project_settings.hpp"
#include "project_settings_tests.hpp"
#include "native_preferences.hpp"
#include "step_import.hpp"
#include "libslic3r/Format/3mf.hpp"
#include <nlohmann/json.hpp>

// Experimental proof of the upstream API, not integrated into the app.
// Upstream's resource and data directories are process-global, so calls are serialized.
namespace {
std::mutex engine_mutex;

struct StlBounds {
    double min[3] = { std::numeric_limits<double>::infinity(), std::numeric_limits<double>::infinity(), std::numeric_limits<double>::infinity() };
    double max[3] = { -std::numeric_limits<double>::infinity(), -std::numeric_limits<double>::infinity(), -std::numeric_limits<double>::infinity() };
    std::uint64_t vertices = 0;
    void add(double x, double y, double z) {
        const double values[] = {x, y, z};
        for (int i = 0; i < 3; ++i) {
            if (!std::isfinite(values[i])) throw std::runtime_error("STL coordinates must be finite");
            min[i] = std::min(min[i], values[i]); max[i] = std::max(max[i], values[i]);
            if (!std::isfinite(max[i] - min[i])) throw std::runtime_error("STL dimensions are not finite");
        }
        if (vertices == std::numeric_limits<std::uint64_t>::max()) throw std::runtime_error("STL vertex count overflow");
        ++vertices;
    }
    void finish() const {
        if (vertices == 0 || vertices % 3 != 0)
            throw std::runtime_error("STL contains no complete triangles");
        if (!(max[0] > min[0] || max[1] > min[1] || max[2] > min[2])) throw std::runtime_error("STL must have a non-zero size");
    }
};

void validate_stl_before_import(const std::string &path)
{
    const auto file_size = std::filesystem::file_size(path);
    if (file_size < 15) throw std::runtime_error("STL is empty or malformed");
    std::ifstream in(path, std::ios::binary);
    if (!in) throw std::runtime_error("Could not open STL");
    std::array<unsigned char, 84> header{};
    if (file_size >= header.size() && !in.read(reinterpret_cast<char *>(header.data()), header.size()))
        throw std::runtime_error("Could not read STL header");
    StlBounds bounds;
    const std::uint32_t count = file_size >= header.size() ?
        (std::uint32_t(header[80]) | (std::uint32_t(header[81]) << 8) |
         (std::uint32_t(header[82]) << 16) | (std::uint32_t(header[83]) << 24)) : 0;
    if (file_size >= header.size() && 84ull + std::uint64_t(count) * 50ull == file_size) {
        if (!count) throw std::runtime_error("STL contains no triangles");
        std::array<unsigned char, 50> record{};
        for (std::uint64_t facet = 0; facet < count; ++facet) {
            if (!in.read(reinterpret_cast<char *>(record.data()), record.size())) throw std::runtime_error("Binary STL is truncated");
            double xyz[9];
            for (int i = 0; i < 9; ++i) {
                const std::size_t p = 12 + 4 * i;
                const std::uint32_t bits = std::uint32_t(record[p]) | (std::uint32_t(record[p+1]) << 8) |
                    (std::uint32_t(record[p+2]) << 16) | (std::uint32_t(record[p+3]) << 24);
                float value; std::memcpy(&value, &bits, sizeof(value)); xyz[i] = value;
            }
            for (int i = 0; i < 9; i += 3) bounds.add(xyz[i], xyz[i+1], xyz[i+2]);
        }
    } else {
        // Parse the same ordered ASCII facet structure expected by upstream's
        // reader. Deliberately reject malformed input before it reaches that reader.
        in.clear(); in.seekg(0); in.imbue(std::locale::classic());
        std::istream &text = in; std::string token;
        if (!(text >> token) || token != "solid") throw std::runtime_error("Unsupported or malformed STL");
        text.ignore(std::numeric_limits<std::streamsize>::max(), '\n'); bool ended = false;
        while (text >> token) {
            if (token == "endsolid") { ended = true; break; }
            if (token != "facet" || !(text >> token) || token != "normal") throw std::runtime_error("Malformed ASCII STL facet");
            for (int i = 0; i < 3; ++i) if (!(text >> token)) throw std::runtime_error("Malformed ASCII STL normal");
            if (!(text >> token) || token != "outer" || !(text >> token) || token != "loop") throw std::runtime_error("Malformed ASCII STL loop");
            for (int i = 0; i < 3; ++i) {
                double x, y, z;
                if (!(text >> token) || token != "vertex" || !(text >> x >> y >> z)) throw std::runtime_error("Malformed ASCII STL vertex");
                bounds.add(x, y, z);
            }
            if (!(text >> token) || token != "endloop") throw std::runtime_error("Malformed ASCII STL endloop");
            text.ignore(std::numeric_limits<std::streamsize>::max(), '\n');
            if (!(text >> token) || token != "endfacet") throw std::runtime_error("Malformed ASCII STL endfacet");
            text.ignore(std::numeric_limits<std::streamsize>::max(), '\n');
        }
        if (!ended) throw std::runtime_error("ASCII STL has no endsolid");
    }
    bounds.finish();
}

class UtfChars {
public:
    UtfChars(JNIEnv *env, jstring value) : m_env(env), m_value(value) {
        if (!value) throw std::invalid_argument("A required path is null");
        m_chars = env->GetStringUTFChars(value, nullptr);
        if (!m_chars) throw std::runtime_error("Could not read a path");
    }
    ~UtfChars() { if (m_chars) m_env->ReleaseStringUTFChars(m_value, m_chars); }
    std::string str() const { return m_chars; }
    UtfChars(const UtfChars &) = delete;
    UtfChars &operator=(const UtfChars &) = delete;
private:
    JNIEnv *m_env;
    jstring m_value;
    const char *m_chars = nullptr;
};

// Caller holds engine_mutex and supplies the app's private data directory.
std::filesystem::path create_job_directory(const std::string &job_path, const std::string &data)
{
    const std::filesystem::path job(job_path);
    if (job_path.empty() || job.filename().empty() || job.filename() == "." || job.filename() == "..")
        throw std::invalid_argument("A new job directory is required");
    if (std::filesystem::canonical(job.parent_path()) != std::filesystem::canonical(data))
        throw std::invalid_argument("Job directory must be a direct child of the private data directory");
    if (!std::filesystem::create_directory(job))
        throw std::invalid_argument("Job directory already exists");
    return job;
}

// Caller holds engine_mutex and supplies an already validated full configuration.
std::string slice_model(const std::string &model_file, const std::string &output_file,
                        const Slic3r::DynamicPrintConfig &config, const kepler::ModelTransform *transform = nullptr, int plate = -1,
                        Slic3r::Model *prepared = nullptr, bool allow_mixed_temperatures = false,
                        bool center_derived_step = false)
{
    const auto extension=std::filesystem::path(model_file).extension().string();
    if(extension==".stl") validate_stl_before_import(model_file);
    Slic3r::Model model = prepared ? std::move(*prepared) : extension==".stl" ? Slic3r::Model::read_from_file(model_file) : kepler::load_plate_model(model_file,plate);
    if(model.objects.empty()) throw std::runtime_error("Model has no objects");
    if (extension==".stl" && (model.objects.size() != 1 || model.objects.front()->instances.size() != 1))
        throw std::runtime_error("Only one STL object and one instance are supported");
    Slic3r::Print print;
    if(allow_mixed_temperatures) {
        const auto *types=config.opt<Slic3r::ConfigOptionStrings>("filament_type");
        const auto *temperatures=config.opt<Slic3r::ConfigOptionInts>("nozzle_temperature");
        const auto *lows=config.opt<Slic3r::ConfigOptionInts>("nozzle_temperature_range_low");
        const auto *highs=config.opt<Slic3r::ConfigOptionInts>("nozzle_temperature_range_high");
        if(!types||!temperatures||!lows||!highs)throw std::runtime_error("Missing filament temperature compatibility data");
        if(Slic3r::Print::check_multi_filaments_compatibility(types->values,temperatures->values,lows->values,highs->values)==Slic3r::InvalidTemperatureRange)
            throw std::runtime_error("A filament nozzle temperature is outside its permitted range");
    }
    print.set_check_multi_filaments_compatibility(!allow_mixed_temperatures);
    print.set_status_silent();
    for (Slic3r::ModelObject *object : model.objects) {
        print.auto_assign_extruders(object);
    }
    const auto *area = config.opt<Slic3r::ConfigOptionPoints>("printable_area");
    const auto *extruder_areas = config.opt<Slic3r::ConfigOptionPointsGroups>("extruder_printable_area");
    const auto *extruder_heights = config.opt<Slic3r::ConfigOptionFloatsNullable>("extruder_printable_height");
    const double height = config.opt_float("printable_height");
    if (!area || area->values.size() < 3 || !std::isfinite(height) || height <= 0)
        throw std::runtime_error("The print configuration has no valid build volume");
    const std::vector<std::vector<Slic3r::Vec2d>> empty_areas;
    const std::vector<double> empty_heights;
    const Slic3r::BuildVolume volume(area->values, height,
        extruder_areas ? extruder_areas->values : empty_areas,
        extruder_heights ? extruder_heights->values : empty_heights);
    if (!volume.valid()) throw std::runtime_error("The build volume shape is invalid");
    // Match the desktop's lateral centering step for a newly loaded model.
    // This is centering, not the multi-object arrangement algorithm.
    const bool identity=!transform || (transform->rotation_x==0 && transform->rotation_y==0 && transform->rotation_z==0 && transform->scale_x==1 && transform->scale_y==1 && transform->scale_z==1 && transform->offset_x==0 && transform->offset_y==0);
    if(center_derived_step) {
        const kepler::ModelTransform unchanged{0,0,0,1,1,1,0,0};
        kepler::place_step_geometry(model,volume,transform?*transform:unchanged);
    } else if(extension==".3mf") {
        if(!identity) throw std::runtime_error("Editing a 3MF project transform is not yet supported; the original layout is preserved");
    } else if(extension==".obj" && (model.objects.size()!=1 || model.objects.front()->instances.size()!=1)) {
        if(!identity) throw std::runtime_error("Transforming a multi-object OBJ is not yet supported");
        auto box=model.bounding_box_exact(); if(!box.defined || !box.min.allFinite() || !box.max.allFinite()) throw std::runtime_error("Invalid model bounds");
        const auto center=volume.bed_center(); Slic3r::Vec3d shift(center.x()-(box.min.x()+box.max.x())/2,center.y()-(box.min.y()+box.max.y())/2,-box.min.z());
        for(auto *object:model.objects) object->translate_instances(shift);
    } else if (transform) kepler::apply_model_transform(model, volume, *transform);
    else {
        for (Slic3r::ModelObject *object : model.objects) object->ensure_on_bed();
        model.center_instances_around_point(volume.bed_center());
    }
    if (model.update_print_volume_state(volume) == 0)
        throw std::runtime_error("No object is completely inside the build volume");
    for (const Slic3r::ModelObject *object : model.objects)
        for (const Slic3r::ModelInstance *instance : object->instances)
            if (instance->print_volume_state != Slic3r::ModelInstancePVS_Inside)
                throw std::runtime_error("An object is not completely inside the build volume");
    if (volume.type() == Slic3r::BuildVolume_Type::Custom) {
        // Upstream's Custom object_state uses the bed's convex hull.  A hull
        // alone can accept a model spanning a concavity, so require the entire
        // XY bounding rectangle to be inside the real polygon. This deliberately
        // rejects some printable shapes, but cannot bridge a concave cutout.
        const auto box = model.bounding_box_exact();
        if (!box.defined || !box.min.allFinite() || !box.max.allFinite() ||
            box.max.x() <= box.min.x() || box.max.y() <= box.min.y())
            throw std::runtime_error("The model has invalid bounds");
        Slic3r::Polygon bounds = Slic3r::Polygon::new_scale({
            {box.min.x(), box.min.y()}, {box.max.x(), box.min.y()},
            {box.max.x(), box.max.y()}, {box.min.x(), box.max.y()}});
        if (!Slic3r::diff(bounds, volume.polygon()).empty())
            throw std::runtime_error("The model's bounds do not fit the actual bed shape");
    }
    print.apply(model, config);
    if (print.empty()) throw std::runtime_error("No printable objects after applying the configuration");
    const auto validation = print.validate();
    if (!validation.string.empty()) throw std::runtime_error(validation.string);
    print.process();
    const std::string result = print.export_gcode(output_file, nullptr, nullptr);
    if (result.empty() || !std::filesystem::is_regular_file(result) || std::filesystem::file_size(result) == 0)
        throw std::runtime_error("OrcaSlicer did not produce a non-empty G-code file");
    return result;
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_keplerslicer_engine_ExperimentalSlicer_sliceStl(
    JNIEnv *env, jclass, jstring model_path, jstring config_path,
    jstring output_path, jstring resources_path, jstring data_path)
{
    try {
        const auto model_file = UtfChars(env, model_path).str();
        const auto config_file = UtfChars(env, config_path).str();
        const auto job_path = UtfChars(env, output_path).str();
        const auto resources = UtfChars(env, resources_path).str();
        const auto data = UtfChars(env, data_path).str();
        if (std::filesystem::path(model_file).extension() != ".stl")
            throw std::invalid_argument("Only lowercase .stl is supported in this experiment");
        if (!std::filesystem::is_regular_file(model_file) || !std::filesystem::is_regular_file(config_file))
            throw std::invalid_argument("Model or resolved INI configuration file not found");
        if (!std::filesystem::is_directory(resources) || !std::filesystem::is_directory(data))
            throw std::invalid_argument("Resource or data directory not found");
        std::lock_guard<std::mutex> guard(engine_mutex);
        // Upstream export uses and may overwrite both the destination and its
        // .tmp sibling. Use a newly-created directory within private app data.
        const auto job = create_job_directory(job_path, data);
        const auto output_file = (job / "result.gcode").string();
        Slic3r::set_resources_dir(resources);
        Slic3r::set_data_dir(data);
        Slic3r::DynamicPrintConfig config = Slic3r::DynamicPrintConfig::full_print_config();
        config.load_from_ini(config_file, Slic3r::ForwardCompatibilitySubstitutionRule::Disable);
        const auto config_errors = config.validate(true);
        if (!config_errors.empty())
            throw std::runtime_error("Invalid print configuration: " + config_errors.begin()->first + ": " + config_errors.begin()->second);

        const std::string result = slice_model(model_file, output_file, config);
        return env->NewStringUTF(result.c_str());
    } catch (const std::exception &error) {
        if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
    } catch (...) {
        if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Unknown slicing error");
    }
    return nullptr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_keplerslicer_engine_ExperimentalSlicer_resolveSystemProfiles(
    JNIEnv *env, jclass, jstring printer_vendor, jstring process_vendor, jstring filament_vendor,
    jstring printer_name, jstring process_name, jstring filament_name,
    jstring resources_path, jstring data_path, jstring job_path)
{
    try {
        const auto resources = UtfChars(env, resources_path).str();
        const auto data = UtfChars(env, data_path).str();
        const auto job_name = UtfChars(env, job_path).str();
        kepler::SystemProfileSelection selection{
            (std::filesystem::path(data) / "system").string(),
            UtfChars(env, printer_vendor).str(), UtfChars(env, process_vendor).str(), UtfChars(env, filament_vendor).str(),
            UtfChars(env, printer_name).str(), UtfChars(env, process_name).str(), UtfChars(env, filament_name).str()};
        if (!std::filesystem::is_directory(resources) || !std::filesystem::is_directory(data))
            throw std::invalid_argument("Resource or private data directory not found");
        std::lock_guard<std::mutex> guard(engine_mutex);
        const auto job = create_job_directory(job_name, data);
        Slic3r::set_resources_dir(resources);
        Slic3r::set_data_dir(data);
        const auto output = (job / "resolved.ini").string();
        kepler::write_system_profile_ini(selection, output);
        return env->NewStringUTF(output.c_str());
    } catch (const std::exception &error) {
        if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
    } catch (...) {
        if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Unknown profile error");
    }
    return nullptr;
}

// Experimental combined path: resolve and slice under a single engine lock.
// This never serializes configuration to INI, preserving upstream values.
extern "C" JNIEXPORT jstring JNICALL
Java_com_keplerslicer_engine_ExperimentalSlicer_sliceSystemProfiles(
    JNIEnv *env, jclass, jstring printer_vendor, jstring process_vendor, jstring filament_vendor,
    jstring printer_name, jstring process_name, jstring filament_name,
    jstring model_path, jstring resources_path, jstring data_path, jstring job_path)
{
    try {
        const auto model_file = UtfChars(env, model_path).str();
        const auto resources = UtfChars(env, resources_path).str();
        const auto data = UtfChars(env, data_path).str();
        const auto job_name = UtfChars(env, job_path).str();
        kepler::SystemProfileSelection selection{
            (std::filesystem::path(data) / "system").string(),
            UtfChars(env, printer_vendor).str(), UtfChars(env, process_vendor).str(), UtfChars(env, filament_vendor).str(),
            UtfChars(env, printer_name).str(), UtfChars(env, process_name).str(), UtfChars(env, filament_name).str()};
        if (std::filesystem::path(model_file).extension() != ".stl")
            throw std::invalid_argument("Only lowercase .stl is supported in this experiment");
        if (!std::filesystem::is_regular_file(model_file))
            throw std::invalid_argument("Model file not found");
        if (!std::filesystem::is_directory(resources) || !std::filesystem::is_directory(data))
            throw std::invalid_argument("Resource or private data directory not found");
        std::lock_guard<std::mutex> guard(engine_mutex);
        const auto job = create_job_directory(job_name, data);
        Slic3r::set_resources_dir(resources);
        Slic3r::set_data_dir(data);
        const Slic3r::DynamicPrintConfig config = kepler::resolve_system_profile_config(selection);
        const std::string result = slice_model(model_file, (job / "result.gcode").string(), config);
        return env->NewStringUTF(result.c_str());
    } catch (const std::exception &error) {
        if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
    } catch (...) {
        if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Unknown slicing error");
    }
    return nullptr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_keplerslicer_engine_ExperimentalSlicer_sliceSystemProfilesTransformed(
    JNIEnv *env, jclass, jstring printer_vendor, jstring process_vendor, jstring filament_vendor,
    jstring printer_name, jstring process_name, jstring filament_name,
    jstring model_path, jstring resources_path, jstring data_path, jstring job_path, jdoubleArray params)
{
    try {
        if (!params || env->GetArrayLength(params) != 8) throw std::invalid_argument("Model transform must contain 8 values");
        jdouble v[8]; env->GetDoubleArrayRegion(params, 0, 8, v);
        if (env->ExceptionCheck()) return nullptr;
        const kepler::ModelTransform transform{v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7]};
        const auto model_file = UtfChars(env, model_path).str();
        const auto resources = UtfChars(env, resources_path).str();
        const auto data = UtfChars(env, data_path).str();
        const auto job_name = UtfChars(env, job_path).str();
        kepler::SystemProfileSelection selection{
            (std::filesystem::path(data) / "system").string(),
            UtfChars(env, printer_vendor).str(), UtfChars(env, process_vendor).str(), UtfChars(env, filament_vendor).str(),
            UtfChars(env, printer_name).str(), UtfChars(env, process_name).str(), UtfChars(env, filament_name).str()};
        if (std::filesystem::path(model_file).extension() != ".stl" || !std::filesystem::is_regular_file(model_file))
            throw std::invalid_argument("STL model file not found");
        if (!std::filesystem::is_directory(resources) || !std::filesystem::is_directory(data))
            throw std::invalid_argument("Resource or private data directory not found");
        std::lock_guard<std::mutex> guard(engine_mutex);
        const auto job = create_job_directory(job_name, data);
        Slic3r::set_resources_dir(resources);
        Slic3r::set_data_dir(data);
        const Slic3r::DynamicPrintConfig config = kepler::resolve_system_profile_config(selection);
        const std::string result = slice_model(model_file, (job / "result.gcode").string(), config, &transform);
        return env->NewStringUTF(result.c_str());
    } catch (const std::exception &error) {
        if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
    } catch (...) {
        if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Unknown slicing error");
    }
    return nullptr;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_keplerslicer_engine_ExperimentalSlicer_listSystemCompatibility(
    JNIEnv *env, jclass, jstring printer_vendor, jstring printer_name, jstring kind,
    jstring process_vendor, jstring process_name, jstring resources_path, jstring data_path)
{
    try {
        const auto vendor = UtfChars(env, printer_vendor).str();
        const auto printer = UtfChars(env, printer_name).str();
        const auto type = UtfChars(env, kind).str();
        const auto processVendor = UtfChars(env, process_vendor).str();
        const auto processName = UtfChars(env, process_name).str();
        const auto resources = UtfChars(env, resources_path).str();
        const auto data = UtfChars(env, data_path).str();
        if (!std::filesystem::is_directory(resources) || !std::filesystem::is_directory(data))
            throw std::invalid_argument("Resource or private data directory not found");
        std::lock_guard<std::mutex> guard(engine_mutex);
        Slic3r::set_resources_dir(resources);
        Slic3r::set_data_dir(data);
        const auto profiles = kepler::list_system_compatibility((std::filesystem::path(data) / "system").string(),
            vendor, printer, type, processVendor, processName);
        if (profiles.size() > 100000) throw std::runtime_error("Too many profiles");
        jclass stringClass = env->FindClass("java/lang/String");
        if (!stringClass) return nullptr;
        jobjectArray output = env->NewObjectArray(static_cast<jsize>(profiles.size() * 3), stringClass, nullptr);
        env->DeleteLocalRef(stringClass);
        if (!output) return nullptr;
        jsize index = 0;
        for (const auto &profile : profiles) {
            for (const auto *value : {&profile.vendor, &profile.name, &profile.status}) {
                jstring item = env->NewStringUTF(value->c_str());
                if (!item) return nullptr;
                env->SetObjectArrayElement(output, index++, item);
                env->DeleteLocalRef(item);
                if (env->ExceptionCheck()) return nullptr;
            }
        }
        return output;
    } catch (const std::exception &error) {
        if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
    } catch (...) {
        if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Unknown compatibility error");
    }
    return nullptr;
}

namespace {
std::string standard_utf8(JNIEnv *env, jstring value) {
    if (!value) throw std::invalid_argument("Missing settings JSON");
    const jsize length = env->GetStringLength(value);
    const jchar *chars = env->GetStringChars(value, nullptr);
    if (!chars) throw std::runtime_error("Could not read settings JSON");
    std::string out;
    try {
        for (jsize n = 0; n < length; ++n) {
            unsigned int cp = chars[n];
            if (cp >= 0xd800 && cp <= 0xdbff) {
                if (++n >= length || chars[n] < 0xdc00 || chars[n] > 0xdfff) throw std::invalid_argument("Invalid Unicode in settings");
                cp = 0x10000 + ((cp - 0xd800) << 10) + (chars[n] - 0xdc00);
            } else if (cp >= 0xdc00 && cp <= 0xdfff) throw std::invalid_argument("Invalid Unicode in settings");
            if (cp < 0x80) out.push_back(static_cast<char>(cp));
            else if (cp < 0x800) { out.push_back(static_cast<char>(0xc0 | (cp >> 6))); out.push_back(static_cast<char>(0x80 | (cp & 63))); }
            else if (cp < 0x10000) { out.push_back(static_cast<char>(0xe0 | (cp >> 12))); out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 63))); out.push_back(static_cast<char>(0x80 | (cp & 63))); }
            else { out.push_back(static_cast<char>(0xf0 | (cp >> 18))); out.push_back(static_cast<char>(0x80 | ((cp >> 12) & 63))); out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 63))); out.push_back(static_cast<char>(0x80 | (cp & 63))); }
        }
    } catch (...) { env->ReleaseStringChars(value, chars); throw; }
    env->ReleaseStringChars(value, chars); return out;
}
kepler::SystemProfileSelection profile_selection(JNIEnv *env, jstring pv, jstring qv, jstring fv,
                                                   jstring pn, jstring qn, jstring fn, const std::string &data) {
    return {(std::filesystem::path(data) / "system").string(), UtfChars(env, pv).str(), UtfChars(env, qv).str(),
            UtfChars(env, fv).str(), UtfChars(env, pn).str(), UtfChars(env, qn).str(), UtfChars(env, fn).str()};
}
void prepare_settings_context(const std::string &resources, const std::string &data) {
    if (!std::filesystem::is_directory(resources) || !std::filesystem::is_directory(data)) throw std::invalid_argument("Resource or private data directory not found");
    Slic3r::set_resources_dir(resources); Slic3r::set_data_dir(data); Slic3r::set_temporary_dir(data);
}
jbyteArray json_bytes(JNIEnv *env,const std::string &json) {
    if(json.size()>static_cast<size_t>(std::numeric_limits<jsize>::max()))throw std::runtime_error("Native snapshot exceeds Java array capacity");
    jbyteArray out=env->NewByteArray(static_cast<jsize>(json.size()));
    if(out)env->SetByteArrayRegion(out,0,static_cast<jsize>(json.size()),reinterpret_cast<const jbyte*>(json.data()));
    return out;
}
kepler::ModelTransform read_transform(JNIEnv *env,jdoubleArray params) {
    if(!params||env->GetArrayLength(params)!=8)throw std::invalid_argument("Expected eight transform values");
    jdouble v[8];env->GetDoubleArrayRegion(params,0,8,v);
    if(env->ExceptionCheck())throw std::runtime_error("Cannot read transform");
    return {v[0],v[1],v[2],v[3],v[4],v[5],v[6],v[7]};
}
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_keplerslicer_engine_ExperimentalSlicer_processSettingsMetadata(
    JNIEnv *env, jclass, jstring pv, jstring qv, jstring fv, jstring pn, jstring qn, jstring fn,
    jstring resources_path, jstring data_path) {
    try {
        const auto resources = UtfChars(env, resources_path).str(), data = UtfChars(env, data_path).str();
        const auto selection = profile_selection(env, pv, qv, fv, pn, qn, fn, data);
        std::lock_guard<std::mutex> guard(engine_mutex); prepare_settings_context(resources, data);
        const auto config = kepler::resolve_system_profile_config(selection);
        const std::string json = kepler::process_settings_metadata_json(config);
        if (json.size() > 4u * 1024u * 1024u) throw std::runtime_error("Settings metadata is too large");
        jbyteArray bytes = env->NewByteArray(static_cast<jsize>(json.size()));
        if (bytes) env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(json.size()), reinterpret_cast<const jbyte *>(json.data()));
        return bytes;
    } catch (const std::exception &e) { if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), e.what()); }
      catch (...) { if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Unknown process settings error"); }
    return nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_keplerslicer_engine_ExperimentalSlicer_validateProcessSettings(
    JNIEnv *env, jclass, jstring pv, jstring qv, jstring fv, jstring pn, jstring qn, jstring fn,
    jstring resources_path, jstring data_path, jstring settings_json) {
    try {
        const auto resources = UtfChars(env, resources_path).str(), data = UtfChars(env, data_path).str();
        const auto settings = standard_utf8(env, settings_json);
        const auto selection = profile_selection(env, pv, qv, fv, pn, qn, fn, data);
        std::lock_guard<std::mutex> guard(engine_mutex); prepare_settings_context(resources, data);
        (void)kepler::resolve_system_profile_config_with_overrides(selection, settings);
    } catch (const std::exception &e) { if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), e.what()); }
      catch (...) { if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Unknown process settings error"); }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_keplerslicer_engine_ExperimentalSlicer_sliceModelWithSettings(
    JNIEnv *env, jclass, jstring pv, jstring qv, jstring fv, jstring pn, jstring qn, jstring fn,
    jstring model_path, jstring resources_path, jstring data_path, jstring job_path, jdoubleArray params, jstring settings_json, jint plate_index) {
    try {
        if (!params || env->GetArrayLength(params) != 8) throw std::invalid_argument("Model transform must contain 8 values");
        jdouble v[8]; env->GetDoubleArrayRegion(params, 0, 8, v); if (env->ExceptionCheck()) return nullptr;
        const kepler::ModelTransform transform{v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7]};
        const auto model = UtfChars(env, model_path).str(), resources = UtfChars(env, resources_path).str(), data = UtfChars(env, data_path).str();
        const auto job_name = UtfChars(env, job_path).str(), settings = standard_utf8(env, settings_json);
        const auto selection = profile_selection(env, pv, qv, fv, pn, qn, fn, data);
        const auto ext=std::filesystem::path(model).extension().string();
        if ((ext!=".stl" && ext!=".obj" && ext!=".3mf") || !std::filesystem::is_regular_file(model)) throw std::invalid_argument("Model file not found or unsupported format");
        if(ext==".stl" && plate_index!=-1) throw std::invalid_argument("STL has no project plates");
        std::lock_guard<std::mutex> guard(engine_mutex); prepare_settings_context(resources, data);
        const auto job = create_job_directory(job_name, data);
        const auto config = kepler::resolve_system_profile_config_with_overrides(selection, settings);
        const auto result = slice_model(model, (job / "result.gcode").string(), config, &transform,plate_index);
        return env->NewStringUTF(result.c_str());
    } catch (const std::exception &e) { if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), e.what()); }
      catch (...) { if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Unknown slicing error"); }
    return nullptr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_keplerslicer_engine_ExperimentalSlicer_sliceSystemProfilesWithSettings(
    JNIEnv *env, jclass clazz, jstring pv, jstring qv, jstring fv, jstring pn, jstring qn, jstring fn,
    jstring model, jstring resources, jstring data, jstring job, jdoubleArray params, jstring settings) {
    return Java_com_keplerslicer_engine_ExperimentalSlicer_sliceModelWithSettings(env,clazz,pv,qv,fv,pn,qn,fn,model,resources,data,job,params,settings,-1);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_keplerslicer_engine_NativeModelIO_inspect(JNIEnv *env,jclass,jstring path,jstring resources_path,jstring data_path,jint plate,jint max_triangles) {
    try {
        const auto model=UtfChars(env,path).str(),resources=UtfChars(env,resources_path).str(),data=UtfChars(env,data_path).str();
        if(!std::filesystem::is_regular_file(model)||max_triangles<1||max_triangles>200000)throw std::invalid_argument("Invalid model or preview size");
        std::lock_guard<std::mutex> guard(engine_mutex); prepare_settings_context(resources,data);
        const auto result=kepler::inspect_model(model,plate,static_cast<size_t>(max_triangles));
        const size_t n=result.metadata_json.size(), f=result.preview_xyz.size();
        if(n>static_cast<size_t>(std::numeric_limits<jsize>::max())-4 || f>(static_cast<size_t>(std::numeric_limits<jsize>::max())-4-n)/4)throw std::runtime_error("Model metadata is too large");
        std::vector<jbyte> bytes(4+n+f*4);
        for(int i=0;i<4;i++)bytes[i]=static_cast<jbyte>((n>>(8*i))&255);
        std::memcpy(bytes.data()+4,result.metadata_json.data(),n);
        for(size_t i=0;i<f;i++){std::uint32_t bits;std::memcpy(&bits,&result.preview_xyz[i],4);for(int j=0;j<4;j++)bytes[4+n+i*4+j]=static_cast<jbyte>((bits>>(8*j))&255);}
        jbyteArray out=env->NewByteArray(static_cast<jsize>(bytes.size()));if(out)env->SetByteArrayRegion(out,0,static_cast<jsize>(bytes.size()),bytes.data());return out;
    } catch(const std::exception &e) {if(!env->ExceptionCheck())env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),e.what());}
      catch(...) {if(!env->ExceptionCheck())env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),"Unknown model inspection error");}
    return nullptr;
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_keplerslicer_engine_ExperimentalSlicer_printerBuildVolume(
    JNIEnv *env, jclass, jstring vendor, jstring name, jstring resources_path, jstring data_path) {
    try {
        const auto v = UtfChars(env, vendor).str(), n = UtfChars(env, name).str();
        const auto resources = UtfChars(env, resources_path).str(), data = UtfChars(env, data_path).str();
        std::unique_lock<std::mutex> guard(engine_mutex, std::try_to_lock);
        if (!guard.owns_lock()) return nullptr;
        prepare_settings_context(resources, data);
        const auto values = kepler::printer_build_volume((std::filesystem::path(data) / "system").string(), v, n);
        jdoubleArray result = env->NewDoubleArray(static_cast<jsize>(values.size()));
        if (result) env->SetDoubleArrayRegion(result, 0, static_cast<jsize>(values.size()), values.data());
        return result;
    } catch (const std::exception &e) { if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), e.what()); }
      catch (...) { if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Unknown printer bed error"); }
    return nullptr;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_keplerslicer_engine_ExperimentalSlicer_projectSettingsSnapshot(JNIEnv *env,jclass,
    jstring pv,jstring qv,jstring fv,jstring pn,jstring qn,jstring fn,jstring resources_path,jstring data_path,
    jstring settings_json,jstring material_edits,jstring model_path,jint plate) {
    try {
        const auto resources=standard_utf8(env,resources_path),data=standard_utf8(env,data_path),path=standard_utf8(env,model_path),settings=standard_utf8(env,settings_json),edits=standard_utf8(env,material_edits);
        const auto selection=profile_selection(env,pv,qv,fv,pn,qn,fn,data);
        std::lock_guard<std::mutex> guard(engine_mutex);prepare_settings_context(resources,data);
        const auto config=kepler::resolve_system_profile_config_with_overrides(selection,settings);
        return json_bytes(env,kepler::project_settings_snapshot_json(path,plate,config,edits));
    } catch(const std::exception &e){if(!env->ExceptionCheck())env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),e.what());}
      catch(...){if(!env->ExceptionCheck())env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),"Unknown project snapshot error");}
    return nullptr;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_keplerslicer_engine_ExperimentalSlicer_resolvedSettingsSnapshot(JNIEnv *env,jclass,
    jstring pv,jstring qv,jstring fv,jstring pn,jstring qn,jstring fn,jstring resources_path,jstring data_path,jstring settings_json) {
    try {
        const auto resources=standard_utf8(env,resources_path),data=standard_utf8(env,data_path),settings=standard_utf8(env,settings_json);
        const auto selection=profile_selection(env,pv,qv,fv,pn,qn,fn,data);
        std::lock_guard<std::mutex> guard(engine_mutex);prepare_settings_context(resources,data);
        return json_bytes(env,kepler::typed_config_snapshot_json(kepler::resolve_system_profile_config_with_overrides(selection,settings)));
    } catch(const std::exception &e){if(!env->ExceptionCheck())env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),e.what());}
      catch(...){if(!env->ExceptionCheck())env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),"Unknown resolved snapshot error");}
    return nullptr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_keplerslicer_engine_ExperimentalSlicer_prepareStep(JNIEnv *env,jclass,jstring source_path,
    jstring resources_path,jstring data_path,jstring job_path,jdouble linear,jdouble angular,jboolean split) {
    try {
        const auto source=standard_utf8(env,source_path),resources=standard_utf8(env,resources_path),data=standard_utf8(env,data_path),job_name=standard_utf8(env,job_path);
        std::lock_guard<std::mutex> guard(engine_mutex);prepare_settings_context(resources,data);
        const auto job=create_job_directory(job_name,data),output=job/"model.3mf";
        const auto metadata=kepler::convert_step_to_3mf(source,output.string(),linear,angular,split);
        std::ofstream meta(job/"step-import.json",std::ios::binary);meta<<metadata;meta.close();if(!meta)throw std::runtime_error("Cannot save STEP tessellation provenance");
        return env->NewStringUTF(output.string().c_str());
    } catch(const std::exception &e){if(!env->ExceptionCheck())env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),e.what());}
      catch(...){if(!env->ExceptionCheck())env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),"Unknown STEP import error");}
    return nullptr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_keplerslicer_engine_ExperimentalSlicer_sliceProjectWithSettings(JNIEnv *env,jclass,
    jstring pv,jstring qv,jstring fv,jstring pn,jstring qn,jstring fn,jstring model_path,jstring resources_path,jstring data_path,
    jstring job_path,jdoubleArray params,jstring settings_json,jstring material_edits,jint plate,jint mode,jboolean allow_mixed) {
    try {
        if(mode< -1||mode>3)throw std::invalid_argument("Unknown project import mode");
        auto transform=read_transform(env,params);
        const auto path=standard_utf8(env,model_path),resources=standard_utf8(env,resources_path),data=standard_utf8(env,data_path),job_name=standard_utf8(env,job_path),settings=standard_utf8(env,settings_json),edits=standard_utf8(env,material_edits);
        const auto selection=profile_selection(env,pv,qv,fv,pn,qn,fn,data);
        std::lock_guard<std::mutex> guard(engine_mutex);prepare_settings_context(resources,data);
        auto config=kepler::resolve_system_profile_config_with_overrides(selection,settings);
        const auto job=create_job_directory(job_name,data);std::string result;
        if(mode==3) {
            if(edits!="{}"||plate!=-1)throw std::invalid_argument("STEP derivative cannot contain project material edits or plate settings");
            kepler::validate_step_snapshot(path);
            result=slice_model(path,(job/"result.gcode").string(),config,&transform,-1,nullptr,allow_mixed,true);
        } else if(std::filesystem::path(path).extension()==".3mf"&&mode>=0) {
            auto candidate=mode==0?kepler::prepare_project_geometry(path,plate,config):kepler::prepare_project_settings(path,plate,config,mode==2);
            if(mode==1||mode==2)candidate.config=kepler::apply_material_edits(candidate.config,edits);
            else if(edits!="{}")throw std::invalid_argument("Geometry import cannot apply project material edits");
            result=slice_model(path,(job/"result.gcode").string(),candidate.config,&transform,candidate.selected_plate,&candidate.model,allow_mixed);
        } else {if(edits!="{}")throw std::invalid_argument("No configured project for material edits");result=slice_model(path,(job/"result.gcode").string(),config,&transform,plate,nullptr,allow_mixed);}
        return env->NewStringUTF(result.c_str());
    } catch(const std::exception &e){if(!env->ExceptionCheck())env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),e.what());}
      catch(...){if(!env->ExceptionCheck())env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),"Unknown project slicing error");}
    return nullptr;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_keplerslicer_engine_ExperimentalSlicer_projectMaterialSettings(JNIEnv *env,jclass,
    jstring pv,jstring qv,jstring fv,jstring pn,jstring qn,jstring fn,jstring resources_path,jstring data_path,jstring settings_json,
    jstring model_path,jint plate,jint mode,jstring material_edits,jint slot,jstring new_colour,jboolean recalculate) {
    try {
        if(mode!=1&&mode!=2)throw std::invalid_argument("Load a 3MF project with its settings before editing materials");
        const auto resources=standard_utf8(env,resources_path),data=standard_utf8(env,data_path),path=standard_utf8(env,model_path),settings=standard_utf8(env,settings_json),edits=standard_utf8(env,material_edits),colour=standard_utf8(env,new_colour);
        const auto selection=profile_selection(env,pv,qv,fv,pn,qn,fn,data);
        std::lock_guard<std::mutex> guard(engine_mutex);prepare_settings_context(resources,data);
        auto project=kepler::prepare_project_settings(path,plate,kepler::resolve_system_profile_config_with_overrides(selection,settings),mode==2);
        auto config=kepler::apply_material_edits(project.config,edits);
        if(slot>=0){
            auto *colours=config.option<Slic3r::ConfigOptionStrings>("filament_colour");
            if(!colours||static_cast<size_t>(slot)>=colours->values.size())throw std::invalid_argument("The selected filament slot no longer exists");
            const auto *saved_matrix=config.opt<Slic3r::ConfigOptionFloats>("flush_volumes_matrix");
            if(!saved_matrix)throw std::runtime_error("Project has no flushing matrix");
            nlohmann::json change={{"colours",colours->values},{"flush_volumes",saved_matrix->values}};change["colours"][slot]=colour;
            config=kepler::apply_material_edits(config,change.dump());
            if(recalculate)config=kepler::recalculate_flushing(config,slot);
        } else if(slot!=-1||!colour.empty())throw std::invalid_argument("Invalid filament slot request");
        auto result=nlohmann::json::parse(kepler::material_snapshot_json(config));
        const auto *colours=config.opt<Slic3r::ConfigOptionStrings>("filament_colour");
        const auto *matrix=config.opt<Slic3r::ConfigOptionFloats>("flush_volumes_matrix");
        if(!colours||!matrix)throw std::runtime_error("Project has no editable filament/flush vectors");
        result["edits"]={{"colours",colours->values},{"flush_volumes",matrix->values}};
        return json_bytes(env,result.dump());
    } catch(const std::exception &e){if(!env->ExceptionCheck())env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),e.what());}
      catch(...){if(!env->ExceptionCheck())env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),"Unknown project materials error");}
    return nullptr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_keplerslicer_engine_ExperimentalSlicer_cloneModelWithPreferences(JNIEnv *env,jclass,
    jstring pv,jstring qv,jstring fv,jstring pn,jstring qn,jstring fn,jstring model_path,jstring resources_path,jstring data_path,
    jstring job_path,jdoubleArray params,jstring settings_json,jint oi,jint ii,jint copies,jboolean arrange) {
    try {
        if(oi<0||ii<0||copies<1)throw std::invalid_argument("Invalid instance clone request");
        const auto path=standard_utf8(env,model_path),resources=standard_utf8(env,resources_path),data=standard_utf8(env,data_path),job_name=standard_utf8(env,job_path),settings=standard_utf8(env,settings_json);
        const auto ext=std::filesystem::path(path).extension().string();
        if(ext!=".stl"&&ext!=".obj")throw std::invalid_argument("Cloning a configured 3MF requires the project-session export path; original preserved");
        auto transform=read_transform(env,params);const auto selection=profile_selection(env,pv,qv,fv,pn,qn,fn,data);
        std::lock_guard<std::mutex> guard(engine_mutex);prepare_settings_context(resources,data);
        auto config=kepler::resolve_system_profile_config_with_overrides(selection,settings);
        if(ext==".stl")validate_stl_before_import(path);
        Slic3r::Model original=Slic3r::Model::read_from_file(path);
        const auto *area=config.opt<Slic3r::ConfigOptionPoints>("printable_area");
        if(!area)throw std::runtime_error("Missing printer build area");
        const auto *extruder_areas=config.opt<Slic3r::ConfigOptionPointsGroups>("extruder_printable_area");
        const auto *extruder_heights=config.opt<Slic3r::ConfigOptionFloatsNullable>("extruder_printable_height");
        const std::vector<std::vector<Slic3r::Vec2d>> empty_areas;
        const std::vector<double> empty_heights;
        Slic3r::BuildVolume volume(area->values,config.opt_float("printable_height"),
            extruder_areas?extruder_areas->values:empty_areas,extruder_heights?extruder_heights->values:empty_heights);
        kepler::apply_model_transform(original,volume,transform);
        auto candidate=kepler::clone_and_arrange(original,config,oi,ii,copies,arrange);
        const auto job=create_job_directory(job_name,data),output=job/"model.3mf";
        if(!Slic3r::store_3mf(output.string().c_str(),&candidate,nullptr,false)||!std::filesystem::is_regular_file(output))throw std::runtime_error("Cannot export cloned model");
        std::ofstream meta(job/"placements.json",std::ios::binary);meta<<kepler::model_placements_json(candidate);meta.close();if(!meta)throw std::runtime_error("Cannot save placement provenance");
        return env->NewStringUTF(output.string().c_str());
    } catch(const std::exception &e){if(!env->ExceptionCheck())env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),e.what());}
      catch(...){if(!env->ExceptionCheck())env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),"Unknown clone/arrange error");}
    return nullptr;
}

namespace {
// Loads the current model for an editing tool and applies the Tools transform. A 3MF is read as geometry only;
// a model with several objects or copies keeps its own placement, so its transform must be the identity.
Slic3r::Model load_editable_model(const std::string &path,const kepler::ModelTransform &t,const Slic3r::DynamicPrintConfig &config) {
    const auto ext=std::filesystem::path(path).extension().string();
    if(ext!=".stl"&&ext!=".obj"&&ext!=".3mf")throw std::invalid_argument("Editing tools need an STL, OBJ or 3MF model");
    if(ext==".stl")validate_stl_before_import(path);
    Slic3r::Model model=ext==".3mf"
        ? Slic3r::Model::read_from_file(path,nullptr,nullptr,Slic3r::LoadStrategy::LoadModel|Slic3r::LoadStrategy::AddDefaultInstances|Slic3r::LoadStrategy::Silence)
        : Slic3r::Model::read_from_file(path);
    if(model.objects.empty())throw std::invalid_argument("The model has no objects");
    const bool identity=t.rotation_x==0&&t.rotation_y==0&&t.rotation_z==0&&t.scale_x==1&&t.scale_y==1&&t.scale_z==1&&t.offset_x==0&&t.offset_y==0;
    if(model.objects.size()==1&&model.objects.front()->instances.size()==1) {
        if(ext!=".3mf"||!identity){
            const auto *area=config.opt<Slic3r::ConfigOptionPoints>("printable_area");
            if(!area)throw std::runtime_error("Missing printer build area");
            const auto *extruder_areas=config.opt<Slic3r::ConfigOptionPointsGroups>("extruder_printable_area");
            const auto *extruder_heights=config.opt<Slic3r::ConfigOptionFloatsNullable>("extruder_printable_height");
            const std::vector<std::vector<Slic3r::Vec2d>> empty_areas;const std::vector<double> empty_heights;
            Slic3r::BuildVolume volume(area->values,config.opt_float("printable_height"),
                extruder_areas?extruder_areas->values:empty_areas,extruder_heights?extruder_heights->values:empty_heights);
            kepler::apply_model_transform(model,volume,t);
        }
    } else if(!identity) throw std::invalid_argument("Reset Move/Rotate/Scale before editing a model with several objects");
    return model;
}
}

// op 1: cut at height a (mm), b = 1 keep upper | 2 keep lower; op 2: text (a = letter height, b = depth, text, font);
// op 3: join all objects into one assembly. Writes job/model.3mf and returns its path; the source is never changed.
extern "C" JNIEXPORT jstring JNICALL
Java_com_keplerslicer_engine_ExperimentalSlicer_editModel(JNIEnv *env,jclass,
    jstring pv,jstring qv,jstring fv,jstring pn,jstring qn,jstring fn,jstring model_path,jstring resources_path,jstring data_path,
    jstring job_path,jdoubleArray params,jstring settings_json,jint op,jdouble a,jdouble b,jstring text,jstring font_path) {
    try {
        const auto path=standard_utf8(env,model_path),resources=standard_utf8(env,resources_path),data=standard_utf8(env,data_path),job_name=standard_utf8(env,job_path),settings=standard_utf8(env,settings_json);
        auto transform=read_transform(env,params);const auto selection=profile_selection(env,pv,qv,fv,pn,qn,fn,data);
        std::lock_guard<std::mutex> guard(engine_mutex);prepare_settings_context(resources,data);
        auto config=kepler::resolve_system_profile_config_with_overrides(selection,settings);
        Slic3r::Model source=load_editable_model(path,transform,config);
        Slic3r::Model edited;
        if(op==1){const int keep=static_cast<int>(b);edited=kepler::cut_model_at_z(source,config,a,(keep&1)!=0,(keep&2)!=0);}
        else if(op==2){if(!text||!font_path)throw std::invalid_argument("Text and font are required");edited=kepler::add_text_object(source,config,standard_utf8(env,text),standard_utf8(env,font_path),a,b);}
        else if(op==3)edited=kepler::assemble_objects(source);
        else throw std::invalid_argument("Unknown model edit");
        const auto job=create_job_directory(job_name,data),output=job/"model.3mf";
        if(!Slic3r::store_3mf(output.string().c_str(),&edited,nullptr,false)||!std::filesystem::is_regular_file(output))throw std::runtime_error("Cannot export the edited model");
        return env->NewStringUTF(output.string().c_str());
    } catch(const std::exception &e){if(!env->ExceptionCheck())env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),e.what());}
      catch(...){if(!env->ExceptionCheck())env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),"Unknown model edit error");}
    return nullptr;
}

// Project 3MF with the resolved printer/process/filament settings and the current placement (upstream writer).
extern "C" JNIEXPORT jstring JNICALL
Java_com_keplerslicer_engine_ExperimentalSlicer_exportProject3mf(JNIEnv *env,jclass,
    jstring pv,jstring qv,jstring fv,jstring pn,jstring qn,jstring fn,jstring model_path,jstring resources_path,jstring data_path,
    jstring job_path,jdoubleArray params,jstring settings_json) {
    try {
        const auto path=standard_utf8(env,model_path),resources=standard_utf8(env,resources_path),data=standard_utf8(env,data_path),job_name=standard_utf8(env,job_path),settings=standard_utf8(env,settings_json);
        auto transform=read_transform(env,params);const auto selection=profile_selection(env,pv,qv,fv,pn,qn,fn,data);
        std::lock_guard<std::mutex> guard(engine_mutex);prepare_settings_context(resources,data);
        auto config=kepler::resolve_system_profile_config_with_overrides(selection,settings);
        Slic3r::Model model=load_editable_model(path,transform,config);
        const auto job=create_job_directory(job_name,data),output=job/"project.3mf";
        kepler::store_project_3mf(model,config,output.string());
        return env->NewStringUTF(output.string().c_str());
    } catch(const std::exception &e){if(!env->ExceptionCheck())env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),e.what());}
      catch(...){if(!env->ExceptionCheck())env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),"Unknown project export error");}
    return nullptr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_keplerslicer_engine_ExperimentalSlicer_setNativeLogLevel(JNIEnv *env,jclass,jstring level) {
    try {const auto requested=standard_utf8(env,level);std::lock_guard<std::mutex> guard(engine_mutex);return env->NewStringUTF(kepler::set_native_log_level(requested).c_str());}
    catch(const std::exception &e){if(!env->ExceptionCheck())env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),e.what());}return nullptr;
}
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_keplerslicer_engine_ExperimentalSlicer_runNativePreferencesSelfTest(JNIEnv *env,jclass,jstring resources_path,jstring data_path) {
    try {
        const auto resources=standard_utf8(env,resources_path),data=standard_utf8(env,data_path);
        std::lock_guard<std::mutex> guard(engine_mutex);
        prepare_settings_context(resources,data);
        auto result=nlohmann::json::parse(kepler::native_preferences_self_test());
        const auto project=nlohmann::json::parse(kepler::project_settings_self_test(data));
        for(const auto &check:project.at("checks")) result["checks"].push_back(check);
        result["count"]=result["checks"].size();
        result["project_checks"]=project.at("count");
        result["passed"]=true;
        return json_bytes(env,result.dump());
    }
    catch(const std::exception &e){if(!env->ExceptionCheck())env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),e.what());}
    catch(...){if(!env->ExceptionCheck())env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),"Unknown native self-test error");}return nullptr;
}
