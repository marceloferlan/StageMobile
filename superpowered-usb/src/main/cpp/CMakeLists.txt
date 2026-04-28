cmake_minimum_required(VERSION 3.22)
project(sp_bridge_project)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Superpowered SDK headers
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/superpowered)

# Import Superpowered static library
add_library(Superpowered STATIC IMPORTED)
set_target_properties(Superpowered PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/superpowered/libSuperpoweredAndroid${ANDROID_ABI}.a
)

# Build our Superpowered bridge shared library (isolado com c++_static)
add_library(spbridge SHARED
    sp_bridge.cpp
)

target_link_libraries(
    spbridge
    Superpowered
    OpenSLES
    log
    android
)
