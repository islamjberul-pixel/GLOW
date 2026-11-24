package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import kotlin.math.max

data class SavedScreenshotResult(
    val filePath: String,
    val width: Int,
    val height: Int
)

enum class ScreenshotErrorKind {
    UNSUPPORTED_FORMAT,
    ACCESS_DENIED,
    FILE_UNAVAILABLE,
    DECODE_FAILED,
    INSUFFICIENT_MEMORY
}

class ScreenshotImportException(
    val kind: ScreenshotErrorKind,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

object ScreenshotStorage {

    private const val SCREENSHOT_FILE_PREFIX = "macro_reference_screenshot_"
    private const val SCREENSHOT_FILE_EXT = ".jpg"
    const val MAX_LONG_EDGE_PX = 1920

    private val SUPPORTED_MIME_TYPES = setOf(
        "image/png",
        "image/jpeg",
        "image/jpg",
        "image/webp"
    )

    internal fun isSupportedProviderMime(mimeType: String?): Boolean {
        if (mimeType.isNullOrBlank()) return true
        val normalized = mimeType.trim().lowercase(Locale.US)
        return normalized == "application/octet-stream" ||
            normalized == "*/*" ||
            normalized in SUPPORTED_MIME_TYPES ||
            normalized.startsWith("image/")
    }

    internal fun isSupportedDecodedMime(mimeType: String?): Boolean {
        if (mimeType.isNullOrBlank()) return true
        val normalized = mimeType.trim().lowercase(Locale.US)
        return normalized in SUPPORTED_MIME_TYPES || normalized.startsWith("image/")
    }

    internal fun computeSampleSize(origWidth: Int, origHeight: Int, maxLongEdgePx: Int = MAX_LONG_EDGE_PX): Int {
        val longEdge = max(origWidth, origHeight)
        var sampleSize = 1
        while (longEdge / sampleSize > maxLongEdgePx) {
            sampleSize *= 2
        }
        return sampleSize
    }

    internal fun applyOrientationToDimensions(width: Int, height: Int, exifOrientation: Int): Pair<Int, Int> {
        val isSwapped = exifOrientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            exifOrientation == ExifInterface.ORIENTATION_ROTATE_270 ||
            exifOrientation == ExifInterface.ORIENTATION_TRANSPOSE ||
            exifOrientation == ExifInterface.ORIENTATION_TRANSVERSE
        return if (isSwapped) height to width else width to height
    }

    /**
     * Imports a screenshot Uri from Android Photo Picker, Gallery, DocumentsUI, or file providers
     * into internal app storage.
     */
    suspend fun importScreenshot(context: Context, uri: Uri): Result<SavedScreenshotResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver

                // Request persistable read permission only when supported by the returned URI/provider.
                runCatching {
                    resolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }

                // Pre-check MIME type when provider supplies a concrete non-generic type.
                val providerMime = runCatching { resolver.getType(uri) }.getOrNull()
                if (!isSupportedProviderMime(providerMime)) {
                    throw ScreenshotImportException(
                        ScreenshotErrorKind.UNSUPPORTED_FORMAT,
                        "Unsupported MIME type: $providerMime"
                    )
                }

                val tempRawFile = File(
                    context.cacheDir,
                    "raw_import_${System.currentTimeMillis()}.tmp"
                )
                try {
                    copyUriToTempFile(context, uri, tempRawFile)

                    val (origW, origH, decodedMime) = decodeBounds(tempRawFile)
                    if (!isSupportedDecodedMime(decodedMime)) {
                        throw ScreenshotImportException(
                            ScreenshotErrorKind.UNSUPPORTED_FORMAT,
                            "Unsupported image format: $decodedMime"
                        )
                    }

                    val exifOrientation = readExifOrientation(tempRawFile)
                    val (orientedOrigWidth, orientedOrigHeight) =
                        applyOrientationToDimensions(origW, origH, exifOrientation)

                    val decodedBitmap = decodeScaledBitmapSafely(tempRawFile, origW, origH)
                    val orientedBitmap = applyExifOrientation(decodedBitmap, exifOrientation)

                    val outFile = File(
                        context.filesDir,
                        "$SCREENSHOT_FILE_PREFIX${System.currentTimeMillis()}$SCREENSHOT_FILE_EXT"
                    )
                    val tempOutFile = File(
                        context.filesDir,
                        "${outFile.name}.tmp"
                    )

                    try {
                        FileOutputStream(tempOutFile).use { fos ->
                            val saved = orientedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                            if (!saved) {
                                throw ScreenshotImportException(
                                    ScreenshotErrorKind.DECODE_FAILED,
                                    "Could not write screenshot to local storage."
                                )
                            }
                        }

                        if (!tempOutFile.renameTo(outFile)) {
                            tempOutFile.copyTo(outFile, overwrite = true)
                            tempOutFile.delete()
                        }

                        cleanupOldScreenshots(context.filesDir, keepFile = outFile)

                        SavedScreenshotResult(
                            filePath = outFile.absolutePath,
                            width = orientedOrigWidth,
                            height = orientedOrigHeight
                        )
                    } finally {
                        if (!orientedBitmap.isRecycled) orientedBitmap.recycle()
                        if (tempOutFile.exists()) tempOutFile.delete()
                    }
                } finally {
                    if (tempRawFile.exists()) tempRawFile.delete()
                }
            }
        }

    private fun copyUriToTempFile(context: Context, uri: Uri, destFile: File) {
        try {
            val bytesCopied = openUriStreamWithFallback(context, uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output, bufferSize = 16 * 1024)
                }
            } ?: throw ScreenshotImportException(
                ScreenshotErrorKind.FILE_UNAVAILABLE,
                "Could not open image stream."
            )

            if (bytesCopied <= 0L || !destFile.exists() || destFile.length() <= 0L) {
                throw ScreenshotImportException(
                    ScreenshotErrorKind.FILE_UNAVAILABLE,
                    "Selected image file is empty."
                )
            }
        } catch (e: ScreenshotImportException) {
            throw e
        } catch (e: SecurityException) {
            throw ScreenshotImportException(
                ScreenshotErrorKind.ACCESS_DENIED,
                "Permission denied reading selected URI.",
                e
            )
        } catch (e: FileNotFoundException) {
            throw ScreenshotImportException(
                ScreenshotErrorKind.FILE_UNAVAILABLE,
                "Selected image file was not found.",
                e
            )
        } catch (e: Throwable) {
            throw ScreenshotImportException(
                ScreenshotErrorKind.FILE_UNAVAILABLE,
                e.message ?: "Unable to read selected image.",
                e
            )
        }
    }

    private fun openUriStreamWithFallback(context: Context, uri: Uri): InputStream? {
        val resolver = context.contentResolver
        runCatching { resolver.openInputStream(uri) }.getOrNull()?.let { return it }

        if (uri.scheme.equals("file", ignoreCase = true)) {
            val path = uri.path
            if (!path.isNullOrBlank()) {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    return FileInputStream(file)
                }
            }
        }

        val afd = runCatching { resolver.openAssetFileDescriptor(uri, "r") }.getOrNull()
        if (afd != null) {
            return runCatching { afd.createInputStream() }.getOrElse {
                runCatching { afd.close() }
                null
            }
        }

        val pfd = runCatching { resolver.openFileDescriptor(uri, "r") }.getOrNull()
        if (pfd != null) {
            return ParcelFileDescriptor.AutoCloseInputStream(pfd)
        }

        return null
    }

    private fun decodeBounds(file: File): Triple<Int, Int, String?> {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOptions)

        if (boundsOptions.outWidth > 0 && boundsOptions.outHeight > 0) {
            return Triple(boundsOptions.outWidth, boundsOptions.outHeight, boundsOptions.outMimeType)
        }

        // Fallback probe for formats handled by ImageDecoder on Android 9+ (e.g., certain WEBPs)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val probed = runCatching {
                var w = 0
                var h = 0
                val source = ImageDecoder.createSource(file)
                val bmp = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    w = info.size.width
                    h = info.size.height
                    decoder.setTargetSampleSize(4)
                }
                bmp.recycle()
                w to h
            }.getOrNull()

            if (probed != null && probed.first > 0 && probed.second > 0) {
                return Triple(probed.first, probed.second, boundsOptions.outMimeType ?: "image/webp")
            }
        }

        throw ScreenshotImportException(
            ScreenshotErrorKind.DECODE_FAILED,
            "Selected file could not be decoded as a valid image."
        )
    }

    private fun decodeScaledBitmapSafely(file: File, origWidth: Int, origHeight: Int): Bitmap {
        val sampleSize = computeSampleSize(origWidth, origHeight)

        val attempts = listOf(
            sampleSize to Bitmap.Config.ARGB_8888,
            sampleSize * 2 to Bitmap.Config.RGB_565,
            sampleSize * 4 to Bitmap.Config.RGB_565
        )

        var sawOom = false
        for ((attemptSample, config) in attempts) {
            val options = BitmapFactory.Options().apply {
                inSampleSize = attemptSample.coerceAtLeast(1)
                inPreferredConfig = config
            }
            try {
                val bmp = BitmapFactory.decodeFile(file.absolutePath, options)
                if (bmp != null) return bmp
            } catch (_: OutOfMemoryError) {
                sawOom = true
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(file)
                return ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.setTargetSampleSize(sampleSize.coerceAtLeast(1))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } catch (_: OutOfMemoryError) {
                sawOom = true
            } catch (_: Throwable) {
                // Fall through to decode error
            }
        }

        if (sawOom) {
            throw ScreenshotImportException(
                ScreenshotErrorKind.INSUFFICIENT_MEMORY,
                "Not enough memory to decode screenshot."
            )
        }
        throw ScreenshotImportException(
            ScreenshotErrorKind.DECODE_FAILED,
            "Failed to decode screenshot image."
        )
    }

    private fun readExifOrientation(file: File): Int {
        return runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }

        return try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) {
                bitmap.recycle()
            }
            rotated
        } catch (_: OutOfMemoryError) {
            bitmap
        }
    }

    private fun cleanupOldScreenshots(filesDir: File, keepFile: File) {
        filesDir.listFiles()?.forEach { file ->
            if (file != keepFile &&
                (file.name.startsWith(SCREENSHOT_FILE_PREFIX) || file.name == "macro_reference_screenshot.jpg")
            ) {
                runCatching { file.delete() }
            }
        }
    }

    fun isScreenshotValid(path: String): Boolean {
        if (path.isBlank()) return false
        val file = File(path)
        return file.exists() && file.isFile && file.length() > 0L
    }
}
