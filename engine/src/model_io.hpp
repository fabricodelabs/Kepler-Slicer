#pragma once
#include <cstddef>
#include <string>
#include <vector>
namespace Slic3r { class Model; }

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
Slic3r::Model load_plate_model(const std::string &path, int plate_index);
}
