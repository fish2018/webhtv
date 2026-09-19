cmake_minimum_required(VERSION 3.22.1)
project(exo_ass LANGUAGES CXX)

if(NOT ANDROID OR NOT ANDROID_ABI STREQUAL "arm64-v8a")
    message(FATAL_ERROR "Stage 1 is an independently built arm64 Android prototype")
endif()
if(NOT DEFINED ASS_PREFIX)
    message(FATAL_ERROR "Run scripts/build_exo_ass_native.py with the pinned dependency lock")
endif()

add_library(exo_ass SHARED exo_ass.cpp)
target_compile_features(exo_ass PRIVATE cxx_std_17)
target_compile_options(exo_ass PRIVATE -Wall -Wextra -Werror=return-type
        -fvisibility=hidden -fvisibility-inlines-hidden)
target_include_directories(exo_ass PRIVATE "${ASS_PREFIX}/include")
target_link_options(exo_ass PRIVATE -Wl,--no-undefined -Wl,--exclude-libs,ALL
        -Wl,-z,max-page-size=16384 -Wl,--gc-sections)
target_link_libraries(exo_ass PRIVATE
        -Wl,--start-group
        "${ASS_PREFIX}/lib/libass.a"
        "${ASS_PREFIX}/lib/libharfbuzz.a"
        "${ASS_PREFIX}/lib/libfribidi.a"
        "${ASS_PREFIX}/lib/libfontconfig.a"
        "${ASS_PREFIX}/lib/libfreetype.a"
        "${ASS_PREFIX}/lib/libxml2.a"
        -Wl,--end-group
        android log EGL GLESv2 m)
