# CMake configuration for Firmware Security JNI Library
# ======================================================
# 
# PURPOSE: Build native library for Device Owner integration
# LOCATION: app/src/main/cpp/CMakeLists.txt
# 
# USAGE:
# 1. Copy this to your Device Owner app's cpp directory
# 2. Update app/build.gradle.kts to enable CMake
# 3. Build: ./gradlew assembleDebug
# 
# OUTPUT: libfirmware_security.so

cmake_minimum_required(VERSION 3.22.1)

project("firmware_security")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Compiler flags for optimization and security
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra -O2 -fPIC")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fstack-protector-strong")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -D_FORTIFY_SOURCE=2")

# Add source files
add_library(
    firmware_security
    SHARED
    firmware_security_jni.cpp
)

# Find required libraries
find_library(log-lib log)
find_library(android-lib android)

# Link libraries
target_link_libraries(
    firmware_security
    ${log-lib}
    ${android-lib}
)

# Include directories
target_include_directories(
    firmware_security
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# Strip symbols in release builds for smaller size
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -s")
endif()

# Print configuration
message(STATUS "=================================")
message(STATUS "Firmware Security Native Library")
message(STATUS "=================================")
message(STATUS "Build Type: ${CMAKE_BUILD_TYPE}")
message(STATUS "C++ Standard: ${CMAKE_CXX_STANDARD}")
message(STATUS "Compiler Flags: ${CMAKE_CXX_FLAGS}")
message(STATUS "Output: libfirmware_security.so")
message(STATUS "=================================")
