#include "project_settings_tests.hpp"
#include "project_settings.hpp"
#include "project_inspector.hpp"
#include "libslic3r/Format/bbs_3mf.hpp"
#include "libslic3r/TriangleMesh.hpp"
#include <nlohmann/json.hpp>
#include <chrono>
#include <filesystem>
#include <set>
#include <stdexcept>

namespace kepler {
std::string project_settings_self_test(const std::string &private_data_root) {
    using namespace Slic3r;
    using json=nlohmann::json;
    namespace fs=std::filesystem;
    const fs::path root=fs::canonical(private_data_root);
    const auto nonce=std::chrono::steady_clock::now().time_since_epoch().count();
    const fs::path job=root/("native-project-checks-"+std::to_string(nonce));
    if(job.parent_path()!=root || !fs::create_directory(job))throw std::runtime_error("Cannot create native project test directory");
    struct Cleanup {fs::path path;~Cleanup(){std::error_code error;fs::remove_all(path,error);}} cleanup{job};
    json result={{"version",1},{"checks",json::array()}};
    auto check=[&](const char *name,bool success,const std::string &diagnostic=std::string()){result["checks"].push_back({{"name",name},{"passed",success}});if(!success)throw std::runtime_error(std::string("Native project check failed: ")+name+(diagnostic.empty()?"":": "+diagnostic));};

    // Match the dynamic option classes supplied by the strict native profile
    // loader instead of copying static enum implementations into the fixture.
    const auto defaults=DynamicPrintConfig::full_print_config();
    DynamicPrintConfig project;
    for(const auto &key:defaults.keys())project.set_deserialize_strict(key,defaults.opt_serialize(key));
    // FFF FullPrintConfig omits this preset-level option. A real resolved
    // printer profile supplies it; the synthetic fixture must do so explicitly.
    project.set_deserialize_strict("printer_technology","FFF");
    project.set_num_filaments(2);
    project.set_key_value("printer_settings_id",new ConfigOptionString("Kepler native test printer"));
    project.set_key_value("print_settings_id",new ConfigOptionString("Kepler native project process"));
    project.set_key_value("filament_settings_id",new ConfigOptionStrings{"PLA black","PLA white"});
    project.set_key_value("filament_type",new ConfigOptionStrings{"PLA","PLA"});
    project.set_key_value("filament_colour",new ConfigOptionStrings{"#000000","#FFFFFF"});
    project.set_key_value("filament_diameter",new ConfigOptionFloats{1.75,1.75});
    project.set_key_value("filament_is_support",new ConfigOptionBools{false,false});
    project.set_key_value("flush_volumes_matrix",new ConfigOptionFloats{0,300,400,0});
    project.set_key_value("nozzle_diameter",new ConfigOptionFloats{0.4});
    project.set_key_value("layer_height",new ConfigOptionFloat(0.2));
    project.set_key_value("printable_area",new ConfigOptionPoints{{0,0},{100,0},{100,100},{0,100}});
    project.set_key_value("printable_height",new ConfigOptionFloat(100));
    project.set_deserialize_strict("print_sequence",ConfigOptionEnum<PrintSequence>(PrintSequence::ByObject).serialize());
    Model model;
    model.set_backup_path((job/"writer-backup").string());
    for(int i=0;i<2;i++) {
        auto *object=model.add_object();object->name="Native project cube "+std::to_string(i+1);
        auto *volume=object->add_volume(make_cube(10.,10.,10.));
        auto *second_volume=object->add_volume(make_cube(2.,2.,2.));
        second_volume->set_offset(Vec3d(12.,0.,0.));
        object->add_instance()->set_offset(Vec3d(20.+30.*i,20.,0.));
        object->config.set_key_value("wall_loops",new ConfigOptionInt(5));
        object->config.set_key_value("extruder",new ConfigOptionInt(i+1));
        volume->config.set_key_value("extruder",new ConfigOptionInt(i+1));
        second_volume->config.set_key_value("extruder",new ConfigOptionInt(i+1));
    }
    model.objects[0]->layer_config_ranges[{0.,5.}].set_key_value("layer_height",new ConfigOptionFloat(0.16));
    // The pinned writer accepts two pairs, but its importer requires strictly
    // more than two pairs (bbs_3mf.cpp:3036). Use a valid three-control-point
    // profile so this is a lossless round-trip test, not malformed test input.
    const std::vector<double> expected_height_profile{0.,0.2,5.,0.16,10.,0.2};
    model.objects[0]->layer_height_profile.set(expected_height_profile);
    std::set<std::pair<int,int>> membership{{0,0},{1,0}};
    PlateData plate(0,membership,false);
    // The actual writer serializes print_sequence as plate metadata. Arbitrary
    // plate.layer_height is not part of that archive contract.
    plate.config.set_key_value("print_sequence",new ConfigOptionEnum<PrintSequence>(PrintSequence::ByLayer));
    const fs::path fixture=job/"two-pla-project.3mf";
    StoreParams params;params.path=fixture.string();params.model=&model;params.config=&project;params.plate_data_list={&plate};
    params.strategy=SaveStrategy::Zip64|SaveStrategy::Silence;
    check("project_real_3mf_writer",store_bbs_3mf(params)&&fs::file_size(fixture)>0);
    auto inspected=inspect_project_3mf(fixture.string());
    const auto first=json::parse(inspected.describe_json());
    const auto original_hash=first.at("archive_sha256").get<std::string>();
    DynamicPrintConfig selected(project);selected.set_key_value("nozzle_diameter",new ConfigOptionFloats{0.6});selected.set_key_value("layer_height",new ConfigOptionFloat(0.12));
    const auto metadata=json::parse(project_settings_snapshot_json(fixture.string(),0,selected));
    // Exercise the previously unsafe static-enum input too, without changing
    // the canonical dynamic fixture used by the rest of the contract checks.
    DynamicPrintConfig static_enum_selected(selected);
    static_enum_selected.set_key_value("printer_technology",new ConfigOptionEnum<PrinterTechnology>(ptFFF));
    const auto static_enum_metadata=json::parse(project_settings_snapshot_json(fixture.string(),0,static_enum_selected));
    check("project_real_snapshot_validation_static_enum_safe",metadata.at("validation").at("embedded_printer").at("can_load").get<bool>()&&static_enum_metadata.at("validation").at("selected_printer").at("can_load").get<bool>());
    check("project_native_typed_float",metadata.at("global").at("options").at("layer_height").at("type")=="coFloat"&&metadata.at("global").at("options").at("layer_height").at("value")==0.2);
    check("project_native_two_material_vectors",metadata.at("effective").at("options").at("filament_colour").at("value").size()==2);
    check("project_native_printer_comparison",metadata.at("printer_diff").get<bool>());
    auto full=prepare_project_settings(fixture.string(),0,selected,true);
    const auto has_nozzle=[](const DynamicPrintConfig &config,double expected){const auto *value=config.option<ConfigOptionFloats>("nozzle_diameter");return value&&!value->values.empty()&&value->values.front()==expected;};
    const auto has_float=[](const DynamicPrintConfig &config,const char *key,double expected){const auto *value=config.option<ConfigOptionFloat>(key);return value&&value->value==expected;};
    const auto has_int=[](const ModelConfig &config,const char *key,int expected){const auto *value=config.get().option<ConfigOptionInt>(key);return value&&value->value==expected;};
    check("project_full_embedded_nozzle",has_nozzle(full.config,0.4));
    const auto *sequence=full.config.option("print_sequence");
    check("project_plate_overrides_global",sequence&&sequence->type()==coEnum&&sequence->getInt()==int(PrintSequence::ByLayer)&&project.option("print_sequence")->getInt()==int(PrintSequence::ByObject));
    check("project_keeps_object_config",full.model.objects.size()==2&&full.model.objects[0]&&has_int(full.model.objects[0]->config,"wall_loops",5));
    check("project_keeps_volume_material_assignment",full.model.objects.size()>1&&full.model.objects[1]&&!full.model.objects[1]->volumes.empty()&&full.model.objects[1]->volumes[0]&&has_int(full.model.objects[1]->volumes[0]->config,"extruder",2));
    bool kept_layers=false;
    json layer_diagnostic={{"prepared_object_count",full.model.objects.size()}};
    if(!full.model.objects.empty()&&full.model.objects[0]) {
        const auto *object=full.model.objects[0];
        const auto &ranges=object->layer_config_ranges;
        const auto range=ranges.find({0.,5.});
        const auto *height=range==ranges.end()?nullptr:range->second.get().option<ConfigOptionFloat>("layer_height");
        const auto &snapshot_object=metadata.at("objects").at(0);
        const auto &snapshot_ranges=snapshot_object.at("layer_ranges");
        kept_layers=range!=ranges.end()&&height&&height->value==0.16&&ranges.size()==1&&
            object->layer_height_profile.get()==expected_height_profile&&
            snapshot_object.at("layer_height_profile")==json(expected_height_profile)&&snapshot_ranges.size()==1&&
            snapshot_ranges.at(0).at("z_min")==0.&&snapshot_ranges.at(0).at("z_max")==5.&&
            snapshot_ranges.at(0).at("config").at("options").at("layer_height").at("value")==0.16;
        layer_diagnostic["prepared_range_count"]=ranges.size();
        layer_diagnostic["prepared_height_profile"]=object->layer_height_profile.get();
        layer_diagnostic["range_0_5_height"]=height?json(height->value):json(nullptr);
        layer_diagnostic["snapshot_range_count"]=snapshot_ranges.size();
        layer_diagnostic["snapshot_height_profile"]=snapshot_object.at("layer_height_profile");
    }
    check("project_keeps_layer_overrides",kept_layers,layer_diagnostic.dump());
    auto keep=prepare_project_settings(fixture.string(),0,selected,false);
    check("project_keep_selected_printer",has_nozzle(keep.config,0.6)&&has_float(keep.config,"layer_height",0.2));
    auto geometry=prepare_project_geometry(fixture.string(),0,selected);
    check("project_geometry_selected_settings",has_float(geometry.config,"layer_height",0.12)&&has_nozzle(geometry.config,0.6));
    bool empty_scopes=!geometry.model.objects.empty();
    for(const auto *object:geometry.model.objects){empty_scopes=empty_scopes&&object&&object->config.empty()&&object->layer_config_ranges.empty()&&object->layer_height_profile.empty();if(object)for(const auto *volume:object->volumes)empty_scopes=empty_scopes&&volume&&volume->config.empty();}
    check("project_geometry_clears_scopes",empty_scopes);
    check("project_geometry_retains_world_placement",!geometry.model.objects.empty()&&geometry.model.objects[0]&&!geometry.model.objects[0]->instances.empty()&&geometry.model.objects[0]->instances[0]&&!full.model.objects[0]->instances.empty()&&full.model.objects[0]->instances[0]&&geometry.model.objects[0]->instances[0]->get_offset().isApprox(full.model.objects[0]->instances[0]->get_offset()));
    check("project_stable_archive_object_identity",metadata.at("objects")[0].at("id")==json::parse(project_settings_snapshot_json(fixture.string(),0,selected)).at("objects")[0].at("id"));
    auto after=inspect_project_3mf(fixture.string());
    check("project_original_archive_unchanged",json::parse(after.describe_json()).at("archive_sha256")==original_hash);
    DynamicPrintConfig invalid(project);invalid.set_key_value("layer_height",new ConfigOptionFloat(-1.));
    const fs::path invalid_fixture=job/"invalid-project.3mf";StoreParams bad=params;bad.path=invalid_fixture.string();bad.config=&invalid;
    check("project_invalid_fixture_writer",store_bbs_3mf(bad));
    const auto invalid_metadata=json::parse(project_settings_snapshot_json(invalid_fixture.string(),0,selected));
    check("project_invalid_snapshot_rejected",!invalid_metadata.at("validation").at("embedded_printer").at("can_load").get<bool>());
    bool rejected=false;try{(void)prepare_project_settings(invalid_fixture.string(),0,selected,true);}catch(const std::exception &){rejected=true;}
    check("project_invalid_config_rejected",rejected);
    check("project_source_survives_rejected_candidate",fs::is_regular_file(fixture)&&json::parse(inspect_project_3mf(fixture.string()).describe_json()).at("archive_sha256")==original_hash);
    result["count"]=result["checks"].size();result["fixtures_are_private_test_data"]=true;
    return result.dump();
}
}
