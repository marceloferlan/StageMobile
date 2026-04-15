plugins {
    id("com.android.library")
}

android {
    namespace = "com.marceloferlan.stagemobile.superpowered"
    compileSdk = 34

    defaultConfig {
        minSdk = 26

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_static"
                arguments += "-DANDROID_ARM_NEON=TRUE"
                cppFlags += "-std=c++17"
                cppFlags += "-fsigned-char"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
}
