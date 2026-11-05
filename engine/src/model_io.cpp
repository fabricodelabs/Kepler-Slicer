#include "model_io.hpp"
#include "project_inspector.hpp"
#include "libslic3r/Model.hpp"
#include "libslic3r/Format/bbs_3mf.hpp"
#include "libslic3r/PrintConfig.hpp"
#include <nlohmann/json.hpp>
#include <algorithm>
#include <cmath>
#include <filesystem>
#include <limits>
#include <map>
#include <memory>
#include <set>
#include <stdexcept>

namespace kepler {
namespace {
using json=nlohmann::json;
using Selection=std::set<std::pair<int,int>>;
std::string extension(const std::string &path) {
    auto ext=std::filesystem::path(path).extension().string();
    std::transform(ext.begin(),ext.end(),ext.begin(),[](unsigned char c){return static_cast<char>(std::tolower(c));});return ext;
}
json matrix(const Slic3r::Transform3d &t) {
    json out=json::array();for(int r=0;r<4;r++){json row=json::array();for(int c=0;c<4;c++){double v=t.matrix()(r,c);if(!std::isfinite(v))throw std::runtime_error("Non-finite model transform");row.push_back(v);}out.push_back(row);}return out;
}
json model_index(const Slic3r::Model &model) {
    json objects=json::array();
    for(size_t oi=0;oi<model.objects.size();++oi){auto *o=model.objects[oi];if(!o)throw std::runtime_error("Invalid model object");
        json obj={{"index",oi},{"name",o->name},{"printable",o->printable},{"instances",json::array()},{"volumes",json::array()}};
        for(size_t ii=0;ii<o->instances.size();++ii){auto *i=o->instances[ii];if(!i)throw std::runtime_error("Invalid model instance");obj["instances"].push_back({{"index",ii},{"printable",i->printable},{"matrix",matrix(i->get_matrix())}});}
        for(size_t vi=0;vi<o->volumes.size();++vi){auto *v=o->volumes[vi];if(!v)throw std::runtime_error("Invalid model volume");obj["volumes"].push_back({{"index",vi},{"name",v->name},{"type",Slic3r::ModelVolume::type_to_string(v->type())},{"matrix",matrix(v->get_matrix())},{"triangles",v->mesh().its.indices.size()}});}
        objects.push_back(obj);
    }return objects;
}
Selection selected_instances(const Slic3r::Model &model,const std::vector<Slic3r::PlateData *> &plates,int &plate_index,bool *unresolved=nullptr) {
    Selection selected;
    if(unresolved)*unresolved=false;
    if(!plates.empty()) {
        const Slic3r::PlateData *chosen=nullptr;
        if(plate_index==-1) chosen=plates.front();
        else for(auto *p:plates)if(p && p->plate_index==plate_index){chosen=p;break;}
        if(!chosen)throw std::runtime_error("Requested 3MF plate was not found");
        plate_index=chosen->plate_index;
        for(const auto &p:chosen->objects_and_instances) {
            if(p.first<0 || p.second<0 || static_cast<size_t>(p.first)>=model.objects.size() || !model.objects[p.first] || static_cast<size_t>(p.second)>=model.objects[p.first]->instances.size())throw std::runtime_error("3MF plate contains an invalid object/instance mapping");
            selected.insert(p);
        }
        // The importer fills obj_inst_map and ModelInstance::loaded_id; the GUI
        // fills objects_and_instances only later. Never interpret source IDs as vector indices.
        if(chosen->objects_and_instances.empty() && !chosen->obj_inst_map.empty()) {
            bool bad=false;
            std::map<size_t,std::pair<int,int>> loaded;
            // A missing loaded ID can indicate an instance lost by upstream's
            // object-id-keyed map, so do not guess its plate membership.
            for(size_t oi=0;oi<model.objects.size();++oi)if(model.objects[oi])
                for(size_t ii=0;ii<model.objects[oi]->instances.size();++ii){const auto *instance=model.objects[oi]->instances[ii];
                    if(!instance || instance->loaded_id==0 || oi>static_cast<size_t>(std::numeric_limits<int>::max()) || ii>static_cast<size_t>(std::numeric_limits<int>::max())){bad=true;continue;}
                    if(!loaded.emplace(instance->loaded_id,std::make_pair(static_cast<int>(oi),static_cast<int>(ii))).second)bad=true;}
            for(const auto &mapping:chosen->obj_inst_map) {
                const int instance_index=mapping.second.first, id=mapping.second.second;
                if(id<=0 || instance_index<0){bad=true;break;}
                auto found=loaded.find(static_cast<size_t>(id));
                if(found==loaded.end() || found->second.second!=instance_index || !selected.insert(found->second).second){bad=true;break;}
            }
            if(bad){selected.clear();if(unresolved)*unresolved=true;else throw std::runtime_error("3MF plate object mapping cannot be resolved safely");}
        }
    } else {
        if(plate_index!=-1)throw std::runtime_error("The model has no embedded plate list");
        for(size_t oi=0;oi<model.objects.size();++oi)if(model.objects[oi])for(size_t ii=0;ii<model.objects[oi]->instances.size();++ii)selected.emplace(static_cast<int>(oi),static_cast<int>(ii));
    }
    return selected;
}
ImportedModelInfo build_preview(const Slic3r::Model &model,const std::vector<Slic3r::PlateData *> &plates,int plate,size_t max,json metadata) {
    if(!max || max>200000)throw std::invalid_argument("Preview triangle limit must be 1..200000");
    bool unresolved=false;Selection selected=selected_instances(model,plates,plate,&unresolved);std::uint64_t total=0;std::size_t non_printable=0;
    for(const auto &p:selected){const auto *object=model.objects[p.first];const auto *instance=object->instances[p.second];if(!instance||!object->printable||!instance->printable)++non_printable;}
    for(const auto &p:selected)for(const auto *v:model.objects[p.first]->volumes)if(v && v->is_model_part()) {
        auto n=v->mesh().its.indices.size();if(n>std::numeric_limits<std::uint64_t>::max()-total)throw std::runtime_error("Triangle count overflow");total+=n;
    }
    if(!total) {
        ImportedModelInfo empty;
        metadata["selected_plate"]=plate;metadata["bounds_min"]={0,0,0};metadata["bounds_max"]={0,0,0};metadata["triangle_count"]=0;metadata["preview_triangle_count"]=0;
        metadata["non_printable_instances"]=non_printable;metadata["printable_instances"]=selected.size()-non_printable;metadata["mapping_unresolved"]=unresolved;metadata["units"]="mm";empty.metadata_json=metadata.dump();return empty;
    }
    std::uint64_t stride=total/max+(total%max?1:0), index=0;
    // Material slot of each kept preview triangle as runs [first_triangle, slot]; slot = part extruder, else object extruder, else 1.
    json runs=json::array();int last_slot=-1;std::uint64_t kept=0;
    auto slot_of=[](const Slic3r::ModelConfigObject &c){const auto *o=c.option("extruder");return o?o->getInt():0;};
    ImportedModelInfo result;result.preview_xyz.reserve(static_cast<size_t>(std::min<std::uint64_t>(total,max))*9);
    double low[3]={INFINITY,INFINITY,INFINITY},high[3]={-INFINITY,-INFINITY,-INFINITY};
    for(const auto &p:selected){const auto *object=model.objects[p.first];const auto *inst=object->instances[p.second];if(!inst)throw std::runtime_error("Invalid instance");
        for(const auto *volume:object->volumes)if(volume && volume->is_model_part()) {
            const Slic3r::Transform3d transform=inst->get_matrix()*volume->get_matrix();const auto &mesh=volume->mesh().its;
            int slot=slot_of(volume->config);if(slot<=0)slot=slot_of(object->config);if(slot<=0)slot=1;
            for(const auto &triangle:mesh.indices) {
                bool keep=(index++%stride)==0;
                if(keep){if(slot!=last_slot){runs.push_back({kept,slot});last_slot=slot;}++kept;}
                for(int k=0;k<3;k++) {
                    const auto vi=triangle[k];if(vi<0 || static_cast<size_t>(vi)>=mesh.vertices.size())throw std::runtime_error("Invalid triangle index");
                    const Slic3r::Vec3d xyz=transform*mesh.vertices[vi].cast<double>();
                    for(int a=0;a<3;a++){double v=xyz[a];if(!std::isfinite(v)||std::abs(v)>std::numeric_limits<float>::max())throw std::runtime_error("Invalid model coordinate");low[a]=std::min(low[a],v);high[a]=std::max(high[a],v);if(keep)result.preview_xyz.push_back(static_cast<float>(v));}
                }
            }
        }
    }
    for(int a=0;a<3;a++)if(!std::isfinite(high[a]-low[a]))throw std::runtime_error("Invalid model bounds");
    if(high[0]==low[0]&&high[1]==low[1]&&high[2]==low[2])throw std::runtime_error("Model has no size");
    metadata["selected_plate"]=plate;metadata["bounds_min"]={low[0],low[1],low[2]};metadata["bounds_max"]={high[0],high[1],high[2]};metadata["triangle_count"]=total;metadata["preview_triangle_count"]=result.preview_xyz.size()/9;metadata["non_printable_instances"]=non_printable;metadata["printable_instances"]=selected.size()-non_printable;metadata["mapping_unresolved"]=unresolved;metadata["units"]="mm";metadata["preview_slot_runs"]=runs;
    result.metadata_json=metadata.dump();return result;
}
}
ImportedModelInfo inspect_model(const std::string &path,int plate,size_t max_triangles) {
    const auto ext=extension(path);
    if(ext==".3mf") {
        auto project=inspect_project_3mf(path);json metadata={{"format","3mf"},{"requires_project_semantics",true},{"project_data",json::parse(project.describe_json())}};
        if(const auto *colours=project.project_config().option<Slic3r::ConfigOptionStrings>("filament_colour"))metadata["slot_colours"]=colours->values;
        auto &json_plates=metadata["project_data"]["plates"];
        for(size_t i=0;i<project.plates().size();++i){const auto *p=project.plates()[i];if(!p)throw std::runtime_error("Invalid 3MF plate");int index=p->plate_index;bool unresolved=false;auto selection=selected_instances(project.model(),project.plates(),index,&unresolved);
            json_plates[i]["mapping_unresolved"]=unresolved;json_plates[i]["object_instances"]=json::array();for(const auto &pair:selection)json_plates[i]["object_instances"].push_back({pair.first,pair.second});}
        return build_preview(project.model(),project.plates(),plate,max_triangles,std::move(metadata));
    }
    if(ext==".obj") {
        Slic3r::Model model=Slic3r::Model::read_from_file(path);json metadata={{"format","obj"},{"requires_project_semantics",false},{"objects",model_index(model)}};
        return build_preview(model,{},plate,max_triangles,std::move(metadata));
    }
    throw std::invalid_argument("Only OBJ and 3MF are handled by this importer");
}
Slic3r::Model load_plate_model(const std::string &path,int plate) {
    const auto ext=extension(path);
    if(ext==".obj") {
        if(plate!=-1)throw std::runtime_error("OBJ has no embedded plate list");
        return Slic3r::Model::read_from_file(path);
    }
    if(ext!=".3mf")throw std::invalid_argument("Only OBJ and 3MF are handled by this importer");
    auto project=inspect_project_3mf(path);
    const json description=json::parse(project.describe_json());
    if(description.value("unknown_unit_requires_validation",false) || description.value("non_mm_transform_requires_validation",false))throw std::runtime_error("3MF unit/transform combination requires validation before slicing");
    if(project.plates().size()>1)throw std::runtime_error("Multi-plate 3MF slicing requires plate-origin support; original project has been preserved");
    if(!project.plates().empty() && project.plates().front() && project.plates().front()->plate_index!=0)throw std::runtime_error("This 3MF plate origin is not yet supported for slicing");
    if(!project.project_config().keys().empty() || !project.embedded_presets().empty() || !description.at("embedded_files").empty())throw std::runtime_error("Embedded project profiles are not yet supported for slicing; original project has been preserved");
    if(!project.substitutions().unrecogized_keys.empty() || !project.substitutions().substitutions.empty())throw std::runtime_error("Unrecognized or substituted project settings are not supported for slicing");
    for(const auto *p:project.plates())if(!p || !p->config.keys().empty())throw std::runtime_error("Plate settings are not yet supported for slicing");
    for(const auto &p:project.model().plates_custom_gcodes)if(!p.second.gcodes.empty())throw std::runtime_error("Custom plate G-code is not yet supported for slicing");
    Selection selected=selected_instances(project.model(),project.plates(),plate);
    Slic3r::Model model=project.model();
    for(size_t oi=model.objects.size();oi-->0;) {
        auto *object=model.objects[oi];if(!object)throw std::runtime_error("Invalid model object");
        for(size_t ii=object->instances.size();ii-->0;) {
            auto *instance=object->instances[ii];
            if(!instance || !object->printable || !instance->printable || !selected.count({static_cast<int>(oi),static_cast<int>(ii)}))object->delete_instance(ii);
        }
        if(object->instances.empty())model.delete_object(oi);
    }
    if(model.objects.empty())throw std::runtime_error("Selected plate has no printable instances");
    return model;
}
}
