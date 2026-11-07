#pragma once
#include <cstddef>
#include <string>
#include <vector>
namespace Slic3r { class Model; class DynamicPrintConfig; }

namespace kepler {
struct ImportedModelInfo {
    std::string metadata_json;
    std::vector<float> preview_xyz; // world coordinates in millimetres, triangle soup
};
// Caller must hold the shared upstream engine mutex and initialize private data,
// resources and temporary directories. path must be a stable, private copy of
// the ORIGINAL file. Preview is sampled; never slice from the returned vertices.
// -1 chooses the first embedded plate or all geometry when no plate is present.
ImportedModelInfo inspect_model(const std::string &path, int plate_index, std::size_t max_triangles);
// Reopens the original snapshot and returns all printable instances of one
// plate. Rejects projects with embedded global/plate configs, custom plate
// G-code, or multiple plate origins until these semantics are implemented.
Slic3r::Model load_plate_model(const std::string &path, int plate_index, const Slic3r::DynamicPrintConfig *bed = nullptr);
// Desktop plate grid (PartPlateList): plate i sits at column i % cols, row i / cols, cols = ceil(sqrt(count)),
// spaced by 1.2 x the whole-millimetre width/depth of the printable area. Returns {x, y} of that origin.
std::pair<double,double> plate_grid_origin(int plate_index, int plate_count, const Slic3r::DynamicPrintConfig &bed);
// Moves the kept instances of a plate back onto the bed (subtracts the plate origin).
// Editing tools on a multi-plate project work on one plate: its objects (with their settings and paint) on the bed.
Slic3r::Model load_project_plate(const std::string &path, int plate_index, const Slic3r::DynamicPrintConfig &bed);
int project_plate_count(const std::string &path);
void move_plate_to_origin(Slic3r::Model &model, int plate_index, int plate_count, const Slic3r::DynamicPrintConfig &bed);
// op 7 deletes the plate, op 8 moves it to the front; writes the whole project to output and returns it.
std::string edit_project_plates(const std::string &path, int op, int plate_index, const Slic3r::DynamicPrintConfig &bed, const std::string &output);
}
