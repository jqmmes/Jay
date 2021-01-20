# Jay: Adaptive Computation Offloading for Hybrid Cloud Environments

Jay is a framework written in kotlin capable of offloading computations for hybrid cloud environments.

## Usage:

Clone this repository to your IDE of choice, and depending on the platform 
you want to develop to, import Jay-Android or Jay-x86 to your project.

## Requirements

In order to build Jay, your machine must have installed the latest version of
gRPC and protobuf.

## Building:

### Jay-x86
Using gradle build your application.

### Jay-Android
Using your IDE of choice, compile code with target API 30 and min SDK version 24.


## Implementation examples:

### Jay-x86

Build Jay-x86 Launcher using *gradle build* and control this application using
gRPC. This application will start a server running Jay with the built-in executor.

#### Available commands:
- StartWorker
- Stop
- SetLogName

### Jay-Android

Using your IDE of choice, build Jay-Android Launcher and control this application
using gRPC. This application will start in the background of your android device, 
with a notification on the notification area, enabling the start of jay services.

#### Available commands:
- StartScheduler
- StartWorker
- StartProfiler
- SetLogName

#### Submitting jobs:
In order to submit a job you must use the available broker commands detailed in 
JayProto.proto (under Jay-Base/proto).



Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).