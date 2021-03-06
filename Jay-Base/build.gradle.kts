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
/**
 *
 *
 * > Transform artifact Jay-Base.jar (project :Jay-Base) with DexingNoClasspathTransform
 * Injecting the input artifact of a transform as a File has been deprecated. This is scheduled to be removed in Gradle 6.0. Declare the input artifact as Provider<FileSystemLocation> instead.
 */

import com.google.protobuf.gradle.*

buildscript {
    repositories {
        maven {
            setUrl("https://plugins.gradle.org/m2/")
            mavenCentral()
        }
    }
    dependencies {
        classpath("com.google.protobuf:protobuf-gradle-plugin:0.8.15")
    }
}

plugins {
    id("com.github.johnrengelman.shadow") version "6.1.0" apply true
    id("kotlin") apply true
    id("kotlin-kapt") apply true
    id("com.google.protobuf") apply true
}

DuplicatesStrategy.EXCLUDE

sourceSets {
    main {
        java {
            srcDirs("build/generated/source/proto/main/java/")
            srcDirs("build/generated/source/proto/main/grpc/")
        }
        proto {
            srcDirs("src/main/proto/")
        }
    }
}

dependencies {
    // Kotlin
    "implementation"("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.4.30")
    "implementation"("org.jetbrains.anko:anko:0.10.8")
    "implementation"("nl.komponents.kovenant:kovenant:3.3.0")
    // GRPC
    "implementation"("com.google.api.grpc:proto-google-common-protos:2.0.1")
    "implementation"("io.grpc:grpc-netty:1.35.0")
    "implementation"("io.grpc:grpc-protobuf:1.35.0")
    "implementation"("io.grpc:grpc-stub:1.35.0")
    // Circular FIFO
    "implementation"("org.apache.commons:commons-collections4:4.4")
    "implementation"("javax.annotation:javax.annotation-api:1.3.2")
    // JSON Support
    "implementation"("com.google.code.gson:gson:2.8.6")
}


protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.10.0"
    }
    plugins {
        id("grpc"){
            artifact = "io.grpc:protoc-gen-grpc-java:1.35.0"
        }
    }
    generateProtoTasks {
        ofSourceSet("main").forEach { task ->
            task.builtins {
                java
            }
            task.plugins {
                id("grpc")
            }
        }
    }
}


tasks.shadowJar {
    minimize()
   archiveVersion.set("1.0")
}