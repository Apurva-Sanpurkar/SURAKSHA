package com.example.suraksha_app

import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    private var imageCapture: ImageCapture? = null
    private val cryptoManager = CryptoManager() // Using the base code we wrote earlier

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. SECURITY: Disable Screenshots and Screen Recording
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContentView(R.layout.activity_main)

        // 2. UI: Find the ViewFinder and Button from your XML Layout
        val viewFinder = findViewById<PreviewView>(R.id.viewFinder)
        val captureButton = findViewById<Button>(R.id.capture_button)

        // 3. START: Initialize the Camera
        startCamera(viewFinder)

        // 4. ACTION: When button is clicked, take a secure photo
        captureButton.setOnClickListener { takeSecurePhoto() }
    }

    private fun startCamera(viewFinder: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            // Set up the preview stream
            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            // Select back camera as default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Toast.makeText(this, "Camera failed to start", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takeSecurePhoto() {
        val imageCapture = imageCapture ?: return

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    // Convert camera image to bytes
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    
                    // Create a .sec file in the app's private storage
                    val file = File(filesDir, "SEC_${System.currentTimeMillis()}.sec")
                    val outputStream = FileOutputStream(file)
                    
                    // ENCRYPT the bytes before saving
                    cryptoManager.encrypt(bytes, outputStream)
                    
                    outputStream.close()
                    image.close()

                    Toast.makeText(baseContext, "Encrypted: ${file.name}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}