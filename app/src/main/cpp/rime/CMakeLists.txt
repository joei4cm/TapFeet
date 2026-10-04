# Bundled fcitx5-rime (中州韵). Built as an in-tree MODULE like androidkeyboard,
# statically linking prebuilt librime so the main APK has 朙月拼音·简化字 without
# a second plugin package.
#
# Only add_subdirectory(src) + po: the full fcitx5-rime project() wants ECM,
# PkgConfig, and GNUInstallDirs that the Android app cmake does not provide.

get_filename_component(TAPFEET_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../.." ABSOLUTE)
set(RIME_CPP_DIR "${TAPFEET_ROOT}/plugin/rime/src/main/cpp")

set(PROJECT_VERSION "5.1.14")
set(REQUIRED_FCITX_VERSION "5.1.13")
set(Rime_VERSION "1.12.0")
set(Rime_dependencies
        log
        "${PREBUILT_DIR}/glog/${ANDROID_ABI}/lib/libglog.a"
        "${PREBUILT_DIR}/leveldb/${ANDROID_ABI}/lib/libleveldb.a"
        "${PREBUILT_DIR}/lua/${ANDROID_ABI}/lib/liblua_static.a"
        "${PREBUILT_DIR}/marisa/${ANDROID_ABI}/lib/libmarisa.a"
        "${PREBUILT_DIR}/opencc/${ANDROID_ABI}/lib/libopencc.a"
        "${PREBUILT_DIR}/yaml-cpp/${ANDROID_ABI}/lib/libyaml-cpp.a"
        )

add_library(Rime_static STATIC IMPORTED)
set_target_properties(Rime_static PROPERTIES
        IMPORTED_LOCATION "${PREBUILT_DIR}/librime/${ANDROID_ABI}/lib/librime.a"
        INTERFACE_INCLUDE_DIRECTORIES "${PREBUILT_DIR}/librime/${ANDROID_ABI}/include"
        INTERFACE_LINK_LIBRARIES "${Rime_dependencies}"
        )

set(RIME_TARGET $<LINK_LIBRARY:WHOLE_ARCHIVE,Rime_static>)
set(RIME_DATA_DIR "${FCITX_INSTALL_DATADIR}/rime-data")

list(APPEND CMAKE_MODULE_PATH "${RIME_CPP_DIR}/fcitx5-rime/cmake")
find_package(Pthread REQUIRED)
find_package(Gettext REQUIRED)
if (NOT TARGET Fcitx5::Module::Notifications)
    find_package(Fcitx5Module REQUIRED COMPONENTS Notifications)
endif()

add_definitions(-DRIME_DATA_DIR="${RIME_DATA_DIR}")
add_definitions(-DFCITX_GETTEXT_DOMAIN=\"fcitx5-rime\")
add_definitions(-DFCITX_RIME_VERSION=\"${PROJECT_VERSION}\")
add_definitions(-DFCITX_RIME_NO_DBUS)
fcitx5_add_i18n_definition()

add_subdirectory("${RIME_CPP_DIR}/fcitx5-rime/src" fcitx5-rime-src)
add_subdirectory("${RIME_CPP_DIR}/fcitx5-rime/po" fcitx5-rime-po)

install(FILES
        "${CMAKE_CURRENT_SOURCE_DIR}/default.yaml"
        "${RIME_CPP_DIR}/rime-essay/essay.txt"
        "${RIME_CPP_DIR}/rime-prelude/key_bindings.yaml"
        "${RIME_CPP_DIR}/rime-prelude/punctuation.yaml"
        "${RIME_CPP_DIR}/rime-prelude/symbols.yaml"
        "${RIME_CPP_DIR}/rime-luna-pinyin/luna_pinyin.dict.yaml"
        "${RIME_CPP_DIR}/rime-luna-pinyin/luna_pinyin.schema.yaml"
        "${RIME_CPP_DIR}/rime-luna-pinyin/luna_pinyin_simp.schema.yaml"
        "${RIME_CPP_DIR}/rime-luna-pinyin/pinyin.yaml"
        "${RIME_CPP_DIR}/rime-stroke/stroke.dict.yaml"
        "${RIME_CPP_DIR}/rime-stroke/stroke.schema.yaml"
        "${RIME_CPP_DIR}/fcitx5-rime/data/fcitx5.yaml"
        DESTINATION "${FCITX_INSTALL_DATADIR}/rime-data"
        COMPONENT prebuilt-assets
        )
