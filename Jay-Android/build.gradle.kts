/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 * 
 * Author: Joaquim Silva
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */

plugins {
    id("com.android.library") apply true
    id("kotlin-android") apply true
}


android {
    aaptOptions {
        noCompress("tflite", "lite")
    }

    compileSdkVersion(30)
    
    sourceSets {
        named("main") {
            java {
                srcDirs("src/main/kotlin")
                res.srcDirs("src/main/res")
            }
        }
    }

    compileOptions {
        sourceCompatibility(JavaVersion.VERSION_11)
        targetCompatibility(JavaVersion.VERSION_11)
    }

    defaultConfig {
        setMinSdkVersion(24)
        setTargetSdkVersion(30)
        setVersionCode(2)
        setVersionName("2.0")
        multiDexEnabled = true
    }

    buildTypes {
        named("release") {
            minifyEnabled(false)
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            multiDexEnabled = false
        }
    }

    dexOptions {
        javaMaxHeapSize = "4g"
        preDexLibraries = false
    }
    buildToolsVersion = "30.0.2"
}

dependencies {
    "implementation"("org.kamranzafar:jtar:2.3")
    "implementation"("org.tensorflow:tensorflow-android:1.13.1")
    "implementation"("org.tensorflow:tensorflow-lite:0.0.0-nightly")
    "implementation"("org.tensorflow:tensorflow-lite-gpu:0.0.0-nightly")
    "implementation"("org.tensorflow:tensorflow-lite-support:0.0.0-nightly")
    "implementation"("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.4.30")
    "implementation"("com.google.api.grpc:proto-google-common-protos:2.0.1")
    "implementation"("com.arthenica:mobile-ffmpeg-min-gpl:4.4.LTS")
    "implementation"("com.jaredrummler:android-device-names:2.0.0")
    "api"("com.google.guava:guava:30.1-jre")
    "implementation"(project(":Jay-Base"))
    "implementation"("androidx.core:core-ktx:1.3.2")
    "implementation"("eu.chainfire:libsuperuser:1.1.0.202004101746")
    "implementation"("com.squareup.leakcanary:plumber-android:2.6")
}
