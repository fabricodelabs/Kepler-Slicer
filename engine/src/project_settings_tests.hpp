#pragma once
#include <string>
namespace kepler {
// In-process contract checks using real Orca 3MF writer/loader/config objects.
// Caller initializes private Orca paths and holds the process-wide engine lock.
std::string project_settings_self_test(const std::string &private_data_root);
}
