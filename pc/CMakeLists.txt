project(Dalj)
cmake_minimum_required(VERSION 3.16)

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(OpenCV REQUIRED)
if(OpenCV_FOUND)
    message("${OpenCV_LIBS}")
endif(OpenCV_FOUND)

find_path(AVCODEC_INCLUDE_DIR libavcodec/avcodec.h)
find_library(AVCODEC_LIBRARY avcodec)
message("${AVCODEC_LIBRARY}")

find_path(AVFORMAT_INCLUDE_DIR libavformat/avformat.h)
find_library(AVFORMAT_LIBRARY avformat)
message("${AVFORMAT_LIBRARY}")

find_path(AVUTIL_INCLUDE_DIR libavutil/avutil.h)
find_library(AVUTIL_LIBRARY avutil)
message("${AVUTIL_LIBRARY}")

find_path(SWSCALE_INCLUDE_DIR libswscale/swscale.h)
find_library(SWSCALE_LIBRARY swscale)
message("${SWSCALE_LIBRARY}")

add_subdirectory(src/)