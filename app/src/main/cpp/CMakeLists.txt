cmake_minimum_required(VERSION 3.22.1)
project("xaudio")

# Mendeklarasikan file C++ kita
add_library(xaudio_engine SHARED audio_pusher.cpp)

# Memasukkan library log bawaan Android biar kita bisa nge-print tulisan
find_library(log-lib log)

# Menyatukan semuanya
target_link_libraries(xaudio_engine ${log-lib})
