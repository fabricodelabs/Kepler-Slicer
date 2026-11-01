#include <CGAL/Exact_predicates_exact_constructions_kernel.h>
#include <mpfr.h>

// Compile/link probe only. This is not a slicing engine or runtime test.
extern "C" int kepler_math_probe()
{
    using Kernel = CGAL::Exact_predicates_exact_constructions_kernel;
    Kernel::Point_2 a(0, 0), b(1, 0), c(0, 1);
    mpfr_t n;
    mpfr_init2(n, 128);
    mpfr_set_ui(n, 3, MPFR_RNDN);
    mpfr_clear(n);
    return CGAL::orientation(a, b, c) == CGAL::COUNTERCLOCKWISE;
}
