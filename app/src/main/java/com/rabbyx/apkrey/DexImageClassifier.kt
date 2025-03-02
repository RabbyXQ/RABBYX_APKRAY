package com.rabbyx.apkrey

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

object DexImageClassifier {

    // Load the TFLite model
    private lateinit var interpreter: Interpreter

    // Load the model once
    fun initialize(context: Context) {
        // Load the model from the assets folder (adjust this according to your file path)
        val model = FileUtil.loadMappedFile(context, "dex_image_classifier.tflite")
        interpreter = Interpreter(model)
    }

    // Function to classify a list of DEX images
    fun classifyDexImages(dexImages: List<ByteArray>): Int {
        // Classify each image and track if any image is classified as malware
        var isMalwareDetected = false

        for (imageBytes in dexImages) {
            val bitmap = ByteArrayToBitmap(imageBytes)
            val result = classifyImage(bitmap)

            // If any image is detected as malware, flag it
            if (result == 1) {
                isMalwareDetected = true
                break
            }
        }

        // If any malware is detected, return 1 (unsafe), otherwise 0 (safe)
        return if (isMalwareDetected) 1 else 0
    }

    // Convert ByteArray to Bitmap (for classifier input)
    private fun ByteArrayToBitmap(byteArray: ByteArray): Bitmap {
        return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
    }

    // Function to classify a single image (convert it to input format for TensorFlow Lite)
    private fun classifyImage(bitmap: Bitmap): Int {
        // Prepare the image for classification
        val input = preprocessImage(bitmap)

        // Prepare the output buffer (assuming a binary classification model)
        val output = Array(1) { FloatArray(1) }

        // Run the model
        interpreter.run(input, output)

        // Get the result (1 if malware, 0 if benign)
        return if (output[0][0] > 0.5) 1 else 0
    }

    // Preprocess the image (resize, normalize, etc.)
    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        // Resize image to 256x256 (or another size depending on your model)
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 256, 256, true)

        // Create a ByteBuffer to hold the image data (assuming 3 channels: RGB)
        val byteBuffer = ByteBuffer.allocateDirect(4 * 256 * 256 * 3) // 256x256 RGB
        byteBuffer.order(ByteOrder.nativeOrder())

        // Convert Bitmap to pixel data and fill the ByteBuffer
        for (y in 0 until 256) {
            for (x in 0 until 256) {
                val pixel = resizedBitmap.getPixel(x, y)
                byteBuffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f) // Red channel
                byteBuffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)  // Green channel
                byteBuffer.putFloat((pixel and 0xFF) / 255.0f)          // Blue channel
            }
        }

        return byteBuffer
    }
}
