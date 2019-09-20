package pt.up.fc.dcc.hyrax.odlib.utils

import pt.up.fc.dcc.hyrax.odlib.interfaces.VideoUtils
import com.arthenica.mobileffmpeg.FFmpeg
import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger

object VideoUtils : VideoUtils {

    private val LOCK = Any()

    override fun extractFrameAt(videoPath: String, min: Int, sec: Int, millis: Int, framePath: String, frameName: String) {
        ODLogger.logInfo("INIT", actions = *arrayOf("VIDEO_PATH=$videoPath","FRAME_TIME=$min:$sec.$millis", "FRAME_OUTPUT=$framePath/$frameName"))
        synchronized(LOCK) { FFmpeg.execute("-i $videoPath -ss 00:%02d:%02d.%03d -vframes 1 $framePath/$frameName.jpg".format(min, sec, millis)) }
        ODLogger.logInfo("COMPLETE", actions = *arrayOf("VIDEO_PATH=$videoPath","FRAME_TIME=$min:$sec.$millis", "FRAME_OUTPUT=$framePath/$frameName"))
    }

    override fun extractFrames(videoPath: String, fps: Int, thumbPath: String, thumbName: String) {
        ODLogger.logInfo("INIT", actions = *arrayOf("VIDEO_PATH=$videoPath","FPS=$fps", "FRAME_OUTPUT=$thumbPath/$thumbName"))
        synchronized(LOCK) { FFmpeg.execute("-i $videoPath -vf fps=$fps $thumbPath/$thumbName%04d.jpg -hide_banner") }
        ODLogger.logInfo("COMPLETE", actions = *arrayOf("VIDEO_PATH=$videoPath","FPS=$fps", "FRAME_OUTPUT=$thumbPath/$thumbName"))
    }
}