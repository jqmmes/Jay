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

buildscript {
    extra.apply{
        set("KOTLIN_VERSION", "1.4.30")
        set("ANKO_VERSION", "0.10.8")
        set("GRPC_VERSION", "1.35.0")
        set("TENSORFLOW_VERSION", "1.13.1")
        set("TENSORFLOW_VERSION_ANDROID", "1.13.1")
    }

    repositories {
        google()
        jcenter()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:4.1.2")
        classpath("gradle.plugin.me.lucas:fat-aar-plugin:1.0.9")
    }
}

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.4.30"
    id("com.google.protobuf") version "0.8.11"
    id("idea") apply true
}

allprojects {
    repositories {
        google()
        mavenCentral()
        jcenter()
    }
}