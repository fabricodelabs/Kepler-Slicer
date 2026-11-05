#pragma once

#include <cmath>
#include <stdexcept>
#include "libslic3r/BuildVolume.hpp"
#include "libslic3r/Model.hpp"

// Isolated integration candidate. The caller must preflight the STL first,
// hold the engine mutex, then run the existing volume / Print validation after
// this function. Never call the untransformed centering sequence afterward.
namespace kepler {
struct ModelTransform {
    double rotation_x, rotation_y, rotation_z; // radians
    double scale_x, scale_y, scale_z;
    double offset_x, offset_y; // mm from the printable bounding-box center
};

inline void apply_model_transform(Slic3r::Model &model, const Slic3r::BuildVolume &bed,
                                  const ModelTransform &t)
{
    const double values[] = {t.rotation_x, t.rotation_y, t.rotation_z, t.scale_x, t.scale_y, t.scale_z, t.offset_x, t.offset_y};
    for (double v : values) if (!std::isfinite(v)) throw std::invalid_argument("Non-finite model transformation");
    if (t.scale_x < .001 || t.scale_y < .001 || t.scale_z < .001 ||
        t.scale_x > 100 || t.scale_y > 100 || t.scale_z > 100 ||
        std::abs(t.offset_x) > 10000 || std::abs(t.offset_y) > 10000 ||
        std::abs(t.rotation_x) > 3.142 || std::abs(t.rotation_y) > 3.142 || std::abs(t.rotation_z) > 3.142)
        throw std::invalid_argument("Model transformation is outside supported limits");
    if (!bed.valid() || model.objects.size() != 1 || model.objects.front()->instances.size() != 1)
        throw std::invalid_argument("One STL object and one instance are required");
    auto *object = model.objects.front();
    auto *instance = object->instances.front();
    // Rotate and scale around the raw model bounding-box center, like the preview.
    object->center_around_origin(false);
    instance->set_rotation(Slic3r::Vec3d(t.rotation_x, t.rotation_y, t.rotation_z));
    instance->set_scaling_factor(Slic3r::Vec3d(t.scale_x, t.scale_y, t.scale_z));
    object->invalidate_bounding_box();
    const auto bounds = object->instance_bounding_box(0, false);
    if (!bounds.defined || !bounds.min.allFinite() || !bounds.max.allFinite())
        throw std::runtime_error("Invalid transformed model bounds");
    const auto size = bounds.size();
    if ((size.array() <= 0).any() || (size.array() > 10000).any())
        throw std::runtime_error("Transformed model dimensions must be greater than zero and no more than 10000 mm");
    object->ensure_on_bed(false);
    model.center_instances_around_point(bed.bed_center());
    object->translate_instances(Slic3r::Vec3d(t.offset_x, t.offset_y, 0.0));
}

// Projects (several objects, original layout): the transform moves the whole plate as one group.
// Rotation and scale act around the group's bounding-box centre; offset_x/y move it from where it was;
// afterwards the group is put back on the bed.
inline void apply_group_transform(Slic3r::Model &model, const ModelTransform &t)
{
    const double values[] = {t.rotation_x, t.rotation_y, t.rotation_z, t.scale_x, t.scale_y, t.scale_z, t.offset_x, t.offset_y};
    for (double v : values) if (!std::isfinite(v)) throw std::invalid_argument("Non-finite model transformation");
    if (t.scale_x < .001 || t.scale_y < .001 || t.scale_z < .001 || t.scale_x > 100 || t.scale_y > 100 || t.scale_z > 100 ||
        std::abs(t.offset_x) > 10000 || std::abs(t.offset_y) > 10000 ||
        std::abs(t.rotation_x) > 3.142 || std::abs(t.rotation_y) > 3.142 || std::abs(t.rotation_z) > 3.142)
        throw std::invalid_argument("Model transformation is outside supported limits");
    const auto box = model.bounding_box_exact();
    if (!box.defined || !box.min.allFinite() || !box.max.allFinite()) throw std::runtime_error("Invalid project bounds");
    const Slic3r::Vec3d c = box.center();
    Slic3r::Transform3d m = Slic3r::Transform3d::Identity();
    m.translate(Slic3r::Vec3d(c.x() + t.offset_x, c.y() + t.offset_y, c.z()));
    m.rotate(Eigen::AngleAxisd(t.rotation_z, Slic3r::Vec3d::UnitZ()));
    m.rotate(Eigen::AngleAxisd(t.rotation_y, Slic3r::Vec3d::UnitY()));
    m.rotate(Eigen::AngleAxisd(t.rotation_x, Slic3r::Vec3d::UnitX()));
    m.scale(Slic3r::Vec3d(t.scale_x, t.scale_y, t.scale_z));
    m.translate(-c);
    for (auto *object : model.objects) {
        for (auto *instance : object->instances) instance->set_transformation(Slic3r::Geometry::Transformation(m * instance->get_matrix()));
        object->invalidate_bounding_box();
    }
    const auto moved = model.bounding_box_exact();
    if (!moved.defined || !moved.min.allFinite()) throw std::runtime_error("Invalid transformed project bounds");
    const auto size = moved.size();
    if ((size.array() <= 0).any() || (size.array() > 10000).any()) throw std::runtime_error("Transformed project dimensions are outside 0-10000 mm");
    for (auto *object : model.objects) { object->translate_instances(Slic3r::Vec3d(0, 0, -moved.min.z())); object->invalidate_bounding_box(); }
}
}
