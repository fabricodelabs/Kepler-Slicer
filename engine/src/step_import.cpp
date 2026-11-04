#include "step_import.hpp"
#include "model_transform.hpp"

#include "libslic3r/Model.hpp"
#include "libslic3r/Format/3mf.hpp"

#include <nlohmann/json.hpp>
#include <algorithm>
#include <cctype>
#include <cstdint>
#include <filesystem>
#include <limits>
#include <array>
#include <fstream>
#include <openssl/sha.h>

namespace kepler {
namespace {
std::string lower_extension(const std::string &path) {
    auto extension = std::filesystem::path(path).extension().string();
    std::transform(extension.begin(), extension.end(), extension.begin(),
                   [](unsigned char value) { return static_cast<char>(std::tolower(value)); });
    return extension;
}

struct PartialOutput {
    std::filesystem::path path;
    bool owned = false;
    ~PartialOutput() { if (owned) { std::error_code ignored; std::filesystem::remove(path, ignored); } }
};

std::string file_sha256(const std::filesystem::path &path) {
    std::ifstream input(path,std::ios::binary);
    if(!input)throw std::runtime_error("Cannot verify STEP print snapshot");
    SHA256_CTX state;SHA256_Init(&state);std::array<char,65536> bytes{};
    while(input){input.read(bytes.data(),bytes.size());const auto count=input.gcount();if(count>0)SHA256_Update(&state,bytes.data(),static_cast<std::size_t>(count));}
    if(!input.eof())throw std::runtime_error("Cannot hash STEP print snapshot");
    unsigned char digest[SHA256_DIGEST_LENGTH];SHA256_Final(digest,&state);
    static constexpr char hex[]="0123456789abcdef";std::string result;
    for(unsigned char value:digest){result.push_back(hex[value>>4]);result.push_back(hex[value&15]);}
    return result;
}
}

void validate_step_snapshot(const std::string &derived_path) {
    using json=nlohmann::json;namespace fs=std::filesystem;
    const fs::path derived=fs::canonical(derived_path),directory=derived.parent_path();
    if(derived.filename()!="model.3mf"||!fs::is_regular_file(derived))throw std::invalid_argument("Invalid derived STEP print snapshot");
    const fs::path proof_path=directory/"step-import.json";
    if(!fs::is_regular_file(proof_path)||!fs::file_size(proof_path)||fs::file_size(proof_path)>16384)throw std::runtime_error("Missing STEP print provenance");
    std::ifstream input(proof_path,std::ios::binary);const json proof=json::parse(input);
    if(proof.at("schema")!="kepler.step-derivative.v1")throw std::runtime_error("Unknown STEP print provenance");
    const std::string original_name=proof.at("original_file").get<std::string>();
    if(original_name!="model.step"&&original_name!="model.stp")throw std::invalid_argument("Invalid STEP source snapshot name");
    const fs::path source=fs::canonical(directory/original_name);
    if(source.parent_path()!=directory||!fs::is_regular_file(source)||fs::file_size(source)!=proof.at("source_bytes").get<std::uintmax_t>()||
       fs::file_size(derived)!=proof.at("derived_bytes").get<std::uintmax_t>()||file_sha256(source)!=proof.at("source_sha256").get<std::string>()||
       file_sha256(derived)!=proof.at("derived_sha256").get<std::string>())throw std::runtime_error("STEP source or derived snapshot changed before slicing");
    const auto &options=proof.at("options"),&native=proof.at("native");
    if(options.at("schema")!="kepler.step-options.v1"||!options.at("linear_mm").is_number()||!options.at("angular_rad").is_number()||!options.at("split").is_boolean())throw std::runtime_error("Invalid STEP snapshot options");
    const StepImportOptions saved{options.at("linear_mm").get<double>(),options.at("angular_rad").get<double>(),options.at("split").get<bool>()};saved.validate();
    if(native.at("schema")!="kepler.step-import.v1"||native.at("units")!="mm"||native.at("triangle_count").get<std::uint64_t>()==0||
       native.at("linear_deflection_mm").get<double>()!=saved.linear_deflection_mm||native.at("angular_deflection_rad").get<double>()!=saved.angular_deflection_rad||
       native.at("split_compounds").get<bool>()!=saved.split_compounds)throw std::runtime_error("STEP tessellation and snapshot options differ");
}

void place_step_geometry(Slic3r::Model &model,const Slic3r::BuildVolume &bed,const ModelTransform &transform) {
    // Upstream's STEP reader makes one object containing all CAD volumes, with a
    // default instance. Do not silently change behavior for a different layout.
    if(model.objects.size()!=1||!model.objects.front()||model.objects.front()->instances.size()!=1)
        throw std::runtime_error("STEP print placement requires one imported CAD object and one instance");
    apply_model_transform(model,bed,transform);
}

std::string convert_step_to_3mf(const std::string &source, const std::string &output,
                               double linear_deflection_mm, double angular_deflection_rad,
                               bool split_compounds) {
    const StepImportOptions options{linear_deflection_mm, angular_deflection_rad, split_compounds};
    options.validate();
    const auto source_extension = lower_extension(source);
    if (source_extension != ".step" && source_extension != ".stp")
        throw std::invalid_argument("STEP import requires an original STEP or STP file");
    if (!std::filesystem::is_regular_file(source))
        throw std::invalid_argument("STEP source is not a regular private file");
    if (lower_extension(output) != ".3mf" || !std::filesystem::is_directory(std::filesystem::path(output).parent_path()))
        throw std::invalid_argument("STEP output must be a new 3MF in an existing private job directory");
    PartialOutput partial{output + ".step-import-pending"};
    if (std::filesystem::exists(output) || std::filesystem::exists(partial.path))
        throw std::invalid_argument("STEP output already exists");
    partial.owned = true;

    // This is the desktop's real OCCT read/mesh route, including its unit and
    // assembly transforms. load_step() is only declared in this pinned revision;
    // Model::read_from_step is the implemented entry point used by desktop.
    Slic3r::Model model = Slic3r::Model::read_from_step(
        source, Slic3r::LoadStrategy::AddDefaultInstances, nullptr, nullptr, nullptr,
        options.linear_deflection_mm, options.angular_deflection_rad, options.split_compounds);
    if (model.objects.empty()) throw std::runtime_error("STEP produced no model objects");

    std::uint64_t triangles = 0, volumes = 0, instances = 0;
    for (const auto *object : model.objects) {
        if (!object) throw std::runtime_error("STEP produced an invalid model object");
        instances += object->instances.size();
        for (const auto *volume : object->volumes) {
            if (!volume) throw std::runtime_error("STEP produced an invalid model volume");
            if (!volume->is_model_part()) continue;
            const auto &mesh = volume->mesh().its;
            if (mesh.indices.size() > std::numeric_limits<std::uint64_t>::max() - triangles)
                throw std::runtime_error("STEP triangle count overflow");
            triangles += mesh.indices.size();
            ++volumes;
            for (const auto &vertex : mesh.vertices)
                if (!vertex.allFinite()) throw std::runtime_error("STEP produced a non-finite mesh vertex");
            for (const auto &triangle : mesh.indices)
                for (int corner = 0; corner < 3; ++corner)
                    if (triangle[corner] < 0 || static_cast<std::size_t>(triangle[corner]) >= mesh.vertices.size())
                        throw std::runtime_error("STEP produced an invalid triangle index");
        }
    }
    if (!triangles || !volumes || !instances) throw std::runtime_error("STEP produced no instanced printable geometry");
    const auto bounds = model.bounding_box_exact();
    if (!bounds.defined || !bounds.min.allFinite() || !bounds.max.allFinite() ||
        !(bounds.max - bounds.min).allFinite() || (bounds.max - bounds.min).maxCoeff() <= 0.0)
        throw std::runtime_error("STEP produced invalid model bounds");

    // No global preset/config object is passed: the source is CAD geometry, not
    // a print project. Source names, object/volume boundaries and transforms are
    // retained by upstream's own exporter. Export into a partial before publication.
    if (!Slic3r::store_3mf(partial.path.string().c_str(), &model, nullptr, false) ||
        !std::filesystem::is_regular_file(partial.path) || std::filesystem::file_size(partial.path) == 0)
        throw std::runtime_error("Could not preserve STEP tessellation as a geometry 3MF");
    if (std::filesystem::exists(output)) throw std::runtime_error("STEP output appeared during import");
    std::filesystem::rename(partial.path, output);

    return nlohmann::json{
        {"schema", "kepler.step-import.v1"}, {"source_format", "step"}, {"units", "mm"},
        {"linear_deflection_mm", options.linear_deflection_mm},
        {"angular_deflection_rad", options.angular_deflection_rad}, {"split_compounds", options.split_compounds},
        {"object_count", model.objects.size()}, {"volume_count", volumes}, {"instance_count", instances},
        {"triangle_count", triangles},
        {"bounds_min", {bounds.min.x(), bounds.min.y(), bounds.min.z()}},
        {"bounds_max", {bounds.max.x(), bounds.max.y(), bounds.max.z()}}
    }.dump();
}

}
