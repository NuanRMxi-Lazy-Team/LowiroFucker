cmake_minimum_required(VERSION 3.22)
project(lowirofucker C)

add_library(native-hook SHARED native-hook.c)

find_library(log-lib log)
target_link_libraries(native-hook ${log-lib} dl)
