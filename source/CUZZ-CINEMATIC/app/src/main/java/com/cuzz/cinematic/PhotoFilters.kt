package com.cuzz.cinematic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

object PhotoFilters {

    var selected = "Original"
    var tone = 0f
    var warmth = 0f

    val filters = listOf(
        "Original",
        "Natural",
        "Vivid",
        "Warm",
        "Cool",
        "Dramatic",
        "Mono",
        "Silvertone",
        "Noir"
    )

    fun apply(source: Bitmap): Bitmap {
        val saturation = when (selected) {
            "Vivid" -> 1.45f
            "Warm" -> 1.15f
            "Cool" -> 1.10f
            "Dramatic" -> 1.30f
            "Mono", "Silvertone", "Noir" -> 0f
            else -> 1f
        }

        val contrast = when (selected) {
            "Dramatic" -> 1.35f
            "Noir" -> 1.65f
            "Silvertone" -> 1.20f
            else -> 1f
        }

        val brightness = when (selected) {
            "Warm" -> 5f
            "Cool" -> 2f
            "Noir" -> -8f
            else -> 0f
        } + tone

        val red = 1f + warmth / 100f
        val blue = 1f - warmth / 100f

        val matrix = ColorMatrix()
        matrix.setSaturation(saturation)

        val adjustment = ColorMatrix(
            floatArrayOf(
                contrast * red, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast * blue, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )

        matrix.postConcat(adjustment)

        val result = Bitmap.createBitmap(
            source.width,
            source.height,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(result)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }

        canvas.drawBitmap(source, 0f, 0f, paint)

        return result
    }
}
