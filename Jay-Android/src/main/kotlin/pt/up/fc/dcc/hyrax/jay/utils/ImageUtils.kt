/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 */

package pt.up.fc.dcc.hyrax.jay.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import java.io.ByteArrayOutputStream
import java.lang.Thread.sleep
import kotlin.math.floor
import kotlin.math.max

@Suppress("unused")
object ImageUtils {

    private val LOCK = Any()
    private val LOCK_2 = Any()

    fun getBitmapFromByteArray(imgData: ByteArray) : Bitmap {
        var data: Bitmap?
        synchronized(LOCK_2) {
            while ((Runtime.getRuntime().maxMemory() - Runtime.getRuntime().totalMemory()) + Runtime.getRuntime().freeMemory
                    () < imgData.size * 2) {
                sleep(100)
            }
            JayLogger.logError("DECODE_BITMAP", actions = arrayOf("BITMAP_SIZE=${imgData.size}", "MAX_HEAP_SIZE=${
                Runtime
                        .getRuntime().maxMemory()
            }", "FREE_HEAP_SIZE=${
                Runtime
                        .getRuntime().freeMemory()
            }"))
            data = BitmapFactory.decodeByteArray(imgData, 0, imgData.size)
        }
        return data!!
    }

    fun getByteArrayFromImage(imgPath: String): ByteArray {
        JayLogger.logInfo("INIT")
        val stream = ByteArrayOutputStream()
        synchronized(LOCK) {
            while ((Runtime.getRuntime().maxMemory() - Runtime.getRuntime().totalMemory()) + Runtime.getRuntime().freeMemory
                    () < 150000000) {
                sleep(100)
            }
            BitmapFactory.decodeFile(imgPath).compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        JayLogger.logInfo("COMPLETE")
        return stream.toByteArray()
    }

    fun getByteArrayFromBitmap(imgBitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        imgBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    fun scaleImage(image : Bitmap, maxSize : Int) : Bitmap {
        if (image.width == maxSize && image.height == maxSize) return image
        JayLogger.logInfo("INIT")
        val scale = maxSize.toFloat()/ max(image.width, image.height)
        val scaledImage = Bitmap.createScaledBitmap(image, floor(image.width*scale).toInt(), floor(image
                .height*scale).toInt(), false)
        val scaledData = Bitmap.createBitmap(maxSize, maxSize, scaledImage.config)
        JayLogger.logInfo("RESIZE_IMAGE", actions = arrayOf("NEW_DIMENSIONS_WIDTH=${scaledImage.width}, NEW_DIMENSIONS_HEIGHT=${scaledImage.height}"))
        val pixels = IntArray(scaledImage.width * scaledImage.height)
        scaledImage.getPixels(pixels, 0, scaledImage.width, 0, 0, scaledImage.width, scaledImage.height)
        scaledData.setPixels(pixels, 0, scaledImage.width, 0, 0, scaledImage.width, scaledImage.height)
        image.recycle()
        scaledImage.recycle()
        JayLogger.logInfo("COMPLETE")
        return  scaledData
    }
}