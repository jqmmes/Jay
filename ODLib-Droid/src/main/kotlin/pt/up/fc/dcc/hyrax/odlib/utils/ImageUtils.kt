package pt.up.fc.dcc.hyrax.odlib.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.floor
import kotlin.math.max

object ImageUtils {

    fun getBitmapFromByteArray(imgData: ByteArray) : Bitmap{
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

    fun getImageBitmapFromFile(imgPath: File): Bitmap? {
        return BitmapFactory.decodeFile(imgPath.absolutePath)
    }

    fun scaleImage(image : Bitmap, maxSize : Float) : Bitmap {
        //var data = BitmapFactory.decodeByteArray(imgData, 0, imgData.size)
        ODLogger.logInfo("Scaling Job...")
        val scale = maxSize/ max(image.width, image.height)
        val scaledImage = Bitmap.createScaledBitmap(image, floor(image.width*scale).toInt(), floor(image
                .height*scale).toInt(), false)
        //val scaledData = Bitmap.createBitmap(300, 300, scaledImage.config)
        val scaledData = Bitmap.createBitmap(maxSize.toInt(),maxSize.toInt(), scaledImage.config)
        println("${scaledImage.width} * ${scaledImage.height}")
        val pixels = IntArray(scaledImage.width * scaledImage.height)
        scaledImage.getPixels(pixels, 0, scaledImage.width, 0, 0, scaledImage.width, scaledImage.height)
        scaledData.setPixels(pixels, 0, scaledImage.width, 0, 0, scaledImage.width, scaledImage.height)
        //scaledData.setPixels(pixels, 0, 300, 0, 0, scaledImage.width, scaledImage.height)
        scaledImage.recycle()
        ODLogger.logInfo("Scaling Job... done")
        return  scaledData
    }

    fun scaleImage(imgData: ByteArray, maxSize: Float) : Bitmap {
        return scaleImage(getBitmapFromByteArray(imgData), maxSize)
    }
}