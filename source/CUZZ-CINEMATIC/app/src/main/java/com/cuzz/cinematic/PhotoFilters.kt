
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
    var intensity = 100

    val filters = listOf(
        "Original",
        "ICE BLUE",
        "ARCTIC",
        "FROZEN",
        "MIDNIGHT",
        "COOL SOFT",
        "TEAL CINEMA",
        "WARM GOLD",
        "KODAK PORTRA",
        "VINTAGE",
        "RETRO",
        "MOCHA",
        "CLEAN",
        "NATURAL",
        "VIVID",
        "NOIR",
        "DRAMATIC",
        "SUNSET",
        "PASTEL"
    )

    private data class Profile(
        val saturation: Float = 1f,
        val contrast: Float = 1f,
        val brightness: Float = 0f,
        val red: Float = 1f,
        val green: Float = 1f,
        val blue: Float = 1f
    )

    private fun profile(name: String): Profile {
        return when (name) {
            "ICE BLUE" -> Profile(1.12f, 1.12f, 3f, 0.96f, 1.02f, 1.14f)
            "ARCTIC" -> Profile(0.90f, 1.10f, 5f, 0.95f, 1.03f, 1.16f)
            "FROZEN" -> Profile(1.18f, 1.16f, 2f, 0.92f, 1.02f, 1.20f)
            "MIDNIGHT" -> Profile(0.75f, 1.35f, -12f, 0.88f, 0.94f, 1.12f)
            "COOL SOFT" -> Profile(0.92f, 0.94f, 7f, 0.97f, 1.02f, 1.08f)
            "TEAL CINEMA" -> Profile(0.88f, 1.22f, -3f, 0.92f, 1.06f, 1.08f)
            "WARM GOLD" -> Profile(1.12f, 1.08f, 6f, 1.13f, 1.04f, 0.88f)
            "KODAK PORTRA" -> Profile(0.92f, 1.06f, 5f, 1.08f, 1.02f, 0.93f)
            "VINTAGE" -> Profile(0.78f, 0.90f, 8f, 1.10f, 1.02f, 0.88f)
            "RETRO" -> Profile(0.85f, 1.12f, 3f, 1.12f, 0.98f, 0.86f)
            "MOCHA" -> Profile(0.82f, 1.08f, 2f, 1.12f, 1.00f, 0.88f)
            "CLEAN" -> Profile(1.05f, 1.04f, 4f)
            "NATURAL" -> Profile()
            "VIVID" -> Profile(1.45f, 1.12f, 2f)
            "NOIR" -> Profile(0f, 1.55f, -7f)
            "DRAMATIC" -> Profile(1.25f, 1.38f, -3f)
            "SUNSET" -> Profile(1.22f, 1.12f, 5f, 1.16f, 1.04f, 0.82f)
            "PASTEL" -> Profile(0.78f, 0.86f, 12f, 1.06f, 1.04f, 1.02f)
            else -> Profile()
        }
    }

    fun apply(source: Bitmap): Bitmap {
        val amount = intensity.coerceIn(0, 100) / 100f
        val p = profile(selected)

        fun mix(original: Float, target: Float): Float {
            return original + (target - original) * amount
        }

        val saturation = mix(1f, p.saturation)
        val contrast = mix(1f, p.contrast)
        val brightness = p.brightness * amount + tone

        val red = mix(1f, p.red) * (1f + warmth / 100f)
        val green = mix(1f, p.green)
        val blue = mix(1f, p.blue) * (1f - warmth / 100f)

        val matrix = ColorMatrix()
        matrix.setSaturation(saturation)

        val adjustment = ColorMatrix(
            floatArrayOf(
                contrast * red, 0f, 0f, 0f, brightness,
                0f, contrast * green, 0f, 0f, brightness,
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
