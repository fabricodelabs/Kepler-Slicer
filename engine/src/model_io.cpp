#include "model_io.hpp"
#include "project_inspector.hpp"
#include "libslic3r/Model.hpp"
#include "libslic3r/Format/bbs_3mf.hpp"
#include "libslic3r/PrintConfig.hpp"
#include "libslic3r/TriangleSelector.hpp"
#include <array>
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
ImportedModelInfo build_preview(const Slic3r::Model &model,const std::vector<Slic3r::PlateData *> &plates,int plate,size_t max,json metadata,const Slic3r::DynamicPrintConfig *bed=nullptr) {
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
    // A later plate of a multi-plate project is shown on the bed, as it is sliced (desktop plate grid).
    Slic3r::Vec3d shift(0,0,0);
    if(bed&&plates.size()>1){int chosen=plate<0?0:plate;for(size_t i=0;i<plates.size();++i)if(plates[i]&&plates[i]->plate_index==chosen&&i>0){try{auto o=plate_grid_origin(static_cast<int>(i),static_cast<int>(plates.size()),*bed);shift=Slic3r::Vec3d(o.first,o.second,0);}catch(const std::exception&){}break;}}
    metadata["plate_origin"]={shift.x(),shift.y()};
    std::uint64_t stride=total/max+(total%max?1:0), index=0;
    // Material slot of each kept preview triangle as runs [first_triangle, slot]; slot = part extruder, else object extruder, else 1.
    json runs=json::array();int last_slot=-1;std::uint64_t kept=0;
    // Paint per kept preview triangle (support, seam, colour) as runs [first_triangle, state], and the source of
    // each run of preview triangles [first_preview, object, part, first_source_triangle] for the paint tools.
    json paint_runs[4]={json::array(),json::array(),json::array(),json::array()};int last_paint[4]={0,0,0,0};json source_map=json::array();
    // Editing tools load only the chosen plate of a multi-plate project, so its objects and instances are renumbered.
    std::map<int,int> object_rank;std::map<std::pair<int,int>,int> instance_rank;
    {int next=0;std::map<int,int> per_object;for(const auto &q:selected){if(!object_rank.count(q.first))object_rank[q.first]=next++;instance_rank[q]=per_object[q.first]++;}}
    const bool renumber=plates.size()>1;
    auto slot_of=[](const Slic3r::ModelConfigObject &c){const auto *o=c.option("extruder");return o?o->getInt():0;};
    ImportedModelInfo result;result.preview_xyz.reserve(static_cast<size_t>(std::min<std::uint64_t>(total,max))*9);
    double low[3]={INFINITY,INFINITY,INFINITY},high[3]={-INFINITY,-INFINITY,-INFINITY};
    for(const auto &p:selected){const auto *object=model.objects[p.first];const auto *inst=object->instances[p.second];if(!inst)throw std::runtime_error("Invalid instance");
        for(const auto *volume:object->volumes)if(volume && volume->is_model_part()) {
            const Slic3r::Transform3d transform=inst->get_matrix()*volume->get_matrix();const auto &mesh=volume->mesh().its;
            int slot=slot_of(volume->config);if(slot<=0)slot=slot_of(object->config);if(slot<=0)slot=1;
            const size_t n=mesh.indices.size();
            std::array<std::vector<int8_t>,4> paint;
            const Slic3r::FacetsAnnotation *annotations[4]={&volume->supported_facets,&volume->seam_facets,&volume->mmu_segmentation_facets,&volume->fuzzy_skin_facets};
            for(int k=0;k<4;++k)if(!annotations[k]->empty()){
                paint[k].assign(n,0);Slic3r::TriangleSelector selector(volume->mesh());selector.deserialize(annotations[k]->get_data(),false);
                const int top=k==2?static_cast<int>(Slic3r::EnforcerBlockerType::ExtruderMax):2;
                // A source triangle shows the state that covers most of its area (brush strokes split triangles).
                std::vector<float> best(n,0.f);
                auto area=[](const Slic3r::Vec3f &a,const Slic3r::Vec3f &b,const Slic3r::Vec3f &c){return 0.5f*(b-a).cross(c-a).norm();};
                for(int s=1;s<=top;++s){std::vector<int> src;const auto facets=selector.get_facets_strict(static_cast<Slic3r::EnforcerBlockerType>(s),&src);
                    std::vector<float> covered(n,0.f);
                    for(size_t f=0;f<src.size()&&f<facets.indices.size();++f){const int t=src[f];if(t<0||static_cast<size_t>(t)>=n)continue;const auto &ix=facets.indices[f];covered[t]+=area(facets.vertices[ix[0]],facets.vertices[ix[1]],facets.vertices[ix[2]]);}
                    for(size_t t=0;t<n;++t)if(covered[t]>0){const auto &ix=mesh.indices[t];const float whole=area(mesh.vertices[ix[0]],mesh.vertices[ix[1]],mesh.vertices[ix[2]]);
                        if(covered[t]>best[t]&&covered[t]>=0.5f*whole){best[t]=covered[t];paint[k][t]=static_cast<int8_t>(s);}}
                }
            }
            int part=0;for(const auto *other:object->volumes){if(other==volume)break;if(other->is_model_part())++part;}
            std::uint64_t local=0;bool mapped=false;
            for(const auto &triangle:mesh.indices) {
                const std::uint64_t source=local++;
                bool keep=(index++%stride)==0;
                if(keep){if(!mapped){source_map.push_back({kept,renumber?object_rank[p.first]:p.first,part,source,renumber?instance_rank[p]:p.second});mapped=true;}
                    for(int k=0;k<4;++k){int st=paint[k].empty()?0:paint[k][source];if(st!=last_paint[k]){paint_runs[k].push_back({kept,st});last_paint[k]=st;}}
                    if(slot!=last_slot){runs.push_back({kept,slot});last_slot=slot;}++kept;}
                for(int k=0;k<3;k++) {
                    const auto vi=triangle[k];if(vi<0 || static_cast<size_t>(vi)>=mesh.vertices.size())throw std::runtime_error("Invalid triangle index");
                    const Slic3r::Vec3d xyz=transform*mesh.vertices[vi].cast<double>()-shift;
                    for(int a=0;a<3;a++){double v=xyz[a];if(!std::isfinite(v)||std::abs(v)>std::numeric_limits<float>::max())throw std::runtime_error("Invalid model coordinate");low[a]=std::min(low[a],v);high[a]=std::max(high[a],v);if(keep)result.preview_xyz.push_back(static_cast<float>(v));}
                }
            }
        }
    }
    for(int a=0;a<3;a++)if(!std::isfinite(high[a]-low[a]))throw std::runtime_error("Invalid model bounds");
    if(high[0]==low[0]&&high[1]==low[1]&&high[2]==low[2])throw std::runtime_error("Model has no size");
    metadata["selected_plate"]=plate;metadata["bounds_min"]={low[0],low[1],low[2]};metadata["bounds_max"]={high[0],high[1],high[2]};metadata["triangle_count"]=total;metadata["preview_triangle_count"]=result.preview_xyz.size()/9;metadata["non_printable_instances"]=non_printable;metadata["printable_instances"]=selected.size()-non_printable;metadata["mapping_unresolved"]=unresolved;metadata["units"]="mm";metadata["preview_slot_runs"]=runs;metadata["preview_stride"]=stride;metadata["preview_source_map"]=source_map;metadata["preview_paint_support"]=paint_runs[0];metadata["preview_paint_seam"]=paint_runs[1];metadata["preview_paint_colour"]=paint_runs[2];metadata["preview_paint_fuzzy"]=paint_runs[3];
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
        const Slic3r::DynamicPrintConfig *bed=project.project_config().has("printable_area")?&project.project_config():nullptr;
        return build_preview(project.model(),project.plates(),plate,max_triangles,std::move(metadata),bed);
    }
    if(ext==".obj") {
        Slic3r::Model model=Slic3r::Model::read_from_file(path);json metadata={{"format","obj"},{"requires_project_semantics",false},{"objects",model_index(model)}};
        return build_preview(model,{},plate,max_triangles,std::move(metadata));
    }
    throw std::invalid_argument("Only OBJ and 3MF are handled by this importer");
}
std::pair<double,double> plate_grid_origin(int plate_index,int plate_count,const Slic3r::DynamicPrintConfig &bed) {
    if(plate_index<0||plate_count<=0||plate_index>=plate_count)throw std::invalid_argument("Plate index is outside the project");
    const auto *area=bed.opt<Slic3r::ConfigOptionPoints>("printable_area");
    if(!area||area->values.size()<3)throw std::runtime_error("The printer has no printable area for the plate grid");
    double x0=INFINITY,x1=-INFINITY,y0=INFINITY,y1=-INFINITY;for(const auto &p:area->values){x0=std::min(x0,p.x());x1=std::max(x1,p.x());y0=std::min(y0,p.y());y1=std::max(y1,p.y());}
    const int width=static_cast<int>(x1-x0),depth=static_cast<int>(y1-y0); // PartPlateList keeps whole millimetres
    const float value=std::sqrt(static_cast<float>(plate_count)),rounded=std::round(value);const int cols=value>rounded?static_cast<int>(rounded)+1:static_cast<int>(rounded);
    const int row=plate_index/cols,col=plate_index%cols;
    return {col*width*1.2,-row*depth*1.2};
}
void move_plate_to_origin(Slic3r::Model &model,int plate_index,int plate_count,const Slic3r::DynamicPrintConfig &bed) {
    if(plate_count<=1||plate_index<=0)return;
    const auto origin=plate_grid_origin(plate_index,plate_count,bed);
    for(auto *object:model.objects)if(object){for(auto *instance:object->instances)if(instance)instance->set_offset(instance->get_offset()-Slic3r::Vec3d(origin.first,origin.second,0));object->invalidate_bounding_box();}
}
Slic3r::Model load_project_plate(const std::string &path,int plate,const Slic3r::DynamicPrintConfig &bed) {
    auto project=inspect_project_3mf(path);
    int index=plate;Selection selected=selected_instances(project.model(),project.plates(),index);
    Slic3r::Model model=project.model();
    for(size_t oi=model.objects.size();oi-->0;){auto *object=model.objects[oi];if(!object)throw std::runtime_error("Invalid model object");
        for(size_t ii=object->instances.size();ii-->0;)if(!selected.count({static_cast<int>(oi),static_cast<int>(ii)}))object->delete_instance(ii);
        if(object->instances.empty())model.delete_object(oi);}
    if(model.objects.empty())throw std::runtime_error("Selected plate has no objects");
    for(size_t i=0;i<project.plates().size();++i)if(project.plates()[i]&&project.plates()[i]->plate_index==index){move_plate_to_origin(model,static_cast<int>(i),static_cast<int>(project.plates().size()),bed);break;}
    return model;
}
int project_plate_count(const std::string &path){return static_cast<int>(inspect_project_3mf(path).plates().size());}
Slic3r::Model load_plate_model(const std::string &path,int plate,const Slic3r::DynamicPrintConfig *bed) {
    const auto ext=extension(path);
    if(ext==".obj") {
        if(plate!=-1)throw std::runtime_error("OBJ has no embedded plate list");
        return Slic3r::Model::read_from_file(path);
    }
    if(ext!=".3mf")throw std::invalid_argument("Only OBJ and 3MF are handled by this importer");
    auto project=inspect_project_3mf(path);
    const json description=json::parse(project.describe_json());
    if(description.value("unknown_unit_requires_validation",false) || description.value("non_mm_transform_requires_validation",false))throw std::runtime_error("3MF unit/transform combination requires validation before slicing");
    if(project.plates().size()>1&&!bed)throw std::runtime_error("Multi-plate 3MF slicing needs the printer bed");
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
    if(bed&&project.plates().size()>1){int index=plate<0?0:plate;for(size_t i=0;i<project.plates().size();++i)if(project.plates()[i]&&project.plates()[i]->plate_index==index){move_plate_to_origin(model,static_cast<int>(i),static_cast<int>(project.plates().size()),*bed);break;}}
    return model;
}
}
