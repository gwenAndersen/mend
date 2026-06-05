# Set the minimum version of CMake required
cmake_minimum_required(VERSION 3.18.1)

# Project name
project("mend_native")

# Add the C++ source files to a library.
# This will be a shared library (.so file) that the Kotlin code can load.
add_library(
        mend_native
        SHARED
        native-lib.cpp
        TerrariaWorldParser.cpp)

# Find and link the necessary Android libraries.
# Log library for logging from C++ to logcat
find_library(
        log-lib
        log)

# Android library for native asset manager access
find_library(
        android-lib
        android)

# OpenGL ES 3.0 and EGL libraries
find_library(
        glesv3-lib
        GLESv3)
find_library(
        egl-lib
        EGL)

# Link all the found libraries to our native library.
target_link_libraries(
        mend_native
        ${log-lib}
        ${android-lib}
        ${glesv3-lib}
        ${egl-lib})
