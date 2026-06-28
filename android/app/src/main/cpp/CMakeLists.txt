cmake_minimum_required(VERSION 3.22)
project(xboxemu_android LANGUAGES C CXX)

add_library(xboxemu_android SHARED
    android_backend.cpp
)

add_library(xemu_core SHARED IMPORTED)
set_target_properties(xemu_core PROPERTIES
    IMPORTED_LOCATION
    "${CMAKE_CURRENT_LIST_DIR}/../jniLibs/${ANDROID_ABI}/libxemu-core-i386.so"
)

target_compile_features(xboxemu_android PRIVATE cxx_std_17)
target_compile_definitions(xboxemu_android PRIVATE VK_USE_PLATFORM_ANDROID_KHR)
target_link_libraries(xboxemu_android
    xemu_core
    android
    log
    EGL
    GLESv3
    vulkan
)
