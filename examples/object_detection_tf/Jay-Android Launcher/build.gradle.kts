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

import com.google.protobuf.gradle.*

plugins {
    id("com.google.protobuf") apply true
    id("com.android.application") apply true
    id("kotlin-android") apply true
    id("kotlin-kapt") apply true
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.15.1"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.35.0"
        }
        id("java") {
            artifact = "com.google.protobuf:protoc:3.15.1"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                id("java")
            }
            task.plugins {
                id("grpc")
            }
        }
    }
}

android {
    compileSdkVersion(30)

    packagingOptions {
        exclude("META-INF/io.netty.versions.properties")
        exclude("META-INF/INDEX.LIST")
        exclude("META-INF/DEPENDENCIES")
        exclude("META-INF/LICENSE")
        exclude("META-INF/LICENSE.txt")
        exclude("META-INF/license.txt")
        exclude("META-INF/NOTICE")
        exclude("META-INF/NOTICE.txt")
        exclude("META-INF/notice.txt")
        exclude("META-INF/ASL2.0")
    }

    aaptOptions {
        noCompress("tflite", "lite")
    }

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
        applicationId("pt.up.fc.dcc.hyrax.jay_droid_launcher")
        minSdkVersion(24)
        targetSdkVersion(30)
        setVersionCode(1)
        setVersionName("1.0")
        multiDexEnabled = true
    }
    buildTypes {
        named("release") {
            minifyEnabled(false)
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

        }
    }
    dexOptions {
        javaMaxHeapSize = "4g"
        preDexLibraries = false
    }
    buildToolsVersion("30.0.2")
    ndkVersion = "21.3.6528147"
}

dependencies {
    "implementation"("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.4.30")
    "implementation"("io.grpc:grpc-netty:1.35.0")
    "implementation"("io.grpc:grpc-protobuf:1.35.0")
    "implementation"("io.grpc:grpc-stub:1.35.0")
    "implementation"("javax.annotation:javax.annotation-api:1.3.2")
    "api"(project(":Jay-Android"))
    "api"(project(":Jay-Base"))
    "implementation"("androidx.core:core-ktx:1.3.2")
    "implementation"("androidx.appcompat:appcompat:1.2.0")
    "implementation"("com.squareup.leakcanary:plumber-android:2.6")
    "implementation"("org.kamranzafar:jtar:2.3")
    "implementation"("org.tensorflow:tensorflow-android:1.13.1")
    "implementation"("org.tensorflow:tensorflow-lite:0.0.0-nightly")
    "implementation"("org.tensorflow:tensorflow-lite-gpu:0.0.0-nightly")
    "implementation"("org.tensorflow:tensorflow-lite-support:0.0.0-nightly")
    "implementation"("com.arthenica:mobile-ffmpeg-min-gpl:4.4.LTS")
}

repositories {
    mavenCentral()
}