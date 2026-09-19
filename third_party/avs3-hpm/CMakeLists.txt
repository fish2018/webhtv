cmake_minimum_required(VERSION 3.16)
project(webhtv_hpm C)
if(NOT HPM_SOURCE)
  message(FATAL_ERROR "HPM_SOURCE must be the verified, prepared HPM 15.0 tree")
endif()
file(GLOB HPM_COMMON CONFIGURE_DEPENDS "${HPM_SOURCE}/src/com*.c")
file(GLOB HPM_DECODER CONFIGURE_DEPENDS "${HPM_SOURCE}/src/dec*.c")
add_library(webhtvhpm STATIC webhtv_hpm.c ${HPM_COMMON} ${HPM_DECODER})
set_target_properties(webhtvhpm PROPERTIES POSITION_INDEPENDENT_CODE ON C_STANDARD 11)
target_include_directories(webhtvhpm PRIVATE "${HPM_SOURCE}/inc" "${CMAKE_CURRENT_SOURCE_DIR}/vendor"
  PUBLIC "${CMAKE_CURRENT_SOURCE_DIR}")
target_compile_definitions(webhtvhpm PRIVATE LINUX=1)
target_compile_options(webhtvhpm PRIVATE -O3 -fvisibility=hidden -fno-strict-aliasing
  -Wno-shift-negative-value -Wno-unused-value -Wno-incompatible-pointer-types)
# Preserve assertions which consume syntax bits. Do not compile HPM with NDEBUG.
target_compile_options(webhtvhpm PRIVATE -UNDEBUG)
target_compile_options(webhtvhpm PRIVATE -include "${CMAKE_CURRENT_SOURCE_DIR}/hpm_namespace.h")
if(CMAKE_SYSTEM_PROCESSOR MATCHES "x86_64|AMD64")
  target_compile_options(webhtvhpm PRIVATE -msse4.2)
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
  target_compile_options(webhtvhpm PRIVATE -mfpu=neon)
endif()
target_compile_definitions(webhtvhpm PRIVATE SSE2NEON_PRECISE_MINMAX=1 SSE2NEON_PRECISE_DIV=1
  SSE2NEON_PRECISE_SQRT=1 SSE2NEON_PRECISE_DP=1)
if(CMAKE_C_COMPILER_ID MATCHES "Clang")
  # Catch runtime indices into fixed-size syntax arrays, returning an ordinary
  # decoder error through our private handler, without a UBSan runtime/SONAME.
  set_source_files_properties(${HPM_COMMON} ${HPM_DECODER} PROPERTIES
    COMPILE_OPTIONS "-fsanitize=array-bounds;-fno-sanitize-link-runtime")
endif()
find_package(Threads REQUIRED)
target_link_libraries(webhtvhpm PUBLIC Threads::Threads m)
add_executable(hpm_contract hpm_contract.c)
target_link_libraries(hpm_contract PRIVATE webhtvhpm)
install(TARGETS webhtvhpm ARCHIVE DESTINATION lib)
install(FILES webhtv_hpm.h webhtv_hpm_ffmpeg.h DESTINATION include)
