cmake_minimum_required(VERSION 3.22)
project(kepler_upstream_probe LANGUAGES C CXX)

# This builds one real, dependency-light component from upstream. It is a
# toolchain/JNI probe, not a slicing engine.
if(NOT ANDROID)
    message(FATAL_ERROR "Android NDK toolchain is required. See engine/README.md.")
endif()
set(UPSTREAM_SOURCE "" CACHE PATH "Path to the official upstream checkout")
set(UPSTREAM_EXPECTED_COMMIT "6d2869c430284714f0a22570c38d3b359965a802")
if(NOT EXISTS "${UPSTREAM_SOURCE}/deps_src/semver/semver.c")
    message(FATAL_ERROR "UPSTREAM_SOURCE must point to the official upstream checkout containing deps_src/semver.")
endif()
find_package(Git REQUIRED)
execute_process(COMMAND "${GIT_EXECUTABLE}" -C "${UPSTREAM_SOURCE}" rev-parse HEAD
    RESULT_VARIABLE git_result OUTPUT_VARIABLE upstream_commit
    OUTPUT_STRIP_TRAILING_WHITESPACE ERROR_QUIET)
if(NOT git_result EQUAL 0 OR NOT upstream_commit STREQUAL UPSTREAM_EXPECTED_COMMIT)
    message(FATAL_ERROR "Upstream revision mismatch. Expected ${UPSTREAM_EXPECTED_COMMIT}; got '${upstream_commit}'.")
endif()

add_library(kepler_upstream_probe SHARED src/semver_jni.cpp
    "${UPSTREAM_SOURCE}/deps_src/semver/semver.c")
target_include_directories(kepler_upstream_probe PRIVATE "${UPSTREAM_SOURCE}/deps_src/semver")
target_compile_features(kepler_upstream_probe PRIVATE cxx_std_17)
message(STATUS "Building upstream semver JNI probe; libslic3r is NOT linked.")
