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
    id("com.github.johnrengelman.shadow") version "6.1.0" apply true
    id("kotlin")
    id("java")
}

DuplicatesStrategy.EXCLUDE

dependencies {
    "implementation"("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.4.30")
    "implementation"("com.google.api.grpc:proto-google-common-protos:2.0.1")
    "api"(project(":Jay-Base"))
}

tasks.shadowJar {
    minimize()
    configurations.add(project.configurations["compile"])
    exclude("*.aar", "*.proto", "module-info.class")
    dependencies {
        exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.4.30"))
    }
    archiveBaseName.set("Jay-x86-shadow")
    archiveVersion.set("1.0")
    archiveClassifier.set("")
}

tasks {
    build {
        dependsOn(shadowJar)
    }
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