package com.example.suraksha_app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    
    // UI Elements
    private lateinit var dashboard: LinearLayout
    private lateinit var cameraContainer: View
    private lateinit var viewFinder: PreviewView
    private lateinit var captureBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Security: Prevent screenshots and screen recording
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_main)

        // Initialize UI Components
        dashboard = findViewById(R.id.dashboard_ui)
        cameraContainer = findViewById(R.id.camera_container)
        viewFinder = findViewById(R.id.viewFinder)
        captureBtn = findViewById(R.id.capture_button)

        // Professional Card-based Navigation
        findViewById<CardView>(R.id.card_camera).setOnClickListener {
            switchToCamera()
        }

        findViewById<CardView>(R.id.card_vault).setOnClickListener {
            val intent = Intent(this, VaultActivity::class.java)
            startActivity(intent)
        }

        // Camera Navigation (Back Button)
        findViewById<ImageButton>(R.id.btn_back_to_dash).setOnClickListener {
            closeCamera()
        }

        captureBtn.setOnClickListener { 
            takeSecurePhoto() 
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun switchToCamera() {
        // Toggle UI visibility before starting camera to prevent black screen
        dashboard.visibility = View.GONE
        cameraContainer.visibility = View.VISIBLE
        
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun closeCamera() {
        // Unbind camera hardware to free up resources
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
            
            cameraContainer.visibility = View.GONE
            dashboard.visibility = View.VISIBLE
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Toast.makeText(this, "Camera Error: ${exc.message}", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takeSecurePhoto() {
        val imageCapture = imageCapture ?: return
        Toast.makeText(this, "Encrypting...", Toast.LENGTH_SHORT).show()

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    saveEncrypted(image)
                    image.close()
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(baseContext, "Capture failed", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun saveEncrypted(image: ImageProxy) {
        try {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            // Better Filename: SEC_YYYYMMDD_HHMMSS.sec
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "SEC_$timeStamp.sec"
            
            // Save to Public Downloads/Suraksha for easy retrieval
            val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Suraksha")
            if (!folder.exists()) folder.mkdirs()
            val file = File(folder, fileName)

            // AES-256 GCM Encryption via Jetpack Security
            val masterKey = MasterKey.Builder(this)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val encryptedFile = EncryptedFile.Builder(
                this, file, masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()

            encryptedFile.openFileOutput().use { it.write(bytes) }

            runOnUiThread {
                Toast.makeText(this, "Saved: $fileName", Toast.LENGTH_LONG).show()
                closeCamera() // Auto-return to dashboard after capture
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

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.CAMERA)
        } else {
            arrayOf(
                Manifest.permission.CAMERA, 
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }
}