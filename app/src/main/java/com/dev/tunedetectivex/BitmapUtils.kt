package com.dev.tunedetectivex

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.Log
import android.widget.ImageView
import androidx.core.graphics.createBitmap
import java.net.URL

object BitmapUtils {

    private const val TAG = "BitmapUtils"

    fun loadBitmapFromUrl(
        activity: Activity,
        url: String,
        imageView: ImageView,
        cornerRadius: Float = 0f,
        isCircular: Boolean = false,
        placeholderResId: Int
    ) {
        imageView.setImageResource(placeholderResId)

        Thread {
            try {
                val input = URL(url).openStream()
                val bitmap = BitmapFactory.decodeStream(input)
                val processedBitmap = if (isCircular) {
                    getCircularBitmap(bitmap)
                } else {
                    getRoundedBitmap(bitmap, cornerRadius)
                }
                activity.runOnUiThread {
                    imageView.setImageBitmap(processedBitmap)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading image: ${e.message}")
            }
        }.start()
    }

    private fun getRoundedBitmap(bitmap: Bitmap, cornerRadius: Float): Bitmap {
        val output = createBitmap(bitmap.width, bitmap.height)
        val canvas = Canvas(output)
        val paint = Paint()
        val path = Path()

        path.addRoundRect(
            0f,
            0f,
            bitmap.width.toFloat(),
            bitmap.height.toFloat(),
            cornerRadius,
            cornerRadius,
            Path.Direction.CW
        )
        canvas.clipPath(path)

        paint.shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return output
    }

    private fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val size = bitmap.width.coerceAtMost(bitmap.height)
        val output = createBitmap(size, size)
        val canvas = Canvas(output)
        val paint = Paint()
        val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

        paint.isAntiAlias = true
        paint.shader = shader

        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        return output
    }

    fun loadBitmapFromUrlSync(url: String): Bitmap? {
        return try {
            val input = URL(url).openStream()
            BitmapFactory.decodeStream(input)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap from URL: ${e.message}", e)
            null
        }
    }
}