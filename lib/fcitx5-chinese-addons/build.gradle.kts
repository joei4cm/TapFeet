plugins {
    id("org.fcitx.fcitx5.android.lib-convention")
    id("org.fcitx.fcitx5.android.native-lib-convention")
    id("org.fcitx.fcitx5.android.fcitx-headers")
}

/**
 * 上游 fcitx5-chinese-addons 是 submodule，本地 commit 推不进 fcitx 官方仓。
 * 拼音「连续拉丁当英文」补丁放在 patches/，每次构建前幂等打上。
 */
val applyPinyinEnglishLatinPatch by tasks.registering {
    val patch = layout.projectDirectory.file("patches/pinyin-english-latin.patch")
    val target = layout.projectDirectory.file(
        "src/main/cpp/fcitx5-chinese-addons/im/pinyin/pinyin.cpp"
    )
    inputs.file(patch)
    outputs.file(target)
    doLast {
        val cpp = target.asFile
        val marker = "Continuous latin buffer"
        if (cpp.readText().contains(marker)) return@doLast
        providers.exec {
            commandLine("patch", "-p1", "--forward", "--batch", "-i", patch.asFile.absolutePath)
            workingDir = layout.projectDirectory.dir("src/main/cpp/fcitx5-chinese-addons").asFile
        }.result.get().assertNormalExitValue()
    }
}

tasks.configureEach {
    if (name.startsWith("configureCMake") || name.startsWith("buildCMake") ||
        name.startsWith("externalNativeBuild")
    ) {
        dependsOn(applyPinyinEnglishLatinPatch)
    }
}

android {
    namespace = "org.fcitx.fcitx5.android.lib.fcitx5_chinese_addons"

    defaultConfig {
        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                targets(
                    // dummy "cmake" target
                    "cmake",
                    // fcitx5-chinese-addons
                    "pinyin",
                    "scel2org5",
                    "table",
                    "chttrans",
                    "fullwidth",
                    "pinyinhelper",
                    "punctuation",
                )
            }
        }
    }

    prefab {
        create("cmake") {
            headerOnly = true
            headers = "src/main/cpp/cmake"
        }
        create("pinyin") {
            libraryName = "libpinyin"
            // no headers
        }
        create("table") {
            libraryName = "libtable"
            // no headers
        }
        create("scel2org5") {
            libraryName = "libscel2org5"
            // no headers
        }
        val moduleHeadersPrefix = "build/headers/usr/include/Fcitx5/Module/fcitx-module"
        create("chttrans") {
            libraryName = "libchttrans"
            // no headers
        }
        create("fullwidth") {
            libraryName = "libfullwidth"
            // no headers
        }
        create("pinyinhelper") {
            libraryName = "libpinyinhelper"
            headers = "$moduleHeadersPrefix/pinyinhelper"
        }
        create("punctuation") {
            libraryName = "libpunctuation"
            headers = "$moduleHeadersPrefix/punctuation"
        }
    }
}

dependencies {
    implementation(project(":lib:fcitx5"))
    implementation(project(":lib:libime"))
    implementation(project(":lib:fcitx5-lua"))
}
