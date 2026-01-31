# CMake configuration for Firmware Security JNI Library
# See docs/deviceowner-firmware-integration-complete and docs/android-firmware-security-complete

cmake_minimum_required(VERSION 3.22.1)

project("firmware_security")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra -O2 -fPIC")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fstack-protector-strong")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -D_FORTIFY_SOURCE=2")

add_library(
    firmware_security
    SHARED
    firmware_security_jni.cpp
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(
    firmware_security
    ${log-lib}
    ${android-lib}
)

target_include_directories(
    firmware_security
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

if(CMAKE_BUILD_TYPE STREQUAL "Release")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -s")
endif()

message(STATUS "Firmware Security JNI: libfirmware_security.so for com.example.deviceowner")
