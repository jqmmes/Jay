package pt.up.fc.dcc.hyrax.odlib.utils

import pt.up.fc.dcc.hyrax.odlib.interfaces.VideoUtils
import com.arthenica.mobileffmpeg.FFmpeg

object VideoUtils : VideoUtils {
    override fun extractFrameAt(videoPath: String, min: Int, sec: Int, millis: Int, frameName: String) {
        FFmpeg.execute("ffmpeg -i %s -ss 00:%02d:%02d.%03d -vframes 1 %s.jpg".format(videoPath, min, sec, millis, frameName))
    }

    override fun extractFrames(videoPath: String, fps: Int) {
        FFmpeg.execute("ffmpeg -i %s -vf fps=%d thumb%%04d.jpg -hide_banner".format(videoPath, fps))
    }
}