@file:Suppress("UnstableApiUsage")

import Config.Version.createVersion
import Config.Version.source
import Config.isRelease
import Config.lastCommitSha
import com.android.build.api.variant.impl.VariantOutputImpl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.com.android.application)
    alias(libs.plugins.org.jetbrains.kotlin.plugin.parcelize)
    alias(libs.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.com.google.devtools.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.navigation.safeargs)
    id("com.mikepenz.aboutlibraries.plugin") version "15.2.0"
    id("io.github.ben-manes.versions") version "0.65.0"
}

/**
 * 本地快速构建开关（只影响本地开发，正式发布/CI 不传即可保持原行为）：
 *
 * - 日常迭代：:app:assembleRelease -PfastBuild=true
 *   只产出 arm64-v8a 单个 APK，并跳过 lintVital，显著缩短打包时间。
 * - 正式发布：:app:assembleRelease（不带该参数）
 *   照旧产出 4 个 ABI 包 + 1 个 universal 包，并执行完整 lintVital 校验。
 */
val fastBuild = (findProperty("fastBuild") as? String)?.toBoolean() ?: false

android {
    compileSdk = property("compile.sdk")?.toString()?.toIntOrNull()

    val commitSha = if (isRelease) lastCommitSha else "b8eace8" // 方便调试

    // 先 Github Secrets 再读取环境变量，若没有则读取本地文件

    val githubToken = System.getenv("HA_GITHUB_TOKEN") ?: File(
        projectDir, "ha1_github_token.txt"
    ).checkIfExists()?.readText().orEmpty()


    defaultConfig {
        applicationId = "com.han1meviewer.remastered"
        minSdk = property("min.sdk")?.toString()?.toIntOrNull()
        targetSdk = property("target.sdk")?.toString()?.toIntOrNull()
        val (code, name) = createVersion(major = 2, minor = 1, patch = 156, versionCodePatch = 261)
        versionCode = code
        versionName = name

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "COMMIT_SHA", "\"$commitSha\"")
        buildConfigField("String", "VERSION_NAME", "\"${versionName}\"")
        buildConfigField("int", "VERSION_CODE", "$versionCode")
        buildConfigField("String", "HA_GITHUB_TOKEN", "\"${githubToken}\"")
        buildConfigField("String", "VERSION_SOURCE", "\"${source}\"")

        buildConfigField("int", "SEARCH_YEAR_RANGE_END", "${Config.thisYear}")
    }
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("HOME") + "/.android/keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEYSTORE_PASSWORD")
        }
    }

    // 应用内提供语言切换（会动态修改 locale），因此 AAB 不能按语言拆分，
    // 否则切换语言后对应语言的资源不存在。
    bundle {
        language {
            enableSplit = false
        }
    }

    splits {
        abi {
            isEnable = (gradle.startParameter.taskRequests.toString().contains("Release")) || fastBuild
            reset()
            if (fastBuild) {
                // 本地快速构建：只打 arm64-v8a，且不再额外产出 universal 包
                include("arm64-v8a")
            } else {
                include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            }
            isUniversalApk = !fastBuild
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            manifestPlaceholders["appIcon"] = "@mipmap/ic_launcher_new"

        }

        debug {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_launcher_debug"
        }
    }
    buildFeatures {
        //noinspection DataBindingWithoutKapt
        dataBinding = true
        buildConfig = true
        viewBinding = true
        compose  =  true
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    lint {
        // OverdrawDetector crashes/hangs with the current Kotlin FIR lint bridge.
        disable += setOf("EnsureInitializerMetadata", "Overdraw", "PropertyEscape")
    }
    namespace = "com.yenaly.han1meviewer"
}

kotlin {
    compilerOptions {
        jvmTarget.value(JvmTarget.JVM_21)
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-jvm-default=enable"
        )
    }
}


androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val impl = output as VariantOutputImpl
            val abiFilter = impl.filters.find { it.filterType.name == "ABI" }?.identifier
            val suffix = if (abiFilter != null) "-$abiFilter" else "-universal"
            //  val apkName = "你的应用名_V${output.versionName.get()}_Build${output.versionCode.get()}_${variant.buildType}.apk"
            val apkName = "Han1meViewer-v${output.versionName.get()}$suffix.apk"
            impl.outputFileName = apkName
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.androidx.window)
    implementation(libs.androidx.window.java)
    implementation(project(":yenaly_libs"))
    // 本地 jar/aar 依赖
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))
    implementation(libs.aboutlibraries.core)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.material.icons.extended)
    // android related

    implementation(libs.bundles.android.base)
    implementation(libs.bundles.android.jetpack)
    implementation(libs.palette)
    implementation(libs.material)
    //compose
    implementation(platform(libs.compose.compose.bom))
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui.ui.tooling.preview)
    implementation(libs.androidx.ui)
    androidTestImplementation(platform(libs.compose.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.compose.ui.ui.tooling)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.video)
    implementation(libs.aboutlibraries.compose.m3)
    implementation(libs.compose.avatar.cropper)
    // datetime

    implementation(libs.datetime)

    // parse

    implementation(libs.serialization.json)
    implementation(libs.jsoup)

    // network

    implementation(libs.retrofit)
    implementation(libs.converter.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.dns.over.https)

    // pic

    implementation(libs.coil)


    // video

    implementation(libs.jiaozi.video.player)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.mpv.lib)

    // view

    implementation(libs.multitype)
    implementation(libs.base.recyclerview.adapter.helper4)
    implementation(libs.expandable.textview)
    implementation(libs.spannable.x)
    implementation(libs.about)
    implementation(libs.circular.reveal.switch)
    implementation(libs.drawerlayout)

    debugImplementation(libs.androidx.ui.test.manifest)
    ksp(libs.room.compiler)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)

    androidTestImplementation(libs.test.junit)
    androidTestImplementation(libs.test.espresso.core)

    // debugImplementation(libs.leak.canary)
}

if (fastBuild) {
    // 本地快速构建不跑 lintVital，避免每次迭代都做完整静态检查
    tasks.matching { it.name.startsWith("lintVital") }.configureEach { enabled = false }
}

/**
 * This function is used to check if a file exists and is a file.
 */
fun File.checkIfExists(): File? = if (exists() && isFile) this else null
