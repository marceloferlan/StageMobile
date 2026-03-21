cmake_minimum_required(VERSION 3.22)
project(stage_engine)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Include directories
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/include)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/externals/DSPFilters/include)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/externals/STK/include)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/externals/SoundTouch/include)

# Import pre-built FluidSynth shared library
add_library(fluidsynth SHARED IMPORTED)
set_target_properties(fluidsynth PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libfluidsynth.so
)

# Source files groups
set(DSP_SOURCES
    externals/DSPFilters/source/Bessel.cpp
    externals/DSPFilters/source/Biquad.cpp
    externals/DSPFilters/source/Butterworth.cpp
    externals/DSPFilters/source/Cascade.cpp
    externals/DSPFilters/source/ChebyshevI.cpp
    externals/DSPFilters/source/ChebyshevII.cpp
    externals/DSPFilters/source/Custom.cpp
    externals/DSPFilters/source/Design.cpp
    externals/DSPFilters/source/Elliptic.cpp
    externals/DSPFilters/source/Filter.cpp
    externals/DSPFilters/source/Legendre.cpp
    externals/DSPFilters/source/Param.cpp
    externals/DSPFilters/source/PoleFilter.cpp
    externals/DSPFilters/source/RBJ.cpp
    externals/DSPFilters/source/RootFinder.cpp
    externals/DSPFilters/source/State.cpp
)

set(STK_SOURCES
    externals/STK/Stk.cpp
    externals/STK/FreeVerb.cpp
    externals/STK/NRev.cpp
    externals/STK/Chorus.cpp
    externals/STK/Delay.cpp
    externals/STK/DelayL.cpp
    externals/STK/DelayA.cpp
    externals/STK/OnePole.cpp
    externals/STK/OneZero.cpp
    externals/STK/SineWave.cpp
    externals/STK/BiQuad.cpp
    externals/STK/TwoPole.cpp
    externals/STK/TwoZero.cpp
    externals/STK/PoleZero.cpp
)

set(SOUNDTOUCH_SOURCES
    externals/SoundTouch/source/AAFilter.cpp
    externals/SoundTouch/source/FIFOSampleBuffer.cpp
    externals/SoundTouch/source/FIRFilter.cpp
    externals/SoundTouch/source/InterpolateCubic.cpp
    externals/SoundTouch/source/InterpolateLinear.cpp
    externals/SoundTouch/source/InterpolateShannon.cpp
    externals/SoundTouch/source/RateTransposer.cpp
    externals/SoundTouch/source/SoundTouch.cpp
    externals/SoundTouch/source/TDStretch.cpp
    externals/SoundTouch/source/cpu_detect_android.cpp
)

# Build our JNI bridge library
add_library(synthmodule SHARED 
    fluidsynth_bridge.cpp
    ${DSP_SOURCES}
    ${STK_SOURCES}
    ${SOUNDTOUCH_SOURCES}
)

find_package(oboe REQUIRED CONFIG)

target_link_libraries(
    synthmodule
    fluidsynth
    oboe::oboe
    log
    android
)