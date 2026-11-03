#include "project_settings.hpp"
#include "project_inspector.hpp"
#include "native_preferences.hpp"

#include "libslic3r/Format/bbs_3mf.hpp"
#include "libslic3r/Preset.hpp"
#include "libslic3r/PlaceholderParser.hpp"
#include <nlohmann/json.hpp>
#include <miniz.h>

#include <algorithm>
#include <cmath>
#include <limits>
#include <map>
#include <memory>
#include <set>
#include <sstream>
#include <stdexcept>
#include <utility>

namespace kepler {
namespace {
using namespace Slic3r;
using json = nlohmann::json;
using Selection = std::set<std::pair<int,int>>;

const char *type_name(ConfigOptionType type) {
    switch(type) {
    case coFloat:return "coFloat";case coFloats:return "coFloats";
    case coInt:return "coInt";case coInts:return "coInts";
    case coString:return "coString";case coStrings:return "coStrings";
    case coPercent:return "coPercent";case coPercents:return "coPercents";
    case coFloatOrPercent:return "coFloatOrPercent";case coFloatsOrPercents:return "coFloatsOrPercents";
    case coPoint:return "coPoint";case coPoints:return "coPoints";case coPoint3:return "coPoint3";
    case coBool:return "coBool";case coBools:return "coBools";
    case coEnum:return "coEnum";case coEnums:return "coEnums";
    case coPointsGroups:return "coPointsGroups";case coIntsGroups:return "coIntsGroups";
    default:throw std::runtime_error("Unsupported native project option type");
    }
}
double finite(double value) {
    if(!std::isfinite(value))throw std::runtime_error("Non-finite project option value");
    return value;
}
json point(const Vec2d &p) { return json::array({finite(p.x()),finite(p.y())}); }
json point3(const Vec3d &p) { return json::array({finite(p.x()),finite(p.y()),finite(p.z())}); }
template<class T,class Convert> json vector_value(const ConfigOption *option,Convert convert) {
    const auto *v=dynamic_cast<const ConfigOptionVector<T> *>(option);
    if(!v)throw std::runtime_error("Project option vector type mismatch");
    json values=json::array();
    for(size_t i=0;i<v->values.size();++i)
        values.push_back(v->nullable() && v->is_nil(i) ? json(nullptr) : convert(v->values[i]));
    return values;
}
json option_value(const ConfigOption &option) {
    if(option.nullable() && option.is_scalar() && option.is_nil())return nullptr;
    switch(option.type()) {
    case coFloat:case coPercent:return finite(option.getFloat());
    case coInt:return option.getInt();
    case coString:return dynamic_cast<const ConfigOptionString &>(option).value;
    case coBool:return option.getBool();
    case coEnum:return option.serialize();
    case coFloatOrPercent: {
        const auto &v=dynamic_cast<const ConfigOptionFloatOrPercent &>(option);
        return {{"value",finite(v.value)},{"percent",v.percent}};
    }
    case coPoint:return point(dynamic_cast<const ConfigOptionPoint &>(option).value);
    case coPoint3:return point3(dynamic_cast<const ConfigOptionPoint3 &>(option).value);
    case coFloats:case coPercents:return vector_value<double>(&option,[](double v){return json(finite(v));});
    case coInts:return vector_value<int>(&option,[](int v){return json(v);});
    case coStrings:return vector_value<std::string>(&option,[](const std::string &v){return json(v);});
    case coBools:return vector_value<unsigned char>(&option,[](unsigned char v){if(v>1)throw std::runtime_error("Invalid project boolean");return json(bool(v));});
    case coFloatsOrPercents:return vector_value<FloatOrPercent>(&option,[](const FloatOrPercent &v){return json{{"value",finite(v.value)},{"percent",v.percent}};});
    case coPoints:return vector_value<Vec2d>(&option,[](const Vec2d &v){return point(v);});
    case coPointsGroups:return vector_value<Vec2ds>(&option,[](const Vec2ds &v){json p=json::array();for(const auto &q:v)p.push_back(point(q));return p;});
    case coIntsGroups:return vector_value<std::vector<int>>(&option,[](const std::vector<int> &v){return json(v);});
    case coEnums: {
        const auto &v=dynamic_cast<const ConfigOptionVectorBase &>(option);
        const auto tokens=v.vserialize();json out=json::array();
        if(tokens.size()!=v.size())throw std::runtime_error("Invalid enum project vector");
        for(size_t i=0;i<tokens.size();++i)out.push_back(v.nullable() && v.is_nil(i)?json(nullptr):json(tokens[i]));
        return out;
    }
    default:throw std::runtime_error("Unsupported native project option value");
    }
}
json config_snapshot(const ConfigBase &config) {
    std::ostringstream encoded;
    config.save_to_json(encoded,"project_snapshot","project","");
    json preset=json::parse(encoded.str());
    for(const char *metadata:{"name","from","version"})if(!config.has(metadata))preset.erase(metadata);
    json options=json::object();
    for(const auto &key:config.keys()) {
        const auto *value=config.option(key);const auto *def=config.def()->get(key);
        if(!value || !def || value->type()!=def->type || !preset.contains(key))
            throw std::runtime_error("Unknown or inconsistent project setting: "+key);
        options[key]={{"type",type_name(value->type())},{"nullable",value->nullable()},
            {"serialized",value->serialize()},{"value",option_value(*value)},{"preset_value",preset.at(key)}};
    }
    return {{"options",std::move(options)},{"preset_values",std::move(preset)}};
}
json config_snapshot(const ModelConfig &config) { return config_snapshot(config.get()); }
std::string config_string(const ConfigBase &config,const char *key) {
    const auto *v=config.option<ConfigOptionString>(key);return v?v->value:std::string();
}
void validate_embedded_sources(const std::string &path,const json &description,const DynamicPrintConfig &definitions,json *sources=nullptr) {
    if(!description.value("embedded_file_count_matches_parsed_presets",false))
        throw std::runtime_error("An embedded preset was not parsed; full project import would lose settings");
    const auto files=description.at("embedded_files");if(files.empty())return;
    struct Zip { mz_zip_archive archive{};bool opened=false;~Zip(){if(opened)mz_zip_reader_end(&archive);} } zip;
    if(!mz_zip_reader_init_file(&zip.archive,path.c_str(),0))throw std::runtime_error("Cannot reopen embedded project settings");zip.opened=true;
    const std::set<std::string> headers={"name","version","from","type","setting_id","filament_id","description","url","is_custom_defined","instantiation","renamed_from"};
    for(const auto &filename:files) {
        size_t size=0;const auto name=filename.get<std::string>();
        std::unique_ptr<void,decltype(&mz_free)> bytes(mz_zip_reader_extract_file_to_heap(&zip.archive,name.c_str(),&size,0),&mz_free);
        if(!bytes || !size)throw std::runtime_error("Cannot read embedded preset source");
        std::set<std::string> keys;
        const auto callback=[&](int depth,json::parse_event_t event,json &value){
            if(depth==1 && event==json::parse_event_t::key && !keys.insert(value.get<std::string>()).second)
                throw std::runtime_error("Duplicate embedded preset key");
            return true;
        };
        const auto *start=static_cast<const char *>(bytes.get());const auto raw=json::parse(start,start+size,callback);
        if(!raw.is_object())throw std::runtime_error("Embedded preset must contain an Orca JSON object");
        // Upstream does not populate Preset::key_values/setting_id from this
        // loader. Expose the exact detached JSON rather than inventing native
        // preset identities or silently losing source metadata.
        if(sources)sources->push_back({{"path",name},{"source_json",raw}});
        for(auto it=raw.begin();it!=raw.end();++it) {
            if(headers.count(it.key()))continue;
            if(!definitions.def()->get(it.key()))throw std::runtime_error("Unknown embedded preset setting: "+it.key());
            if(!it.value().is_string() && !it.value().is_array())throw std::runtime_error("Invalid embedded preset value type: "+it.key());
        }
    }
}
json differences(const ConfigBase &embedded,const ConfigBase &selected) {
    json result=json::array();std::set<std::string> keys(Preset::printer_options().begin(),Preset::printer_options().end());
    keys.insert("printer_settings_id");
    for(const auto &key:keys) {
        const auto *a=embedded.option(key),*b=selected.option(key);
        if(a && (!b || a->type()!=b->type() || *a!=*b))result.push_back(key);
    }
    return result;
}
const PlateData *choose_plate(const ProjectInspection &project,int &plate) {
    if(project.plates().empty()) {
        if(plate!=-1)throw std::runtime_error("The 3MF has no embedded plate list");
        return nullptr;
    }
    const PlateData *chosen=nullptr;
    if(plate==-1)chosen=project.plates().front();
    else for(const auto *p:project.plates())if(p && p->plate_index==plate){chosen=p;break;}
    if(!chosen)throw std::runtime_error("Requested 3MF plate was not found");
    plate=chosen->plate_index;return chosen;
}
Selection selected_instances(const Model &model,const PlateData *plate) {
    Selection selected;
    if(!plate) {
        for(size_t oi=0;oi<model.objects.size();++oi)if(model.objects[oi])
            for(size_t ii=0;ii<model.objects[oi]->instances.size();++ii)selected.emplace(static_cast<int>(oi),static_cast<int>(ii));
        return selected;
    }
    for(const auto &p:plate->objects_and_instances) {
        if(p.first<0 || p.second<0 || size_t(p.first)>=model.objects.size() || !model.objects[p.first] || size_t(p.second)>=model.objects[p.first]->instances.size())
            throw std::runtime_error("Invalid project plate object/instance mapping");
        if(!selected.insert(p).second)throw std::runtime_error("Duplicate project plate instance");
    }
    if(selected.empty() && !plate->obj_inst_map.empty()) {
        std::map<size_t,std::pair<int,int>> loaded;
        for(size_t oi=0;oi<model.objects.size();++oi)if(model.objects[oi])for(size_t ii=0;ii<model.objects[oi]->instances.size();++ii) {
            const auto *instance=model.objects[oi]->instances[ii];
            if(!instance || !instance->loaded_id || oi>size_t(std::numeric_limits<int>::max()) || ii>size_t(std::numeric_limits<int>::max()) ||
               !loaded.emplace(instance->loaded_id,std::make_pair(static_cast<int>(oi),static_cast<int>(ii))).second)
                throw std::runtime_error("Ambiguous project instance identities");
        }
        for(const auto &mapping:plate->obj_inst_map) {
            const int instance_index=mapping.second.first,id=mapping.second.second;
            auto found=id>0?loaded.find(size_t(id)):loaded.end();
            if(instance_index<0 || found==loaded.end() || found->second.second!=instance_index || !selected.insert(found->second).second)
                throw std::runtime_error("Project plate instance identity cannot be resolved");
        }
    }
    return selected;
}
void validate_config(DynamicPrintConfig config) {
    const auto *technology=config.option("printer_technology");
    if(!technology || technology->type()!=coEnum)
        throw std::runtime_error("Project has no valid printer technology");
    if(technology->getInt()!=ptFFF)
        throw std::runtime_error("Only FFF project configurations are supported");
    // A detached config may originate from FullPrintConfig::defaults(), whose
    // scalar enum is ConfigOptionEnum<PrinterTechnology>. Upstream validate()
    // unconditionally casts this option to ConfigOptionEnumGeneric. Convert
    // only the local validation copy through the real definition first; a
    // type-correct static enum must not become a process-wide null dereference.
    const auto *definition=config.def()->get("printer_technology");
    if(!definition || definition->type!=coEnum || !definition->enum_keys_map)
        throw std::runtime_error("Printer technology definition is unavailable");
    std::unique_ptr<ConfigOption> dynamic_technology(definition->create_default_option());
    if(!dynamic_cast<ConfigOptionEnumGeneric *>(dynamic_technology.get()))
        throw std::runtime_error("Printer technology cannot be validated safely");
    dynamic_technology->set(technology);
    config.set_key_value("printer_technology",dynamic_technology.release());
    const auto *nozzles=config.option<ConfigOptionFloats>("nozzle_diameter");
    if(!nozzles || nozzles->values.empty())throw std::runtime_error("Project has no nozzle profile");
    for(double nozzle:nozzles->values)if(!std::isfinite(nozzle) || nozzle<=0)throw std::runtime_error("Invalid project nozzle diameter");
    const auto errors=config.validate(true);
    if(!errors.empty())throw std::runtime_error("Invalid project configuration: "+errors.begin()->first+": "+errors.begin()->second);
}
void validate_scope(const ConfigBase &scope) {
    // Check types, finite scalars/vectors, nil flags and native option existence.
    // Object option subsets cannot be independently validated as FullPrintConfig.
    (void)config_snapshot(scope);
}
void validate_scope(const ModelConfig &scope) { validate_scope(scope.get()); }
DynamicPrintConfig merge_config(const ProjectInspection &project,const PlateData *plate,const DynamicPrintConfig &selected,bool embedded_printer) {
    DynamicPrintConfig result(selected);
    result.apply(project.project_config());
    if(plate)result.apply(plate->config);
    if(!embedded_printer) {
        auto keys=Preset::printer_options();keys.push_back("printer_settings_id");
        for(const auto &key:keys)if(const auto *option=selected.option(key))result.set_key_value(key,option->clone());
    }
    return result;
}
void validate_compatibility(const ProjectInspection &project,const DynamicPrintConfig &config) {
    Preset printer(Preset::TYPE_PRINTER,config_string(config,"printer_settings_id"));
    printer.config.apply_only(config,Preset::printer_options(),true);
    Preset process(Preset::TYPE_PRINT,config_string(config,"print_settings_id"));
    process.config.apply_only(config,Preset::print_options(),true);
    const auto *filament_ids=config.option<ConfigOptionStrings>("filament_settings_id");
    for(const auto *preset:project.embedded_presets()) {
        if(!preset)throw std::runtime_error("Invalid embedded preset");
        const bool active_process=preset->type==Preset::TYPE_PRINT && preset->name==process.name;
        const bool active_filament=preset->type==Preset::TYPE_FILAMENT && filament_ids &&
            std::find(filament_ids->values.begin(),filament_ids->values.end(),preset->name)!=filament_ids->values.end();
        if(!active_process && !active_filament)continue;
        const auto *printers=preset->config.option<ConfigOptionStrings>("compatible_printers");
        const auto &condition=preset->compatible_printers_condition();
        // Public predicates catch malformed conditions and return true. Parse
        // first and propagate errors so project import cannot fail open.
        if((!printers || printers->values.empty()) && !condition.empty()) {
            DynamicPrintConfig extra;extra.set_key_value("printer_preset",new ConfigOptionString(printer.name));
            if(const auto *nozzles=printer.config.option<ConfigOptionFloats>("nozzle_diameter"))
                extra.set_key_value("num_extruders",new ConfigOptionInt(static_cast<int>(nozzles->values.size())));
            PlaceholderParser::evaluate_boolean_expression(condition,printer.config,&extra);
        }
        const PresetWithVendorProfile p(*preset,preset->vendor),target(printer,nullptr),q(process,nullptr);
        if(!is_compatible_with_printer(p,target))throw std::runtime_error("Embedded preset is incompatible with the chosen printer: "+preset->name);
        if(active_filament) {
            const auto *prints=preset->config.option<ConfigOptionStrings>("compatible_prints");
            const auto &print_condition=preset->compatible_prints_condition();
            if((!prints || prints->values.empty()) && !print_condition.empty())
                PlaceholderParser::evaluate_boolean_expression(print_condition,process.config);
            if(!is_compatible_with_print(p,q,target))throw std::runtime_error("Embedded filament is incompatible with the project process: "+preset->name);
        }
    }
}
void validate_project(const ProjectInspection &project,const json &description,const PlateData *plate,int plate_index,const DynamicPrintConfig &config) {
    if(description.value("unknown_unit_requires_validation",false) || description.value("non_mm_transform_requires_validation",false))
        throw std::runtime_error("3MF units/transforms require validation before applying project settings");
    // Orca's GUI places plates in a grid whose origin is not stored in PlateData.
    // Do not guess its layout from object bounds and silently relocate the model.
    if(project.plates().size()>1 || (plate && plate_index!=0))
        throw std::runtime_error("Multi-plate project origins are not yet supported; original project has been retained");
    if(!project.substitutions().unrecogized_keys.empty() || !project.substitutions().substitutions.empty())
        throw std::runtime_error("Project has unknown or substituted configuration values");
    for(const auto *preset:project.embedded_presets()) {
        if(!preset)throw std::runtime_error("Invalid embedded preset");
        if(preset->loading_substitutions && !preset->loading_substitutions->empty())throw std::runtime_error("Embedded preset required configuration substitution");
        validate_scope(preset->config);
    }
    validate_scope(project.project_config());if(plate)validate_scope(plate->config);
    for(const auto &material:project.model().materials){if(!material.second)throw std::runtime_error("Invalid project material");validate_scope(material.second->config);}
    for(const auto *object:project.model().objects) {
        if(!object)throw std::runtime_error("Invalid project object");validate_scope(object->config);
        const auto &heights=object->layer_height_profile.get();
        if(heights.size()%2)throw std::runtime_error("Incomplete project layer-height profile");
        for(double value:heights)if(!std::isfinite(value))throw std::runtime_error("Non-finite project layer-height profile");
        for(const auto *volume:object->volumes){if(!volume)throw std::runtime_error("Invalid project volume");validate_scope(volume->config);}
        for(const auto &range:object->layer_config_ranges) {
            if(!std::isfinite(range.first.first) || !std::isfinite(range.first.second) || range.first.first>range.first.second)
                throw std::runtime_error("Invalid project layer range");
            validate_scope(range.second);
        }
    }
    // Nozzle assignments, multi-material compatibility and actual print volume
    // are additionally checked by Print::apply/validate in the slicing path.
    validate_config(config);
    validate_compatibility(project,config);
    (void)selected_instances(project.model(),plate);
}
bool has_project_settings(const ProjectInspection &project) {
    if(!project.project_config().keys().empty() || !project.embedded_presets().empty())return true;
    for(const auto *plate:project.plates())if(plate&&!plate->config.keys().empty())return true;
    for(const auto *object:project.model().objects)if(object) {
        if(!object->config.empty() || !object->layer_config_ranges.empty() || !object->layer_height_profile.empty())return true;
        for(const auto *volume:object->volumes)if(volume && (!volume->config.empty() || !volume->supported_facets.empty() || !volume->seam_facets.empty() || !volume->mmu_segmentation_facets.empty() || !volume->fuzzy_skin_facets.empty()))return true;
    }
    for(const auto &material:project.model().materials)if(material.second&&!material.second->config.empty())return true;
    for(const auto &custom:project.model().plates_custom_gcodes)if(!custom.second.gcodes.empty())return true;
    return false;
}
json snapshot(const ProjectInspection &project,const json &description,int plate_index,const PlateData *plate,const DynamicPrintConfig &selected,const std::string &material_edits_json="{}") {
    const std::string sha=description.at("archive_sha256").get<std::string>();
    const auto diff=differences(project.project_config(),selected);
    json out={{"schema",1},{"kind","kepler.project_settings"},{"archive_sha256",sha},{"selected_plate",plate_index},
        {"has_settings",has_project_settings(project)},
        {"printer_diff",!diff.empty()},{"printer_differences",diff},
        {"selected_printer_id",config_string(selected,"printer_settings_id")},
        {"embedded_printer_id",config_string(project.project_config(),"printer_settings_id")},
        {"global",config_snapshot(project.project_config())},{"plate",{{"index",plate_index},{"config",plate?config_snapshot(plate->config):json{{"options",json::object()},{"preset_values",json::object()}}}}},
        {"objects",json::array()},{"embedded_presets",json::array()},{"materials",json::array()},
        {"plate_custom_gcode",description.at("plate_custom_gcode")},
        {"source_bytes_retained",true},{"geometry_only_changes_original",false}};
    for(size_t oi=0;oi<project.model().objects.size();++oi) {
        const auto *object=project.model().objects[oi];if(!object)throw std::runtime_error("Invalid project object");
        const std::string id=sha+":object:"+std::to_string(oi);
        json value={{"id",id},{"index",oi},{"name",object->name},{"config",config_snapshot(object->config)},
            {"volumes",json::array()},{"layer_ranges",json::array()},{"instances",json::array()},
            {"layer_height_profile",object->layer_height_profile.get()}};
        for(size_t vi=0;vi<object->volumes.size();++vi) {
            const auto *v=object->volumes[vi];if(!v)throw std::runtime_error("Invalid project volume");
            value["volumes"].push_back({{"id",id+":volume:"+std::to_string(vi)},{"index",vi},{"config",config_snapshot(v->config)}});
        }
        for(const auto &range:object->layer_config_ranges)value["layer_ranges"].push_back({{"z_min",range.first.first},{"z_max",range.first.second},{"config",config_snapshot(range.second)}});
        for(size_t ii=0;ii<object->instances.size();++ii) {
            const auto *i=object->instances[ii];if(!i)throw std::runtime_error("Invalid project instance");
            value["instances"].push_back({{"id",id+":instance:"+std::to_string(ii)},{"index",ii},{"loaded_id",i->loaded_id}});
        }
        out["objects"].push_back(std::move(value));
    }
    for(const auto &material:project.model().materials) {
        if(!material.second)throw std::runtime_error("Invalid project material");
        out["materials"].push_back({{"id",sha+":material:"+material.first},{"name",material.first},{"config",config_snapshot(material.second->config)}});
    }
    for(const auto *preset:project.embedded_presets()) {
        if(!preset)throw std::runtime_error("Invalid embedded preset");
        out["embedded_presets"].push_back({{"name",preset->name},{"type",Preset::get_type_string(preset->type)},
            {"setting_id",preset->setting_id},{"filament_id",preset->filament_id},{"base_id",preset->base_id},
            {"config",config_snapshot(preset->config)}});
    }
    const auto with_printer=apply_material_edits(merge_config(project,plate,selected,true),material_edits_json),keep_printer=apply_material_edits(merge_config(project,plate,selected,false),material_edits_json);
    out["effective"]=config_snapshot(with_printer);
    out["effective_selected_printer"]=config_snapshot(keep_printer);
    for(const auto &mode:{std::make_pair("embedded_printer",&with_printer),std::make_pair("selected_printer",&keep_printer)}) {
        try{validate_project(project,description,plate,plate_index,*mode.second);out["validation"][mode.first]={{"can_load",true},{"error",""}};}
        catch(const std::exception &e){out["validation"][mode.first]={{"can_load",false},{"error",e.what()}};}
    }
    out["validation"]["can_load"]=out["validation"]["embedded_printer"]["can_load"];
    out["validation"]["error"]=out["validation"]["embedded_printer"]["error"];
    return out;
}
}

std::string typed_config_snapshot_json(const Slic3r::ConfigBase &config) { return config_snapshot(config).dump(); }

std::string project_settings_snapshot_json(const std::string &path,int plate_index,const Slic3r::DynamicPrintConfig &selected,const std::string &material_edits_json) {
    auto project=inspect_project_3mf(path);const auto description=json::parse(project.describe_json());
    const auto *plate=choose_plate(project,plate_index);
    auto out=snapshot(project,description,plate_index,plate,selected,material_edits_json);
    out["embedded_sources"]=json::array();
    try{validate_embedded_sources(path,description,selected,&out["embedded_sources"]);}
    catch(const std::exception &e){
        for(const char *mode:{"embedded_printer","selected_printer"})out["validation"][mode]={{"can_load",false},{"error",e.what()}};
        out["validation"]["can_load"]=false;out["validation"]["error"]=e.what();
    }
    return out.dump();
}

PreparedProject prepare_project_settings(const std::string &path,int plate_index,const Slic3r::DynamicPrintConfig &selected,bool use_embedded_printer) {
    auto project=inspect_project_3mf(path);const auto description=json::parse(project.describe_json());
    const auto *plate=choose_plate(project,plate_index);
    validate_embedded_sources(path,description,selected);
    PreparedProject result;result.config=merge_config(project,plate,selected,use_embedded_printer);
    validate_project(project,description,plate,plate_index,result.config);
    const Selection chosen=selected_instances(project.model(),plate);
    result.model=project.model();result.model.curr_plate_index=plate_index<0?0:plate_index;
    for(size_t oi=result.model.objects.size();oi-->0;) {
        auto *object=result.model.objects[oi];
        for(size_t ii=object->instances.size();ii-->0;) {
            const auto *instance=object->instances[ii];
            if(!instance || !object->printable || !instance->printable || !chosen.count({static_cast<int>(oi),static_cast<int>(ii)}))object->delete_instance(ii);
        }
        if(object->instances.empty())result.model.delete_object(oi);
    }
    if(result.model.objects.empty())throw std::runtime_error("Selected plate has no printable instances");
    result.selected_plate=plate_index;result.snapshot_json=snapshot(project,description,plate_index,plate,selected).dump();
    return result;
}

PreparedProject prepare_project_geometry(const std::string &path,int plate_index,const Slic3r::DynamicPrintConfig &selected) {
    auto project=inspect_project_3mf(path);const auto description=json::parse(project.describe_json());
    const auto *plate=choose_plate(project,plate_index);
    if(description.value("unknown_unit_requires_validation",false) || description.value("non_mm_transform_requires_validation",false))
        throw std::runtime_error("3MF units/transforms require validation before geometry-only import");
    if(project.plates().size()>1 || (plate && plate_index!=0))
        throw std::runtime_error("Multi-plate project origins are not yet supported; original project has been retained");
    validate_config(selected);
    const auto chosen=selected_instances(project.model(),plate);
    PreparedProject result;result.model=project.model();result.config=selected;result.selected_plate=plate_index;
    result.model.curr_plate_index=plate_index<0?0:plate_index;
    result.model.plates_custom_gcodes.clear();
    for(auto &material:result.model.materials)if(material.second)material.second->config.reset();
    for(size_t oi=result.model.objects.size();oi-->0;) {
        auto *object=result.model.objects[oi];
        for(size_t ii=object->instances.size();ii-->0;) {
            const auto *instance=object->instances[ii];
            if(!instance || !object->printable || !instance->printable || !chosen.count({static_cast<int>(oi),static_cast<int>(ii)}))object->delete_instance(ii);
        }
        if(object->instances.empty()){result.model.delete_object(oi);continue;}
        object->config.reset();object->layer_config_ranges.clear();object->layer_height_profile.clear();
        for(auto *volume:object->volumes) {
            if(!volume)throw std::runtime_error("Invalid project geometry volume");
            volume->config.reset();volume->supported_facets.reset();volume->seam_facets.reset();
            volume->mmu_segmentation_facets.reset();volume->fuzzy_skin_facets.reset();
        }
    }
    if(result.model.objects.empty())throw std::runtime_error("Selected plate has no printable geometry");
    result.snapshot_json=json{{"schema",1},{"kind","kepler.project_geometry"},{"archive_sha256",description.at("archive_sha256")},
        {"selected_plate",plate_index},{"source_bytes_retained",true},
        {"discarded_working_copy_scopes",json::array({"global_config","plate_config","object_config","volume_config","material_config","layer_ranges","layer_height_profile","paint","custom_plate_gcode"})}}.dump();
    return result;
}

}
