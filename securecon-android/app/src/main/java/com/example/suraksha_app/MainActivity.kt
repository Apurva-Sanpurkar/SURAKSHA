package com.example.suraksha_app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // PHASE 1: SCREENSHOT PROTECTION
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        
        setContentView(R.layout.activity_main)

        // Request Permissions at Runtime
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        // Setup capture button
        findViewById<Button>(R.id.capture_button).setOnClickListener {
            takeSecurePhoto()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(findViewById<PreviewView>(R.id.viewFinder).surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Toast.makeText(this, "Camera initialization failed", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takeSecurePhoto() {
        val imageCapture = imageCapture ?: return

        Toast.makeText(this, "Encrypting Image...", Toast.LENGTH_SHORT).show()

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    saveEncrypted(image)
                    image.close()
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(baseContext, "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun saveEncrypted(image: ImageProxy) {
        try {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            // VISIBLE PATH: Uses External Scoped Storage
            val folder = getExternalFilesDir(null) 
            val fileName = "SURAKSHA_${System.currentTimeMillis()}.sec"
            val file = File(folder, fileName)

            // Hardware-backed AES-256 Key
            val masterKey = MasterKey.Builder(this)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val encryptedFile = EncryptedFile.Builder(
                this, file, masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()

            encryptedFile.openFileOutput().use { output ->
                output.write(bytes)
            }

            runOnUiThread {
                Toast.makeText(this, "SUCCESS: Saved to Android/data/.../files", Toast.LENGTH_LONG).show()
            }

        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this, "Encryption Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    // ONLY ONE COMPANION OBJECT ALLOWED
    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}