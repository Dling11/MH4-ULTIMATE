plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// the port/ directory this project lives in, and the Azahar tree the core is built from
val portDir = rootDir.parentFile.absolutePath
val azaharDir = file(providers.gradleProperty("azahar.dir").orElse("../../../.local/azahar").get()).absolutePath
val usePrebuiltEngine = providers.gradleProperty("engine.prebuilt").orElse("false").get().toBoolean()
val privateBuildDir = rootDir.resolve("../../.local")

android {
    namespace = "org.mh4u.port"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    if (!usePrebuiltEngine) ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "com.rowell.mh4u"
        minSdk = 29
        targetSdk = 35
        // CI passes its run number, so every build counts as newer than the last
        versionCode = System.getenv("MH4U_VERSION_CODE")?.toIntOrNull() ?: 17
        versionName = "1.0.16"
        ndk { abiFilters += listOf("arm64-v8a") }
        if (!usePrebuiltEngine) externalNativeBuild {
            cmake {
                arguments(
                    "-DMH4U_PORT_DIR=$portDir",
                    "-DENABLE_QT=0", "-DENABLE_SDL2=0", "-DENABLE_TESTS=0", "-DENABLE_ROOM=0",
                    "-DENABLE_SCRIPTING=0", "-DENABLE_GDBSTUB=OFF", "-DENABLE_OPENGL=OFF",
                    "-DENABLE_VULKAN=ON", "-DANDROID_ARM_NEON=true",
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON", "-DCITRA_WARNINGS_AS_ERRORS=OFF",
                    "-DCMAKE_JOB_POOLS=compile=2;link=1", "-DCMAKE_JOB_POOL_COMPILE=compile", "-DCMAKE_JOB_POOL_LINK=link",
                )
                targets += listOf("mh4u-android", "main_hook", "file_redirect_hook", "gsl_alloc_hook", "SPIRV-Tools-shared")
            }
        }
    }

    if (usePrebuiltEngine) sourceSets.getByName("main").jniLibs.srcDir(
        providers.gradleProperty("engine.prebuilt.dir").orNull?.let { file(it) } ?: privateBuildDir.resolve("engine"))
    if (!usePrebuiltEngine) externalNativeBuild {
        cmake {
            path = file("$azaharDir/CMakeLists.txt")
            version = "3.28.3"
        }
    }

    // One signing key for every build, so each APK can update the last one (docs/ROWELL_RELEASE.md). The key
    // comes from the environment in CI; local builds use the private persistent Rowell key.
    signingConfigs {
        create("rowell") {
            storeFile = privateBuildDir.resolve("signing/rowell.jks")
            storePassword = privateBuildDir.resolve("signing/password.txt").takeIf { it.exists() }?.readText()?.trim()
            keyAlias = "rowell"
            keyPassword = storePassword
        }
        val keystore = System.getenv("MH4U_KEYSTORE_FILE")
        if (!keystore.isNullOrEmpty() && file(keystore).exists()) {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("MH4U_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MH4U_KEY_ALIAS")
                keyPassword = System.getenv("MH4U_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("rowell")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging {
        jniLibs.useLegacyPackaging = true   // the code library is dlopen'ed from files
        if (usePrebuiltEngine) jniLibs.keepDebugSymbols += "**/*.so"
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
