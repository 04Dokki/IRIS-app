package com.iris.security.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ImageDownloadManager {

    private const val IRIS_FOLDER = "IRIS Security"

    sealed class DownloadResult {
        data class Success(val filePath: String) : DownloadResult()
        data class Error(val message: String) : DownloadResult()
    }

    /**
     * Downloads an image from a URL and saves it to the phone's Pictures/IRIS Security gallery.
     * Uses MediaStore on API 29+ (scoped storage), fallback to legacy for older.
     */
    suspend fun downloadImage(
        context: Context,
        imageUrl: String,
        fileName: String = generateFileName()
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val bitmap = fetchBitmap(imageUrl)
                ?: return@withContext DownloadResult.Error("Could not load image from device")

            return@withContext if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(context, bitmap, fileName)
            } else {
                saveToLegacyStorage(context, bitmap, fileName)
            }
        } catch (e: Exception) {
            DownloadResult.Error("Download failed: ${e.message}")
        }
    }

    /**
     * Saves a Bitmap that was already captured from the live stream (no URL needed).
     */
    suspend fun saveBitmap(
        context: Context,
        bitmap: Bitmap,
        fileName: String = generateFileName()
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            return@withContext if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(context, bitmap, fileName)
            } else {
                saveToLegacyStorage(context, bitmap, fileName)
            }
        } catch (e: Exception) {
            DownloadResult.Error("Save failed: ${e.message}")
        }
    }

    private fun fetchBitmap(imageUrl: String): Bitmap? {
        val connection = URL(imageUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.connect()
        val stream: InputStream = connection.inputStream
        return BitmapFactory.decodeStream(stream).also {
            stream.close()
            connection.disconnect()
        }
    }

    private fun saveViaMediaStore(
        context: Context,
        bitmap: Bitmap,
        fileName: String
    ): DownloadResult {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/$IRIS_FOLDER")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return DownloadResult.Error("Could not create image entry in gallery")

        resolver.openOutputStream(uri)?.use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
        }

        contentValues.clear()
        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, contentValues, null, null)

        return DownloadResult.Success(
            "${Environment.DIRECTORY_PICTURES}/$IRIS_FOLDER/$fileName"
        )
    }

    private fun saveToLegacyStorage(
        context: Context,
        bitmap: Bitmap,
        fileName: String
    ): DownloadResult {
        val picturesDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES
        )
        val irisDir = File(picturesDir, IRIS_FOLDER).also { it.mkdirs() }
        val file = File(irisDir, fileName)

        FileOutputStream(file).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
        }

        // Notify the media scanner
        android.media.MediaScannerConnection.scanFile(
            context, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null
        )

        return DownloadResult.Success(file.absolutePath)
    }

    fun generateFileName(): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return "IRIS_${sdf.format(Date())}.jpg"
    }
}
