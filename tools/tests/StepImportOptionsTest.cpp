#include "step_import.hpp"
#include <iostream>
#include <limits>
#include <stdexcept>

int main() {
    using kepler::StepImportOptions;
    int checks = 0;
    auto valid = [&](double linear, double angular) {
        StepImportOptions{linear, angular, false}.validate();
        ++checks;
    };
    auto invalid = [&](double linear, double angular) {
        bool rejected = false;
        try { StepImportOptions{linear, angular, true}.validate(); }
        catch (const std::invalid_argument &) { rejected = true; }
        if (!rejected) throw std::runtime_error("Invalid meshing parameters reached OCCT");
        ++checks;
    };
    StepImportOptions{}.validate(); ++checks;
    valid(0.003, 0.5);
    valid(0.5, 1.0);
    valid(0.00001, 3.14159265358979323846);
    valid(1000.0, 0.01); // No arbitrary maximum CAD size/tolerance cap.
    for (double value : {0.0, -1.0, std::numeric_limits<double>::infinity(),
                         -std::numeric_limits<double>::infinity(), std::numeric_limits<double>::quiet_NaN()}) {
        invalid(value, 0.5);
        invalid(0.003, value);
    }
    invalid(0.003, 3.14159265358979323846 + 0.00001);
    std::cout << "STEP import parameter checks: " << checks << " passed\n";
}
