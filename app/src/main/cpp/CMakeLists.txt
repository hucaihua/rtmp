cmake_minimum_required(VERSION 3.18.1)
project("bilybily")

file(GLOB hch *.cpp)
add_subdirectory(librtmp)

add_library(
        bilybily
        SHARED
        ${hch})

find_library( # Sets the name of the path variable.
        log-lib
        log)

target_link_libraries( # Specifies the target library.
        bilybily
        ${log-lib}
        rtmp)
