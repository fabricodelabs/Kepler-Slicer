#include "project_inspector.hpp"

#include <algorithm>
#include <array>
#include <cerrno>
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <limits>
#include <set>
#include <stdexcept>
#include <utility>

#include <expat.h>
#include <miniz.h>
#include <nlohmann/json.hpp>
#include <openssl/sha.h>
#include "libslic3r/Model.hpp"
#include "libslic3r/Format/bbs_3mf.hpp"
#include "libslic3r/Preset.hpp"
#include "libslic3r/PrintConfig.hpp"
#include "libslic3r/ProjectTask.hpp"

namespace kepler {
namespace {
using json = nlohmann::json;
namespace fs = std::filesystem;
// Expat takes a signed int chunk length. The source file itself is not limited
// to an arbitrary byte/triangle count; exhaustion is reported by allocation.
constexpr std::uint64_t MAX_TOTAL = std::numeric_limits<std::uint64_t>::max();
constexpr std::uint64_t MAX_MODEL = std::numeric_limits<int>::max();
constexpr size_t MAX_VERTICES = std::numeric_limits<size_t>::max(), MAX_TRIANGLES = MAX_VERTICES, MAX_OBJECTS = MAX_VERTICES, MAX_INSTANCES = MAX_VERTICES;

std::uintmax_t archive_size(const std::string &path) {
    if (!fs::is_regular_file(path)) throw std::runtime_error("3MF snapshot not found");
    const auto size = fs::file_size(path);
    if (!size || size > std::numeric_limits<size_t>::max()) throw std::runtime_error("3MF snapshot is empty or cannot be addressed");
    return size;
}
std::string digest_file(const std::string &path) {
    std::ifstream in(path,std::ios::binary); if(!in)throw std::runtime_error("Cannot read 3MF snapshot");
    SHA256_CTX state; SHA256_Init(&state); std::array<char,65536> bytes{};
    while(in) {in.read(bytes.data(),bytes.size()); auto n=in.gcount(); if(n>0)SHA256_Update(&state,bytes.data(),static_cast<size_t>(n));}
    if(!in.eof())throw std::runtime_error("Cannot hash 3MF snapshot");
    unsigned char output[SHA256_DIGEST_LENGTH]; SHA256_Final(output,&state);
    static constexpr char hex[] = "0123456789abcdef";
    std::string out; out.reserve(2 * SHA256_DIGEST_LENGTH);
    for (unsigned char b : output) { out.push_back(hex[b >> 4]); out.push_back(hex[b & 15]); }
    return out;
}
struct ZipGuard { mz_zip_archive z{}; bool open=false; ~ZipGuard() { if(open) mz_zip_reader_end(&z); } };
struct XmlGuard { XML_Parser p=nullptr; ~XmlGuard() { if(p) XML_ParserFree(p); } };
struct ModelBudget {
    XML_Parser parser=nullptr;
    size_t vertices=0,triangles=0,objects=0,items=0,current_vertices=0;
    std::string error,current_unit="millimeter";
    bool current_has_transform=false;
    void fail(const char *why) { if(error.empty()) error=why; XML_StopParser(parser,XML_FALSE); }
};
const char *attribute(const XML_Char **attrs,const char *name) {
    for(const XML_Char **p=attrs;p && p[0];p+=2) if(!std::strcmp(p[0],name)) return p[1]; return nullptr;
}
bool finite_number(const char *text) {
    if(!text || !*text) return false; errno=0; char *end=nullptr; double n=std::strtod(text,&end);
    return end && *end=='\0' && errno!=ERANGE && std::isfinite(n) && std::abs(n)<=10000000;
}
bool valid_index(const char *text,size_t limit) {
    if(!text || !*text || *text=='-') return false; errno=0; char *end=nullptr; auto n=std::strtoull(text,&end,10);
    return end && *end=='\0' && errno!=ERANGE && n<limit;
}
void XMLCALL start(void *user,const XML_Char *name,const XML_Char **attrs) {
    auto &s=*static_cast<ModelBudget *>(user);
    static constexpr const char *CORE="http://schemas.microsoft.com/3dmanufacturing/core/2015/02|";
    if(std::strncmp(name,CORE,std::strlen(CORE))!=0) return;
    const char *local=std::strrchr(name,'|'); local=local ? local+1 : name;
    if (!std::strcmp(local,"model"))
        for(const XML_Char **p=attrs;p && p[0];p+=2) if(!std::strcmp(p[0],"unit")) s.current_unit=p[1];
    if (!std::strcmp(local,"item") || !std::strcmp(local,"component"))
        for(const XML_Char **p=attrs;p && p[0];p+=2) if(!std::strcmp(p[0],"transform")) s.current_has_transform=true;
    if (!std::strcmp(local,"vertex")) {
        ++s.vertices; ++s.current_vertices;
        if(s.vertices>MAX_VERTICES || !finite_number(attribute(attrs,"x")) || !finite_number(attribute(attrs,"y")) || !finite_number(attribute(attrs,"z")))
            s.fail("Invalid or excessive 3MF vertices");
    } else if (!std::strcmp(local,"triangle")) {
        ++s.triangles;
        if(s.triangles>MAX_TRIANGLES || !valid_index(attribute(attrs,"v1"),s.current_vertices) ||
           !valid_index(attribute(attrs,"v2"),s.current_vertices) || !valid_index(attribute(attrs,"v3"),s.current_vertices))
            s.fail("Invalid or excessive 3MF triangles");
    } else if (!std::strcmp(local,"object")) {
        s.current_vertices=0; if(++s.objects>MAX_OBJECTS) s.fail("Too many 3MF objects");
    }
    else if (!std::strcmp(local,"item") && ++s.items>MAX_INSTANCES) s.fail("Too many 3MF instances");
}
void XMLCALL doctype(void *user,const XML_Char *,const XML_Char *,const XML_Char *,int) {
    static_cast<ModelBudget *>(user)->fail("DTD is not permitted in model XML");
}
std::string lowercase(std::string name) {
    for(char &c:name) if(c>='A' && c<='Z') c=static_cast<char>(c-'A'+'a');
    return name;
}
bool bad_path(const std::string &name) {
    if(name.empty() || name[0]=='/' || name.find('\\')!=std::string::npos || name.find(':')!=std::string::npos) return true;
    size_t pos=0;
    while(pos<name.size()) { size_t end=name.find('/',pos); if(end==std::string::npos) end=name.size();
        const auto part=name.substr(pos,end-pos); if(part=="." || part==".." || part.empty()) return true; pos=end+1; }
    return false;
}
struct ArchiveScan {
    json entries=json::array(), model_units=json::array(), embedded_files=json::array();
    std::size_t model_files=0;
    bool any_non_mm=false, any_transform=false, unknown_unit=false;
};
ArchiveScan scan_archive(const std::string &path) {
    ZipGuard g; if(!mz_zip_reader_init_file(&g.z,path.c_str(),0)) throw std::runtime_error("Invalid 3MF ZIP"); g.open=true;
    const mz_uint n=mz_zip_reader_get_num_files(&g.z); if(n>10000) throw std::runtime_error("Too many ZIP entries");
    ArchiveScan result; std::set<std::string> names; std::uint64_t total=0; ModelBudget budget;
    for(mz_uint i=0;i<n;++i) {
        mz_zip_archive_file_stat st{};
        if(!mz_zip_reader_file_stat(&g.z,i,&st) || st.m_is_encrypted || !st.m_is_supported)
            throw std::runtime_error("Unsupported or encrypted ZIP entry");
        if(st.m_uncomp_size>MAX_TOTAL-total) throw std::runtime_error("3MF expanded size overflows"); total+=st.m_uncomp_size;
        if(std::strlen(st.m_filename)>=sizeof(st.m_filename)-1) throw std::runtime_error("3MF path too long");
        const std::string name(st.m_filename), key=lowercase(name);
        if(bad_path(name) || !names.insert(key).second) throw std::runtime_error("Unsafe or duplicate ZIP path");
        result.entries.push_back({{"path",name},{"uncompressed_bytes",st.m_uncomp_size},{"directory",bool(st.m_is_directory)}});
        if(!st.m_is_directory && (key.rfind("metadata/print_setting_",0)==0 || key.rfind("metadata/process_settings_",0)==0 ||
            key.rfind("metadata/filament_settings_",0)==0 || key.rfind("metadata/machine_settings_",0)==0))
            result.embedded_files.push_back(name);
        if(st.m_is_directory || key.size()<6 || key.substr(key.size()-6)!=".model") continue;
        if(st.m_uncomp_size>MAX_MODEL) throw std::runtime_error("3MF model XML is too large for the parser");
        ++result.model_files; size_t xml_size=0;
        std::unique_ptr<void,decltype(&mz_free)> xml(mz_zip_reader_extract_to_heap(&g.z,i,&xml_size,0),&mz_free);
        if(!xml || xml_size>MAX_MODEL) throw std::runtime_error("Cannot read 3MF model XML");
        XmlGuard parser; parser.p=XML_ParserCreateNS(nullptr,'|'); if(!parser.p) throw std::runtime_error("Cannot allocate XML parser");
        budget.parser=parser.p; budget.current_unit="millimeter"; budget.current_has_transform=false;
        XML_SetUserData(parser.p,&budget); XML_SetStartElementHandler(parser.p,start); XML_SetStartDoctypeDeclHandler(parser.p,doctype);
        if(XML_Parse(parser.p,static_cast<const char *>(xml.get()),static_cast<int>(xml_size),XML_TRUE)!=XML_STATUS_OK)
            throw std::runtime_error(budget.error.empty()?"Invalid 3MF model XML":budget.error);
        result.model_units.push_back({{"path",name},{"unit",budget.current_unit},{"has_transform",budget.current_has_transform}});
        result.any_non_mm |= budget.current_unit!="millimeter";
        result.any_transform |= budget.current_has_transform;
        const auto &u=budget.current_unit;
        result.unknown_unit |= u!="millimeter" && u!="micron" && u!="centimeter" && u!="inch" && u!="foot" && u!="meter";
    }
    if(!result.model_files) throw std::runtime_error("No 3MF model XML found");
    return result;
}
template <class Config> json config_description(const Config &config) {
    json out=json::object(); for(const auto &key:config.keys()) out[key]=config.opt_serialize(key); return out;
}
json matrix(const Slic3r::Transform3d &t) {
    json out=json::array();
    for(int r=0;r<4;++r) { json row=json::array(); for(int c=0;c<4;++c) {
        double v=t.matrix()(r,c); if(!std::isfinite(v)) throw std::runtime_error("Non-finite transformation"); row.push_back(v);
    } out.push_back(std::move(row)); }
    return out;
}
}

struct ProjectInspection::Impl {
    std::uintmax_t archive_bytes=0;
    std::string sha256;
    ArchiveScan scan;
    Slic3r::Model model;
    Slic3r::DynamicPrintConfig config;
    Slic3r::ConfigSubstitutionContext substitutions{Slic3r::ForwardCompatibilitySubstitutionRule::Disable};
    Slic3r::PlateDataPtrs plates;
    std::vector<Slic3r::Preset *> presets;
    Slic3r::En3mfType type=Slic3r::En3mfType::From_Other;
    Slic3r::Semver version;
    Slic3r::BBLProject project;
    ~Impl() { Slic3r::release_PlateData_list(plates); for(auto *p:presets) delete p; }
};
ProjectInspection::ProjectInspection(std::unique_ptr<Impl> impl):p_(std::move(impl)) {}
ProjectInspection::~ProjectInspection()=default;
ProjectInspection::ProjectInspection(ProjectInspection &&) noexcept=default;
ProjectInspection &ProjectInspection::operator=(ProjectInspection &&) noexcept=default;
const Slic3r::Model &ProjectInspection::model() const { return p_->model; }
const Slic3r::DynamicPrintConfig &ProjectInspection::project_config() const { return p_->config; }
const Slic3r::ConfigSubstitutionContext &ProjectInspection::substitutions() const { return p_->substitutions; }
const Slic3r::BBLProject &ProjectInspection::project_metadata() const { return p_->project; }
const std::vector<Slic3r::PlateData *> &ProjectInspection::plates() const { return p_->plates; }
const std::vector<Slic3r::Preset *> &ProjectInspection::embedded_presets() const { return p_->presets; }

ProjectInspection inspect_project_3mf(const std::string &path) {
    if(fs::path(path).extension()!=".3mf") throw std::invalid_argument("Expected a private .3mf snapshot");
    auto p=std::make_unique<ProjectInspection::Impl>();
    p->archive_bytes=archive_size(path); p->sha256=digest_file(path); p->scan=scan_archive(path);
    p->model=Slic3r::Model::read_from_archive(path,&p->config,&p->substitutions,p->type,
        Slic3r::LoadStrategy::LoadModel | Slic3r::LoadStrategy::LoadConfig |
        Slic3r::LoadStrategy::LoadAuxiliary | Slic3r::LoadStrategy::AddDefaultInstances | Slic3r::LoadStrategy::Silence,
        &p->plates,&p->presets,&p->version,nullptr,&p->project);
    if(digest_file(path)!=p->sha256) throw std::runtime_error("3MF snapshot changed during inspection");
    return ProjectInspection(std::move(p));
}

std::string ProjectInspection::describe_json() const {
    json doc={{"schema",1},{"archive_sha256",p_->sha256},{"original_archive_bytes",p_->archive_bytes},
              {"source_bytes_retained",true},{"summary_is_lossless",false},{"print_ready",false},
              {"warnings",json::array({"JSON is a descriptive index, not a complete or round-trippable project.",
                 "Preserve the original private archive and the live Orca model/configs; do not slice from this summary.",
                 "Unit/transform semantics and the full project path need runtime and desktop comparison."})},
              {"format",static_cast<int>(p_->type)},{"file_version",p_->version.to_string()},
              {"project_model_id",p_->project.project_model_id},{"project_name",p_->project.project_name},
              {"project_design_id",p_->project.project_design_id},{"project_country_code",p_->project.project_country_code},
              {"archive_entries",p_->scan.entries},
              {"model_units",p_->scan.model_units},
              {"non_mm_transform_requires_validation",p_->scan.any_non_mm && p_->scan.any_transform},
              {"unknown_unit_requires_validation",p_->scan.unknown_unit},
              {"embedded_files",p_->scan.embedded_files},
              {"embedded_file_count_matches_parsed_presets",p_->scan.embedded_files.size()==p_->presets.size()},
              {"embedded_substitutions_not_reported_by_upstream",true},
              {"model_xml_files",p_->scan.model_files},{"project_config",config_description(p_->config)},
              {"unrecognized_config_keys",p_->substitutions.unrecogized_keys},
              {"substitutions_count",p_->substitutions.substitutions.size()}};
    doc["substitutions"]=json::array();
    for(const auto &sub:p_->substitutions.substitutions)
        doc["substitutions"].push_back({{"key",sub.opt_def ? sub.opt_def->opt_key : ""},
            {"old_value",sub.old_value},{"new_value",sub.new_value ? sub.new_value->serialize() : ""}});
    doc["objects"]=json::array();
    for(size_t oi=0;oi<p_->model.objects.size();++oi) {
        const auto *o=p_->model.objects[oi]; if(!o) throw std::runtime_error("Null object");
        json object={{"index",oi},{"name",o->name},{"printable",o->printable},{"auto_drop",o->auto_drop},
                     {"config",config_description(o->config)},{"layer_height_profile",o->layer_height_profile.get()},
                     {"instances",json::array()},{"volumes",json::array()},{"layer_config_ranges",json::array()}};
        for(const auto &range:o->layer_config_ranges)
            object["layer_config_ranges"].push_back({{"z_min",range.first.first},{"z_max",range.first.second},{"config",config_description(range.second)}});
        for(size_t i=0;i<o->instances.size();++i) {
            const auto *instance=o->instances[i]; if(!instance) throw std::runtime_error("Null instance");
            object["instances"].push_back({{"index",i},{"matrix",matrix(instance->get_matrix())},
                {"printable",instance->printable},{"arrange_order",instance->arrange_order},{"loaded_id",instance->loaded_id}});
        }
        for(size_t i=0;i<o->volumes.size();++i) {
            const auto *v=o->volumes[i]; if(!v) throw std::runtime_error("Null volume");
            object["volumes"].push_back({{"index",i},{"name",v->name},{"type",Slic3r::ModelVolume::type_to_string(v->type())},
                {"matrix",matrix(v->get_matrix())},{"material_id",v->material_id()},{"config",config_description(v->config)},
                {"vertices",v->mesh().its.vertices.size()},{"triangles",v->mesh().its.indices.size()},
                {"has_support_paint",!v->supported_facets.empty()},{"has_seam_paint",!v->seam_facets.empty()},
                {"has_mmu_paint",!v->mmu_segmentation_facets.empty()},{"has_fuzzy_paint",!v->fuzzy_skin_facets.empty()}});
        }
        doc["objects"].push_back(std::move(object));
    }
    doc["plates"]=json::array();
    for(const auto *plate:p_->plates) {
        if(!plate) throw std::runtime_error("Null plate");
        json entry={{"index",plate->plate_index},{"name",plate->plate_name},{"locked",plate->locked},
                    {"printer_model_id",plate->printer_model_id},{"nozzle_diameters",plate->nozzle_diameters},
                    {"nozzle_volume_types",plate->nozzle_volume_types},{"config",config_description(plate->config)},
                    {"filament_maps",plate->filament_maps},{"skipped_objects",plate->skipped_objects},
                    {"object_instances",json::array()},{"object_instance_map",json::array()}};
        for(const auto &pair:plate->objects_and_instances) entry["object_instances"].push_back({pair.first,pair.second});
        for(const auto &mapping:plate->obj_inst_map) entry["object_instance_map"].push_back({{"source",mapping.first},{"object",mapping.second.first},{"instance",mapping.second.second}});
        doc["plates"].push_back(std::move(entry));
    }
    doc["embedded_presets"]=json::array();
    for(const auto *preset:p_->presets) {
        if(!preset) throw std::runtime_error("Null embedded preset");
        doc["embedded_presets"].push_back({{"name",preset->name},{"type",Slic3r::Preset::get_type_string(preset->type)},
            {"project_embedded",preset->is_project_embedded},{"alias",preset->alias},{"version",preset->version.to_string()},
            {"setting_id",preset->setting_id},{"filament_id",preset->filament_id},{"base_id",preset->base_id},
            {"renamed_from",preset->renamed_from},{"source_key_values",preset->key_values},{"config",config_description(preset->config)}});
    }
    doc["plate_custom_gcode"]=json::array();
    for(const auto &p:p_->model.plates_custom_gcodes) {
        json value={{"plate_index",p.first},{"mode",static_cast<int>(p.second.mode)},{"items",json::array()}};
        for(const auto &item:p.second.gcodes) value["items"].push_back({{"z",item.print_z},{"type",static_cast<int>(item.type)},
            {"extruder",item.extruder},{"color",item.color},{"extra",item.extra}});
        doc["plate_custom_gcode"].push_back(std::move(value));
    }
    doc["cad_recipe_bytes"]=p_->model.cad_recipe.size();
    return doc.dump();
}
}
