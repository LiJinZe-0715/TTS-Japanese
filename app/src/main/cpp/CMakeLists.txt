cmake_minimum_required(VERSION 3.22.1)
project(voicevoxjni C)

# libvoicevox_core.so lives in jniLibs so Gradle packages it into the APK and
# this module can link against the very same file.
set(CORE_LIB "${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libvoicevox_core.so")

add_library(voicevoxjni SHARED voicevox_jni.c)
target_include_directories(voicevoxjni PRIVATE "${CMAKE_SOURCE_DIR}/include")

find_library(log-lib log)
target_link_libraries(voicevoxjni ${CORE_LIB} ${log-lib})
