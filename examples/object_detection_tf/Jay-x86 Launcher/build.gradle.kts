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
    id("org.jetbrains.kotlin.jvm")
    id("com.github.johnrengelman.shadow") version "6.1.0" apply true
    id("application") apply true
    id("kotlin") apply true
    id("com.google.protobuf")
}

dependencies {
    "implementation"("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.4.30")
    "implementation"("io.grpc:grpc-okhttp:1.35.0")
    "implementation"("io.grpc:grpc-protobuf:1.35.0")
    "implementation"("io.grpc:grpc-stub:1.35.0")
    "implementation"("org.kamranzafar:jtar:2.3")
    "implementation"("org.tensorflow:tensorflow:1.13.1")
    "implementation"("org.tensorflow:libtensorflow:1.13.1")
    "implementation"("org.tensorflow:proto:1.13.1")
    "implementation"("javax.annotation:javax.annotation-api:1.3.2")
    "api"(project(":Jay-x86"))
    "api"(project(":Jay-Base"))
}

DuplicatesStrategy.EXCLUDE

tasks.whenTaskAdded {
    if (this.name.startsWith("extractInclude") && this.name.endsWith("Proto")) {
        DuplicatesStrategy.EXCLUDE
    }
}

protobuf {
    DuplicatesStrategy.EXCLUDE
    protoc {
        // The artifact spec for the Protobuf Compiler
        artifact = "com.google.protobuf:protoc:3.15.1"
    }
    plugins {
        // Optional: an artifact spec for a protoc plugin, with "grpc" as
        // the identifier, which can be referred to in the "plugins"
        // container of the "generateProtoTasks" closure.
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.35.0"
        }
    }
    generateProtoTasks {
        ofSourceSet("main").forEach {
            it.builtins {
                java
            }
            // Apply the "grpc" plugin whose spec is defined above, without
            // options.  Note the braces cannot be omitted, otherwise the
            // plugin will not be added. This is because of the implicit way
            // NamedDomainObjectContainer binds the methods.
            it.plugins {
                id("grpc")
            }
        }
    }
}

sourceSets {
    main {
        java {
            srcDirs("build/generated/source/proto/main/grpc")
            srcDirs("build/generated/source/proto/main/java")
        }
    }
}

tasks.shadowJar {
    archiveBaseName.set("Jay-Tensorflow-Launcher-x86")
    archiveVersion.set("1.0")

    manifest {
        attributes["Main-Class"] = "pt.up.fc.dcc.hyrax.jay.X86JayLauncher"
        attributes["Class-Path"] = "libs/Jay-x86.jar"
    }
}

tasks.distZip.get().enabled = false
tasks.distTar.get().enabled = false
tasks.shadowDistZip.get().enabled = false
tasks.shadowDistTar.get().enabled = false

application {
    setProperty("mainClassName", "pt.up.fc.dcc.hyrax.jay.X86JayLauncher")
}

tasks.compileKotlin {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

tasks.compileTestKotlin {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}