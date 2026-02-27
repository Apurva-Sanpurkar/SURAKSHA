package com.example.suraksha_app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
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
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var dashboard: LinearLayout
    private lateinit var viewFinder: PreviewView
    private lateinit var captureBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_main)

        dashboard = findViewById(R.id.dashboard_ui)
        viewFinder = findViewById(R.id.viewFinder)
        captureBtn = findViewById(R.id.capture_button)

        // Switch to Camera Mode
        findViewById<Button>(R.id.btn_goto_camera).setOnClickListener {
            switchToCamera()
        }

        // Vault placeholder
        findViewById<Button>(R.id.btn_goto_vault).setOnClickListener {
            Toast.makeText(this, "Vault Decryption Coming Next!", Toast.LENGTH_SHORT).show()
        }

        captureBtn.setOnClickListener { takeSecurePhoto() }
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

     
    findViewById<Button>(R.id.btn_goto_vault).setOnClickListener {
    val intent = Intent(this, VaultActivity::class.java)
    startActivity(intent)
}
 
    private fun switchToCamera() {
        dashboard.visibility = View.GONE
        viewFinder.visibility = View.VISIBLE
        captureBtn.visibility = View.VISIBLE
        
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            } catch (exc: Exception) {
                Toast.makeText(this, "Camera failed", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takeSecurePhoto() {
        val imageCapture = imageCapture ?: return
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    saveEncrypted(image)
                    image.close()
                }
                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(baseContext, "Capture error", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun saveEncrypted(image: ImageProxy) {
        try {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            // Human Readable Filename
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "SEC_$timeStamp.sec"
            
            val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Suraksha")
            if (!folder.exists()) folder.mkdirs()
            val file = File(folder, fileName)

            val masterKey = MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            val encryptedFile = EncryptedFile.Builder(this, file, masterKey, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()

            encryptedFile.openFileOutput().use { it.write(bytes) }

            runOnUiThread {
                Toast.makeText(this, "Encrypted: $fileName", Toast.LENGTH_SHORT).show()
                // Return to Dashboard after success
                dashboard.visibility = View.VISIBLE
                viewFinder.visibility = View.GONE
                captureBtn.visibility = View.GONE
            }
        } catch (e: Exception) {
            runOnUiThread { Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.CAMERA)
        } else {
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
}yes 