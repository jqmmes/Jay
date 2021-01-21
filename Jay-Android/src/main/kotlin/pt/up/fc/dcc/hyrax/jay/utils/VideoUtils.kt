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

package pt.up.fc.dcc.hyrax.jay.utils

import com.arthenica.mobileffmpeg.FFmpeg
import pt.up.fc.dcc.hyrax.jay.interfaces.VideoUtils
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger

object VideoUtils : VideoUtils {

    private val LOCK = Any()

    override fun extractFrameAt(videoPath: String, min: Int, sec: Int, millis: Int, framePath: String, frameName: String) {
        JayLogger.logInfo("INIT", actions = arrayOf("VIDEO_PATH=$videoPath", "FRAME_TIME=$min:$sec.$millis", "FRAME_OUTPUT=$framePath/$frameName"))
        synchronized(LOCK) { FFmpeg.execute("-i $videoPath -ss 00:%02d:%02d.%03d -vframes 1 $framePath/$frameName.jpg".format(min, sec, millis)) }
        JayLogger.logInfo("COMPLETE", actions = arrayOf("VIDEO_PATH=$videoPath", "FRAME_TIME=$min:$sec.$millis", "FRAME_OUTPUT=$framePath/$frameName"))
    }

    override fun extractFrames(videoPath: String, fps: Int, thumbPath: String, thumbName: String) {
        JayLogger.logInfo("INIT", actions = arrayOf("VIDEO_PATH=$videoPath", "FPS=$fps", "FRAME_OUTPUT=$thumbPath/$thumbName"))
        synchronized(LOCK) { FFmpeg.execute("-i $videoPath -vf fps=$fps $thumbPath/$thumbName%04d.jpg -hide_banner") }
        JayLogger.logInfo("COMPLETE", actions = arrayOf("VIDEO_PATH=$videoPath", "FPS=$fps", "FRAME_OUTPUT=$thumbPath/$thumbName"))
    }
}