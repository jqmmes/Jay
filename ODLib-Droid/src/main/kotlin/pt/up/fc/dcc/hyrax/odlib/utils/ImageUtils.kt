package pt.up.fc.dcc.hyrax.odlib.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.floor
import kotlin.math.max

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
        ODLogger.logInfo("Resizing Image...")
        val scale = maxSize.toFloat()/ max(image.width, image.height)
        val scaledImage = Bitmap.createScaledBitmap(image, floor(image.width*scale).toInt(), floor(image
                .height*scale).toInt(), false)
        val scaledData = Bitmap.createBitmap(maxSize, maxSize, scaledImage.config)
        ODLogger.logInfo("New image dimensions: ${scaledImage.width} * ${scaledImage.height} (W * H)")
        val pixels = IntArray(scaledImage.width * scaledImage.height)
        scaledImage.getPixels(pixels, 0, scaledImage.width, 0, 0, scaledImage.width, scaledImage.height)
        scaledData.setPixels(pixels, 0, scaledImage.width, 0, 0, scaledImage.width, scaledImage.height)
        image.recycle()
        scaledImage.recycle()
        ODLogger.logInfo("Resizing Image... done")
        return  scaledData
    }

    fun scaleImage(imgData: ByteArray, maxSize: Int) : Bitmap {
        return scaleImage(getBitmapFromByteArray(imgData), maxSize)
    }
}