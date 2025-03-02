package com.rabbyx.apkrey

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object DexImageExtractor {

    // Extract and generate a 256x256 DEX image from an APK
    fun extractDexImage(appInfo: ApplicationInfo): ByteArray {
        val apkFile = File(appInfo.sourceDir)

        // Extract all DEX files from APK
        val dexFiles = extractDexFilesFromAPK(apkFile)

        if (dexFiles.isEmpty()) {
            return ByteArray(0) // No DEX files found
        }

        // Convert the first DEX file into a 256x256 image
        val dexData = dexFiles.first()
        val bitmap = createDexImage(dexData)
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 256, 256, true)

        return bitmapToByteArray(resizedBitmap)
    }

    // Extracts DEX files from an APK
    private fun extractDexFilesFromAPK(apkFile: File): List<ByteArray> {
        val dexFiles = mutableListOf<ByteArray>()

        try {
            FileInputStream(apkFile).use { fileStream ->
                ZipInputStream(fileStream).use { zipStream ->
                    var entry: ZipEntry?
                    val buffer = ByteArray(4096) // Buffer for reading DEX files

                    while (zipStream.nextEntry.also { entry = it } != null) {
                        entry?.let {
                            if (it.name.endsWith(".dex")) {
                                val output = ByteArrayOutputStream()
                                var bytesRead: Int
                                while (zipStream.read(buffer).also { bytesRead = it } != -1) {
                                    output.write(buffer, 0, bytesRead)
                                }
                                dexFiles.add(output.toByteArray())
                            }
                        }
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return dexFiles
    }

    // Converts DEX file bytes into a grayscale image
    private fun createDexImage(dexData: ByteArray): Bitmap {
        val width = 256
        val height = 256
        val expectedSize = width * height * 4
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // Pad or trim dexData to ensure it fills the expected size
        val scaledData = scaleDexDataToImage(dexData, expectedSize)

        // Set pixel values from scaled dexData
        var index = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val value = scaledData[index++].toInt() and 0xFF
                val color = Color.rgb(value, value, value)
                bitmap.setPixel(x, y, color)
            }
        }

        return bitmap
    }

    // Scale dexData to fit the expected size
    private fun scaleDexDataToImage(dexData: ByteArray, expectedSize: Int): ByteArray {
        val scaledData = ByteArray(expectedSize)

        // If dexData is smaller than expected, repeat its values
        if (dexData.size < expectedSize) {
            var i = 0
            while (i < expectedSize) {
                scaledData[i] = dexData[i % dexData.size]
                i++
            }
        } else {
            // If dexData is larger than expected, trim it
            System.arraycopy(dexData, 0, scaledData, 0, expectedSize)
        }

        return scaledData
    }

    // Convert Bitmap to ByteArray
    private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        return outputStream.toByteArray()
    }
}
