// The desktop build defines this in GUI/BitmapCache.cpp. The experimental
// headless target uses the same upstream header without linking the desktop GUI.
#define NANOSVG_IMPLEMENTATION
#include "nanosvg.h"
