cmake_minimum_required(VERSION 3.10)

add_library(native-audio SHARED native-audio.cpp)

find_library(log-lib log)

target_link_libraries(native-audio ${log-lib})
