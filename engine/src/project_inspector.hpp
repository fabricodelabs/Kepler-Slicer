#pragma once

#include <cstdint>
#include <memory>
#include <string>
#include <vector>

namespace Slic3r { class Model; class DynamicPrintConfig; class Preset; class BBLProject; struct PlateData; struct ConfigSubstitutionContext; }
namespace kepler {

// Live inspection retains Orca's parsed Model/config/plate/preset objects.
// Exact source bytes remain in the caller-owned private immutable snapshot.
// JSON is a descriptive index, not a lossless project serializer. Destroy
// while the process-wide Orca engine lock is held.
class ProjectInspection {
public:
    ~ProjectInspection();
    ProjectInspection(ProjectInspection &&) noexcept;
    ProjectInspection &operator=(ProjectInspection &&) noexcept;
    ProjectInspection(const ProjectInspection &) = delete;
    ProjectInspection &operator=(const ProjectInspection &) = delete;

    // Original archive remains in the caller-owned private immutable snapshot.
    const Slic3r::Model &model() const;
    const Slic3r::DynamicPrintConfig &project_config() const;
    const Slic3r::ConfigSubstitutionContext &substitutions() const;
    const Slic3r::BBLProject &project_metadata() const;
    const std::vector<Slic3r::PlateData *> &plates() const;
    const std::vector<Slic3r::Preset *> &embedded_presets() const;
    std::string describe_json() const;

private:
    struct Impl;
    std::unique_ptr<Impl> p_;
    explicit ProjectInspection(std::unique_ptr<Impl> impl);
    friend ProjectInspection inspect_project_3mf(const std::string &path);
};

// Caller supplies a private immutable snapshot and holds the common engine
// mutex; Orca resource/data/temp dirs must already be private and configured.
// File bytes are checked before and after loading. This is not a print-ready
// project importer, persistence implementation, or sanitizer.
ProjectInspection inspect_project_3mf(const std::string &path);
}
