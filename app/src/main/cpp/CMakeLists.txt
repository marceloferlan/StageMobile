cmake_minimum_required(VERSION 3.22)
project(stage_engine)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Include directories
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/include)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/externals/DSPFilters/include)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/externals/STK/include)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/externals/SoundTouch/include)

# ─── libusb (LGPL-2.1) — compilada como parte do synthmodule ─────────────────
# Sources e config.h oficiais do diretório android/ do repositório libusb.
# Ref: externals/libusb/android/jni/libusb.mk
set(LIBUSB_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/externals/libusb)

include_directories(
    ${LIBUSB_ROOT}/android          # config.h oficial para Android
    ${LIBUSB_ROOT}/libusb           # libusb.h, libusbi.h
    ${LIBUSB_ROOT}/libusb/os        # linux_usbfs.h, threads_posix.h, events_posix.h
)

set(LIBUSB_SOURCES
    ${LIBUSB_ROOT}/libusb/core.c
    ${LIBUSB_ROOT}/libusb/descriptor.c
    ${LIBUSB_ROOT}/libusb/hotplug.c
    ${LIBUSB_ROOT}/libusb/io.c
    ${LIBUSB_ROOT}/libusb/sync.c
    ${LIBUSB_ROOT}/libusb/strerror.c
    ${LIBUSB_ROOT}/libusb/os/linux_usbfs.c
    ${LIBUSB_ROOT}/libusb/os/events_posix.c
    ${LIBUSB_ROOT}/libusb/os/threads_posix.c
    ${LIBUSB_ROOT}/libusb/os/linux_netlink.c
)

# Flags idênticas ao Android.mk oficial da libusb
set_source_files_properties(${LIBUSB_SOURCES} PROPERTIES
    COMPILE_FLAGS "-fvisibility=hidden -Wno-sign-compare -Wno-unused-parameter"
)
# ─────────────────────────────────────────────────────────────────────────────

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
    usb_audio_driver.cpp
    ${DSP_SOURCES}
    ${STK_SOURCES}
    ${SOUNDTOUCH_SOURCES}
    ${LIBUSB_SOURCES}
)

find_package(oboe REQUIRED CONFIG)

target_link_libraries(
    synthmodule
    fluidsynth
    oboe::oboe
    log
    android
)