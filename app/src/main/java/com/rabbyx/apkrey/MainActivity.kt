package com.rabbyx.apkrey

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import org.tensorflow.lite.Interpreter
import java.io.*
import com.rabbyx.apkrey.R
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class MainActivity : AppCompatActivity() {

    private lateinit var interpreter: Interpreter
    private lateinit var resultText: TextView
    private lateinit var loadingBar: ProgressBar
    private lateinit var scanButton: Button

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        resultText = findViewById(R.id.resultText)
        loadingBar = findViewById(R.id.loadingBar)
        scanButton = findViewById(R.id.scanButton)

        try {
            interpreter = Interpreter(loadModelFile())
            Log.d("MainActivity", "TensorFlow Lite model loaded successfully")
        } catch (e: IOException) {
            Log.e("MainActivity", "Failed to load model", e)
            return
        }

        scanButton.setOnClickListener {
            startScan()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun startScan() {
        // Show the loading bar and hide the scan button
        loadingBar.visibility = ProgressBar.VISIBLE
        scanButton.isEnabled = false
        resultText.text = "Scanning..."

        // Start scanning installed apps in the background
        Thread {
            val installedApps = getInstalledApps()

            val totalApps = installedApps.size
            var scannedApps = 0
            var foundMalicious = false

            for (app in installedApps) {
                val dexImage = DexImageExtractor.extractDexImage(app)
                if (dexImage != null && dexImage.isNotEmpty()) {
                    val isMalicious = classifyDexImage(dexImage)
                    scannedApps++

                    // Update progress bar
                    val progress = (scannedApps * 100) / totalApps
                    runOnUiThread {
                        loadingBar.progress = progress
                    }

                    if (isMalicious) {
                        foundMalicious = true
                        runOnUiThread {
                            resultText.text = "Malicious App Found: ${app.packageName}"
                            resultText.setTextColor(getColor(android.R.color.holo_red_dark))
                        }
                        break
                    }
                }
            }

            // Finish scanning
            if (!foundMalicious) {
                runOnUiThread {
                    resultText.text = "Scan Complete - No Malicious Apps Found"
                    resultText.setTextColor(getColor(android.R.color.white))
                }
            }

            // Hide loading bar and re-enable the scan button
            runOnUiThread {
                loadingBar.visibility = ProgressBar.GONE
                scanButton.isEnabled = true
            }
        }.start()
    }

    private fun getInstalledApps(): List<ApplicationInfo> {
        val packageManager: PackageManager = packageManager
        return packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
    }

    private fun classifyDexImage(dexImage: ByteArray): Boolean {
        // Ensure correct ByteBuffer size
        val inputBuffer = ByteBuffer.allocateDirect(196608).order(ByteOrder.nativeOrder())
        inputBuffer.put(dexImage)
        inputBuffer.rewind()

        // Output buffer (1 float, assuming binary classification)
        val outputBuffer = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())

        try {
            interpreter.run(inputBuffer, outputBuffer)
        } catch (e: Exception) {
            Log.e("MainActivity", "Model inference failed", e)
            return false
        }

        outputBuffer.rewind()
        val result = outputBuffer.float  // Assuming single output float (0 = benign, 1 = malware)
        return result > 0.5
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = assets.openFd("dex_image_classifier.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }
}
