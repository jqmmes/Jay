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

package pt.up.fc.dcc.hyrax.jay.interfaces

interface VideoUtils {
    fun extractFrames(videoPath: String, fps: Int, thumbPath: String, thumbName: String)  // ffmpeg -i video.webm -vf fps=1 thumb%04d.jpg -hide_banner
    fun extractFrameAt(videoPath: String, min: Int, sec : Int, millis : Int, framePath: String, frameName: String)  // ffmpeg -i video.webm -ss 00:00:07.000 -vframes 1 thumb.jpg
}