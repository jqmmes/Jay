package pt.up.fc.dcc.hyrax.odlib.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.floor
import kotlin.math.max
import com.arthenica.mobileffmpeg.FFmpeg

object ImageUtils {

    fun getBitmapFromByteArray(imgData: ByteArray) : Bitmap {
        return BitmapFactory.decodeByteArray(imgData, 0, imgData.size)
    }

    fun getByteArrayFromImage(imgPath: String): ByteArray {
        val stream = ByteArrayOutputStream()
        BitmapFactory.decodeFile(imgPath).compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    fun getByteArrayFromBitmap(imgBitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        imgBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    fun getByteArrayFromBitmapFast(imgBitmap: Bitmap) : ByteArray {
        val byteBuffer = ByteBuffer.allocate(imgBitmap.byteCount)
        imgBitmap.copyPixelsToBuffer(byteBuffer)
        return byteBuffer.array()
    }

    fun getImageBitmapFromFile(imgPath: File): Bitmap? {
        return BitmapFactory.decodeFile(imgPath.absolutePath)
    }

    fun scaleImage(image : Bitmap, maxSize : Int) : Bitmap {
        if (image.width == maxSize && image.height == maxSize) return image
        ODLogger.logInfo("INIT")
        val scale = maxSize.toFloat()/ max(image.width, image.height)
        val scaledImage = Bitmap.createScaledBitmap(image, floor(image.width*scale).toInt(), floor(image
                .height*scale).toInt(), false)
        val scaledData = Bitmap.createBitmap(maxSize, maxSize, scaledImage.config)
        ODLogger.logInfo("RESIZE_IMAGE", actions = *arrayOf("NEW_DIMENSIONS_WIDTH=${scaledImage.width}, NEW_DIMENSIONS_HEIGHT=${scaledImage.height}"))
        val pixels = IntArray(scaledImage.width * scaledImage.height)
        scaledImage.getPixels(pixels, 0, scaledImage.width, 0, 0, scaledImage.width, scaledImage.height)
        scaledData.setPixels(pixels, 0, scaledImage.width, 0, 0, scaledImage.width, scaledImage.height)
        image.recycle()
        scaledImage.recycle()
        ODLogger.logInfo("COMPLETE")
        return  scaledData
    }

    fun scaleImage(imgData: ByteArray, maxSize: Int) : Bitmap {
        return scaleImage(getBitmapFromByteArray(imgData), maxSize)
    }
}