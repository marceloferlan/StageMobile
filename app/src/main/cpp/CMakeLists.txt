cmake_minimum_required(VERSION 3.22)
project(stage_engine)

# FluidSynth headers
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/include)

# Import pre-built FluidSynth shared library
add_library(fluidsynth SHARED IMPORTED)
set_target_properties(fluidsynth PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libfluidsynth.so
)

# Build our JNI bridge library
add_library(synthmodule SHARED fluidsynth_bridge.cpp)

target_link_libraries(
    synthmodule
    fluidsynth
    log
    android
)