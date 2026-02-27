package com.example.suraksha_app

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.security.*
import java.security.spec.X509EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

class MainActivity : AppCompatActivity() {

    private var imageCapture: ImageCapture? = null
    private var latestBitmap: Bitmap? = null
    private var latestEncryptedFile: File? = null

    private lateinit var dashboard: View
    private lateinit var cameraContainer: View
    private lateinit var postCapturePreview: View
    private lateinit var capturedImageView: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_main)

        dashboard = findViewById(R.id.dashboard_ui)
        cameraContainer = findViewById(R.id.camera_container)
        postCapturePreview = findViewById(R.id.post_capture_preview)
        capturedImageView = findViewById(R.id.captured_image_view)

        ensureHardwareIdentity()

        findViewById<CardView>(R.id.card_camera).setOnClickListener {
            if (allPermissionsGranted()) {
                openCameraUI()
            } else {
                ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, 10)
            }
        }

        findViewById<CardView>(R.id.card_vault).setOnClickListener {
            startActivity(Intent(this, VaultActivity::class.java))
        }

        findViewById<CardView>(R.id.btn_invite_contact).setOnClickListener {
            shareInviteLink()
        }

        findViewById<Button>(R.id.capture_button).setOnClickListener { takeSecurePhoto() }
        
        findViewById<Button>(R.id.btn_share_now).setOnClickListener { shareEncryptedFile() }
        
        findViewById<Button>(R.id.btn_discard).setOnClickListener {
            postCapturePreview.visibility = View.GONE
            cameraContainer.visibility = View.VISIBLE
        }

        findViewById<ImageButton>(R.id.btn_back_to_dash).setOnClickListener {
            cameraContainer.visibility = View.GONE
            dashboard.visibility = View.VISIBLE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) { checkClipboardForHandshake() }
    }

    private fun checkClipboardForHandshake() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        // FIX: Using primaryClip instead of primaryData
        val clipData = clipboard.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val text = clipData.getItemAt(0).text?.toString() ?: ""
            if (text.contains("suraksha://add-key")) {
                val uri = Uri.parse(text.substring(text.indexOf("suraksha://")))
                showAddContactDialog(uri)
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }

    private fun showAddContactDialog(uri: Uri) {
        val name = uri.getQueryParameter("name") ?: "New Contact"
        val key = uri.getQueryParameter("key") ?: ""

        AlertDialog.Builder(this)
            .setTitle("Identity Sync Detected")
            .setMessage("Add $name as a trusted contact?")
            .setPositiveButton("Add") { _, _ ->
                getSharedPreferences("Contacts", MODE_PRIVATE).edit()
                    .putString("saved_key", key).apply()
                Toast.makeText(this, "$name added!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Ignore", null).show()
    }

    private fun ensureHardwareIdentity() {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias("suraksha_id")) {
            val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            kpg.initialize(KeyGenParameterSpec.Builder("suraksha_id", 
                KeyProperties.PURPOSE_DECRYPT or KeyProperties.PURPOSE_ENCRYPT)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .build())
            kpg.generateKeyPair()
        }
    }

    private fun shareInviteLink() {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val publicKey = ks.getCertificate("suraksha_id").publicKey
        val keyBase64 = Base64.encodeToString(publicKey.encoded, Base64.URL_SAFE or Base64.NO_WRAP)
        val message = "Connect with me on Suraksha! Copy this message and open the app:\nsuraksha://add-key?name=Adishree&key=$keyBase64"
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        startActivity(Intent.createChooser(intent, "Share Invite"))
    }

    private fun openCameraUI() {
        dashboard.visibility = View.GONE
        cameraContainer.visibility = View.VISIBLE
        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(findViewById<PreviewView>(R.id.viewFinder).surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            } catch (exc: Exception) { }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takeSecurePhoto() {
        imageCapture?.takePicture(ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                image.close()
                saveAndShowPreview(bytes)
            }
            override fun onError(exc: ImageCaptureException) {}
        })
    }

    private fun saveAndShowPreview(data: ByteArray) {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Suraksha")
            if (!folder.exists()) folder.mkdirs()
            val file = File(folder, "SEC_$timeStamp.sec")
            
            val savedKey = getSharedPreferences("Contacts", MODE_PRIVATE).getString("saved_key", null)
            val dataToSave = if (savedKey != null) encryptWithPublicKey(data, savedKey) else data

            FileOutputStream(file).use { it.write(dataToSave) }
            latestEncryptedFile = file
            latestBitmap = BitmapFactory.decodeByteArray(data, 0, data.size)

            runOnUiThread {
                capturedImageView.setImageBitmap(latestBitmap)
                cameraContainer.visibility = View.GONE
                postCapturePreview.visibility = View.VISIBLE
            }
        } catch (e: Exception) { }
    }

    private fun encryptWithPublicKey(data: ByteArray, keyBase64: String): ByteArray {
        val keyBytes = Base64.decode(keyBase64, Base64.URL_SAFE)
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return cipher.doFinal(data)
    }

    private fun shareEncryptedFile() {
        val file = latestEncryptedFile ?: return
        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Send Secure .sec File"))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    // FIX: Added missing REQUIRED_PERMISSIONS and companion object
    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}