cmake_minimum_required(VERSION 3.4.1)
set(TARGET wenet)
project(${TARGET} CXX)
set(CMAKE_CXX_STANDARD 14)

option(TORCH "whether to build with Torch" ON)
option(ONNX "whether to build with ONNX" OFF)
option(ITN "whether to use WeTextProcessing" OFF)
set(CMAKE_VERBOSE_MAKEFILE on)
# build_DIR should point to the directory where AARs are extracted by Gradle
if(NOT build_DIR)
  set(build_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../build")
endif()
list(APPEND CMAKE_MODULE_PATH ${CMAKE_CURRENT_SOURCE_DIR}/cmake)
string(REPLACE "-Wl,--exclude-libs,libgcc_real.a" "" CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS}")        

include(libtorch)
set(PYTORCH_LIBRARY ${build_DIR}/pytorch_android-1.13.0/jni/${ANDROID_ABI}/libpytorch_jni.so)
set(FBJNI_LIBRARY ${build_DIR}/pytorch_android-1.13.0/jni/${ANDROID_ABI}/libfbjni.so)
include(openfst)

include_directories(
  ${CMAKE_SOURCE_DIR}
  ${CMAKE_SOURCE_DIR}/kaldi
)

add_subdirectory(utils)
add_subdirectory(frontend)
add_subdirectory(post_processor)
add_subdirectory(kaldi)  # kaldi: wfst based decoder
add_subdirectory(decoder)

link_libraries(frontend decoder android)
add_library(${TARGET} SHARED wenet.cc)

add_executable(decoder_main bin/decoder_main.cc)
target_link_libraries(decoder_main PUBLIC libc++_shared.so)
