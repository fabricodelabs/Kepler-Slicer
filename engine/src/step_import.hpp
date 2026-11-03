#pragma once

#include <cmath>
#include <stdexcept>
#include <string>

namespace Slic3r { class Model; class BuildVolume; }

namespace kepler {
struct ModelTransform;

struct StepImportOptions {
    // The pinned Orca STEP importer uses millimetres and radians.
    double linear_deflection_mm = 0.003;
    double angular_deflection_rad = 0.5;
    bool split_compounds = false;

    void validate() const {
        if (!std::isfinite(linear_deflection_mm) || linear_deflection_mm <= 0.0)
            throw std::invalid_argument("STEP linear deflection must be a finite positive millimetre value");
        constexpr double pi = 3.14159265358979323846;
        if (!std::isfinite(angular_deflection_rad) || angular_deflection_rad <= 0.0 || angular_deflection_rad > pi)
            throw std::invalid_argument("STEP angular deflection must be finite and between 0 and pi radians");
    }
};

// Caller holds the shared Orca engine mutex and configures its private resource,
// data and temporary directories. source is a stable private copy of the
// ORIGINAL STEP/STP file. output must be a new private job's nonexistent .3mf.
// The source is never modified. The derived file contains tessellated geometry
// only; project settings are not invented. Returns the applied options and real
// imported geometry statistics as a versioned JSON object.
std::string convert_step_to_3mf(const std::string &source, const std::string &output,
                               double linear_deflection_mm, double angular_deflection_rad,
                               bool split_compounds);

// A private frozen print snapshot contains original model.step/model.stp,
// derived model.3mf, and step-import.json binding both byte digests and options.
// This is checked before the internal STEP-centering slice mode is accepted.
void validate_step_snapshot(const std::string &derived_path);

// STEP has CAD coordinates rather than an intentional printer-plate placement.
// Normalize the real (all-volume) object just like a newly imported STL, then
// apply its saved transform around the selected printer's actual bed center.
void place_step_geometry(Slic3r::Model &model, const Slic3r::BuildVolume &bed,
                         const ModelTransform &transform);

}
