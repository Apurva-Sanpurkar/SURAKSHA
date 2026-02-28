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
import javax.crypto.KeyGenerator
import javax.crypto.spec.SecretKeySpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

class MainActivity : AppCompatActivity() {

    private var imageCapture: ImageCapture? = null
    private var latestEncryptedFile: File? = null

    private lateinit var dashboard: View
    private lateinit var cameraContainer: View
    private lateinit var postCapturePreview: View
    private lateinit var capturedImageView: ImageView
    private lateinit var statusIndicator: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    setContentView(R.layout.activity_main)

    dashboard = findViewById(R.id.dashboard_ui)
    cameraContainer = findViewById(R.id.camera_container)
    postCapturePreview = findViewById(R.id.post_capture_preview)
    capturedImageView = findViewById(R.id.captured_image_view)
    statusIndicator = findViewById(R.id.status_indicator)

    ensureHardwareIdentity()
    updateStatusUI()

    // --- DASHBOARD NAVIGATION ---
    findViewById<CardView>(R.id.card_camera).setOnClickListener {
        if (allPermissionsGranted()) openCameraUI()
        else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 10)
    }

    findViewById<CardView>(R.id.card_vault).setOnClickListener {
        startActivity(Intent(this, VaultActivity::class.java))
    }

    findViewById<CardView>(R.id.btn_invite_contact).setOnClickListener { shareInviteLink() }

    // --- CAMERA ACTIONS ---
    findViewById<Button>(R.id.capture_button).setOnClickListener { takeSecurePhoto() }
    
    // NEW: BACK BUTTON LOGIC
    findViewById<ImageButton>(R.id.btn_back_to_dash).setOnClickListener {
        cameraContainer.visibility = View.GONE
        dashboard.visibility = View.VISIBLE
        
        // Safety: Unbind camera to save resources
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll() 
        }, ContextCompat.getMainExecutor(this))
    }

    // --- PREVIEW ACTIONS ---
    findViewById<Button>(R.id.btn_share_now).setOnClickListener { shareEncryptedFile() }
    findViewById<Button>(R.id.btn_discard).setOnClickListener {
        postCapturePreview.visibility = View.GONE
        cameraContainer.visibility = View.VISIBLE
        startCamera()
    }
}

    private fun updateStatusUI() {
        val savedKey = getSharedPreferences("Contacts", MODE_PRIVATE).getString("saved_key", null)
        statusIndicator.text = if (savedKey != null) "SECURELY LINKED" else "LOCAL TEST MODE"
        statusIndicator.setTextColor(if (savedKey != null) Color.GREEN else Color.parseColor("#1A73E8"))
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

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
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

    private fun openCameraUI() {
        dashboard.visibility = View.GONE
        cameraContainer.visibility = View.VISIBLE
        startCamera()
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

    // --- HYBRID ENCRYPTION ENGINE ---
   
private fun saveAndShowPreview(photoBytes: ByteArray) {
    try {
        val aesKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val aesCipher = Cipher.getInstance("AES/ECB/PKCS5Padding").apply { 
            init(Cipher.ENCRYPT_MODE, aesKey) 
        }
        val encryptedPhoto = aesCipher.doFinal(photoBytes)

        val rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        val savedKey = getSharedPreferences("Contacts", MODE_PRIVATE).getString("saved_key", null)
        val publicKey = if (savedKey != null) {
            val keyBytes = Base64.decode(savedKey, Base64.URL_SAFE)
            KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
        } else {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            ks.getCertificate("suraksha_id").publicKey
        }
        rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encryptedAesKey = rsaCipher.doFinal(aesKey.encoded)

        // SAVE LOGIC
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Suraksha")
        if (!folder.exists()) folder.mkdirs()
        val file = File(folder, "SEC_$timeStamp.sec")
        
        FileOutputStream(file).use { fos ->
            val len = encryptedAesKey.size
            fos.write(byteArrayOf((len shr 24).toByte(), (len shr 16).toByte(), (len shr 8).toByte(), len.toByte()))
            fos.write(encryptedAesKey)
            fos.write(encryptedPhoto)
        }
        
        latestEncryptedFile = file
        runOnUiThread {
            capturedImageView.setImageBitmap(BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size))
            cameraContainer.visibility = View.GONE
            postCapturePreview.visibility = View.VISIBLE
        }
    } catch (e: Exception) {
        runOnUiThread { Toast.makeText(this, "Encryption Error: ${e.message}", Toast.LENGTH_LONG).show() }
    }
}
private fun decryptFile(file: File) {
    try {
        val bytes = file.readBytes()
        val inputStream = java.io.ByteArrayInputStream(bytes)

        // 1. Read the 4-byte key length header
        val b1 = inputStream.read()
        val b2 = inputStream.read()
        val b3 = inputStream.read()
        val b4 = inputStream.read()
        val keyLength = (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4

        // 2. Extract Encrypted AES Key
        val encryptedAesKey = ByteArray(keyLength)
        inputStream.read(encryptedAesKey)

        // 3. Extract Encrypted Photo
        val encryptedPhoto = inputStream.readBytes()

        // 4. Decrypt AES Key using RSA Private Key
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val privateKey = ks.getKey("suraksha_id", null) as java.security.PrivateKey
        val rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey)
        val aesKeyBytes = rsaCipher.doFinal(encryptedAesKey)
        val aesKey = SecretKeySpec(aesKeyBytes, "AES")

        // 5. Decrypt Photo using AES
        val aesCipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        aesCipher.init(Cipher.DECRYPT_MODE, aesKey)
        val photoBytes = aesCipher.doFinal(encryptedPhoto)

        val bitmap = BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size)
        findViewById<ImageView>(R.id.decryptedImageView).setImageBitmap(bitmap)
        
    } catch (e: Exception) {
        Toast.makeText(this, "Decryption Failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

    private fun shareEncryptedFile() {
        val file = latestEncryptedFile ?: return
        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share .sec File"))
    }

    private fun shareInviteLink() {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val publicKey = ks.getCertificate("suraksha_id").publicKey
        val keyBase64 = Base64.encodeToString(publicKey.encoded, Base64.URL_SAFE or Base64.NO_WRAP)
        val link = "suraksha://add-key?name=User&key=$keyBase64"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Suraksha Invite:\n$link")
        }
        startActivity(Intent.createChooser(intent, "Share Invite"))
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) checkClipboardForHandshake()
    }

    private fun checkClipboardForHandshake() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboard.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val text = clipData.getItemAt(0).text?.toString() ?: ""
            if (text.contains("suraksha://add-key")) {
                val uri = Uri.parse(text.substring(text.indexOf("suraksha://")))
                AlertDialog.Builder(this).setTitle("Sync Found").setMessage("Add contact?")
                    .setPositiveButton("Add") { _, _ ->
                        val key = uri.getQueryParameter("key")
                        getSharedPreferences("Contacts", MODE_PRIVATE).edit().putString("saved_key", key).apply()
                        updateStatusUI()
                    }.setNegativeButton("No", null).show()
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }
}