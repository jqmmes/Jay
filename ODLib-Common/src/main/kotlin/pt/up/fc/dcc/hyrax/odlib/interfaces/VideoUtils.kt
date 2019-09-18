package pt.up.fc.dcc.hyrax.odlib.interfaces

interface VideoUtils {
    fun extractFrames(videoPath: String, fps: Int)  // ffmpeg -i video.webm -vf fps=1 thumb%04d.jpg -hide_banner
    fun extractFrameAt(videoPath: String, min: Int, sec : Int, millis : Int, frameName: String)  // ffmpeg -i video.webm -ss 00:00:07.000 -vframes 1 thumb.jpg
}