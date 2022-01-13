# Jay: Adaptive Computation Offloading for Hybrid Cloud Environments

Jay is a framework written in kotlin capable of offloading computations for hybrid cloud environments.

> Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
>
> Author: Joaquim Silva
>
> This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
> This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
> You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.


## Usage:

Clone this repository to your IDE of choice, and depending on the platform
you want to develop to, import Jay-Android or Jay-x86 to your project.

For more information on how to use Jay please check chapters 3, 4 and appendix B
from my PhD Thesis. You can download it [here](https://joaquimsilva.me/thesis.pdf).

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
