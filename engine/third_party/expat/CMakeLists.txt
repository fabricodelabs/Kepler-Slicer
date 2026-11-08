# Expat 2.10.0 (MIT, https://github.com/libexpat/libexpat, release R_2_10_0, signature verified) replacing the
# expat 2.2.0 copy in OrcaSlicer deps_src: 3MF files are XML from untrusted sources. Copied over deps_src/expat by
# engine/build-native.ps1.
cmake_minimum_required(VERSION 3.13)
project(expat C)
add_library(expat STATIC xcs.c xmlparse.c xmlrole.c xmltok.c random_arc4random_buf.c random_dev_urandom.c)
target_include_directories(expat SYSTEM PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})
target_compile_definitions(expat PRIVATE HAVE_EXPAT_CONFIG_H XML_STATIC)
if(CMAKE_C_COMPILER_ID MATCHES "Clang|GNU")
    target_compile_options(expat PRIVATE -Wno-unused-parameter)
endif()
