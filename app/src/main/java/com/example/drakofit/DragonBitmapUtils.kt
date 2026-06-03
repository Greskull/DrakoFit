package tu.paquete.aqui  // 👈 ajusta esto a tu proyecto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.BitmapFactory
import org.maplibre.android.geometry.LatLng

object DragonBitmapUtils {

    fun rotateBitmap(
        context: Context,
        drawableRes: Int,
        degrees: Float
    ): Bitmap {

        val original = BitmapFactory.decodeResource(
            context.resources,
            drawableRes
        )

        val matrix = Matrix()
        matrix.postRotate(degrees)

        return Bitmap.createBitmap(
            original,
            0,
            0,
            original.width,
            original.height,
            matrix,
            true
        )
    }

    fun burnDragonBitmap(bitmap: Bitmap): Bitmap {

        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint()

        val colorMatrix = ColorMatrix(
            floatArrayOf(
                1.25f, 0f, 0f, 0f, 25f,
                0f, 1.10f, 0f, 0f, 10f,
                0f, 0f, 0.85f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return result
    }

    fun getDirectionAngle(oldPoint: LatLng, newPoint: LatLng): Float {

        val dLon = Math.toRadians(newPoint.longitude - oldPoint.longitude)
        val lat1 = Math.toRadians(oldPoint.latitude)
        val lat2 = Math.toRadians(newPoint.latitude)

        val y = kotlin.math.sin(dLon) * kotlin.math.cos(lat2)
        val x = kotlin.math.cos(lat1) * kotlin.math.sin(lat2) -
                kotlin.math.sin(lat1) * kotlin.math.cos(lat2) * kotlin.math.cos(dLon)

        val bearing = Math.toDegrees(kotlin.math.atan2(y, x))

        return ((bearing + 360) % 360).toFloat()
    }
}