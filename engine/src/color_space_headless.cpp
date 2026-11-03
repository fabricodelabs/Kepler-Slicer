// Exact RGB2HSV implementation from OrcaSlicer
// src/slic3r/Utils/ColorSpaceConvert.cpp at
// 6d2869c430284714f0a22570c38d3b359965a802 (AGPL-3.0).
// FlushVolCalc in libslic3r depends on this global function. The desktop source
// also contains wxColour UI helpers and includes wx/colordlg.h, so the Android
// engine links this isolated unchanged function instead of a wxWidgets object.
#include <algorithm>
#include <cmath>
#include "slic3r/Utils/ColorSpaceConvert.hpp"

// The input r, g, b values should be in range [0, 1]. The output h is in range [0, 360], s is in range [0, 1] and v is in range [0, 1].
void RGB2HSV(float r, float g, float b, float* h, float* s, float* v)
{
    float Cmax = std::max(std::max(r, g), b);
    float Cmin = std::min(std::min(r, g), b);
    float delta = Cmax - Cmin;

    if (std::abs(delta) < 0.001) {
        *h = 0.f;
    }
    else if (Cmax == r) {
        *h = 60.f * fmod((g - b) / delta, 6.f);
    }
    else if (Cmax == g) {
        *h = 60.f * ((b - r) / delta + 2);
    }
    else {
        *h = 60.f * ((r - g) / delta + 4);
    }

    if (std::abs(Cmax) < 0.001) {
        *s = 0.f;
    }
    else {
        *s = delta / Cmax;
    }

    *v = Cmax;
}
