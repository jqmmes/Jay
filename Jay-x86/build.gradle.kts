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
    id("java-library")
}

tasks.shadowJar {
    minimize()
    archiveBaseName.set("Jay-x86")
    archiveVersion.set("1.0")
}

dependencies {
    "implementation"("org.kamranzafar:jtar:2.3")
    "implementation"("org.tensorflow:tensorflow:1.13.1")
    "implementation"("org.tensorflow:libtensorflow:1.13.1")
    "implementation"("org.tensorflow:proto:1.13.1")
    "implementation"("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.4.30")
    "implementation"("com.google.guava:guava:30.1-jre")
    "implementation"(project(":Jay-Base"))
}