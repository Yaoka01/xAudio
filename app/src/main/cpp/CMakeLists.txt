cmake_minimum_required(VERSION 3.22.1)
project("xaudio")

# Deklarasi library native
add_library(xaudio_engine SHARED audio_pusher.cpp)

# Cari library log Android
find_library(log-lib log)

# Link library log ke engine kita
target_link_libraries(xaudio_engine ${log-lib} android)
