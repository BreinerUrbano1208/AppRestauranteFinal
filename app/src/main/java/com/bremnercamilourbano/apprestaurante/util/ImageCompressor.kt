package com.bremnercamilourbano.apprestaurante.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Reduce la foto elegida (lado máximo 1280 px, JPEG) antes de enviarla al servidor. */
object ImageCompressor {

    private const val MAX_SIDE = 1280
    private const val START_QUALITY = 80
    private const val MIN_QUALITY = 40
    private const val MAX_BYTES = 2_500_000 // el servidor acepta hasta 3 MB

    /** Devuelve los bytes del JPEG, o null si no se pudo leer la imagen. Llamar fuera del hilo principal. */
    fun compress(context: Context, uri: Uri): ByteArray? {
        return try {
            val resolver = context.contentResolver

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (bounds.outWidth / sample > MAX_SIDE * 2 || bounds.outHeight / sample > MAX_SIDE * 2) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return null

            val orientation = resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL

            val upright = rotate(decoded, orientation)
            val scale = minOf(1f, MAX_SIDE.toFloat() / max(upright.width, upright.height))
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    upright,
                    (upright.width * scale).roundToInt().coerceAtLeast(1),
                    (upright.height * scale).roundToInt().coerceAtLeast(1),
                    true
                )
            } else {
                upright
            }

            // JPEG no tiene transparencia: se pinta sobre fondo blanco.
            val flat = if (scaled.hasAlpha()) {
                Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888).also { out ->
                    val canvas = Canvas(out)
                    canvas.drawColor(Color.WHITE)
                    canvas.drawBitmap(scaled, 0f, 0f, null)
                }
            } else {
                scaled
            }

            var quality = START_QUALITY
            var bytes: ByteArray
            do {
                val stream = ByteArrayOutputStream()
                flat.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                bytes = stream.toByteArray()
                quality -= 10
            } while (bytes.size > MAX_BYTES && quality >= MIN_QUALITY)

            if (bytes.size > MAX_BYTES) null else bytes
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    private fun rotate(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
