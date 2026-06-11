cmake_minimum_required(VERSION 3.20)
project(dither_tests CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)

FetchContent_Declare(
	Catch2
	GIT_REPOSITORY https://github.com/catchorg/Catch2.git
	GIT_TAG        v3.8.1
)

FetchContent_MakeAvailable(Catch2)

add_executable(dither_tests
	dither_color_test.cpp
	dither_kernels_test.cpp
	dither_algorithms_test.cpp
	dither_matrices_test.cpp
)

target_include_directories(dither_tests PRIVATE ../../main/cpp)

target_link_libraries(dither_tests PRIVATE Catch2::Catch2WithMain)

include(CTest)
include(Catch)
catch_discover_tests(dither_tests)
