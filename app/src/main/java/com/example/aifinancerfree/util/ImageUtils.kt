package com.example.aifinancerfree.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.max

object ImageUtils {
    private const val TAG = "ImageUtils"
    private const val MAX_DIMENSION = 1280
    private const val JPEG_QUALITY = 80

    /**
     * Reads an image Uri, applies EXIF rotation if needed, downsamples to max 1280px,
     * and compresses to JPEG bytes (~150KB-250KB).
     */
    fun compressAndOrientImage(context: Context, uri: Uri): ByteArray {
        val contentResolver = context.contentResolver

        // 1. Determine image dimensions without allocating full pixel memory
        var inputStream: InputStream? = contentResolver.openInputStream(uri)
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream?.close()

        val srcWidth = options.outWidth
        val srcHeight = options.outHeight

        // 2. Calculate inSampleSize to avoid OOM
        var inSampleSize = 1
        val maxSrcDimension = max(srcWidth, srcHeight)
        while ((maxSrcDimension / (inSampleSize * 2)) >= MAX_DIMENSION) {
            inSampleSize *= 2
        }

        // 3. Decode scaled down bitmap
        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        inputStream = contentResolver.openInputStream(uri)
        val decodedBitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            ?: throw IllegalStateException("Failed to decode image bitmap from uri: $uri")
        inputStream?.close()

        // 4. Read EXIF orientation
        var orientation = ExifInterface.ORIENTATION_NORMAL
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not determine EXIF orientation, proceeding with normal", e)
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }

        // 5. Downscale precisely to max MAX_DIMENSION if still larger
        val curWidth = decodedBitmap.width
        val curHeight = decodedBitmap.height
        val maxCur = max(curWidth, curHeight)
        if (maxCur > MAX_DIMENSION) {
            val scale = MAX_DIMENSION.toFloat() / maxCur
            matrix.postScale(scale, scale)
        }

        val orientedBitmap = if (!matrix.isIdentity) {
            Bitmap.createBitmap(decodedBitmap, 0, 0, decodedBitmap.width, decodedBitmap.height, matrix, true)
        } else {
            decodedBitmap
        }

        // 6. Compress to JPEG byte array
        val outputStream = ByteArrayOutputStream()
        orientedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
        val compressedBytes = outputStream.toByteArray()

        if (orientedBitmap != decodedBitmap) {
            decodedBitmap.recycle()
        }
        orientedBitmap.recycle()

        Log.i(TAG, "Compressed receipt image from ${srcWidth}x${srcHeight} to ${compressedBytes.size / 1024} KB")
        return compressedBytes
    }
}
