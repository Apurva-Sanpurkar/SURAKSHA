package com.example.suraksha_app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class VaultActivity : AppCompatActivity() {

    private var decryptedBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. SECURITY: Block Screenshots and Screen Recording
        // This must be called BEFORE super.onCreate and setContentView
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vault)

        // Back button to return to Dashboard
        findViewById<ImageButton>(R.id.btn_vault_back).setOnClickListener {
            finish() 
        }

        findViewById<Button>(R.id.btn_select_file).setOnClickListener {
            // Path must match the subfolder in MainActivity
            val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Suraksha")
            val files = folder.listFiles { f -> f.extension == "sec" } ?: arrayOf()
            
            if (files.isEmpty()) {
                Toast.makeText(this, "No .sec files found in Downloads/Suraksha", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val names = files.map { it.name }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("Select Encrypted File")
                .setItems(names) { _, i ->
                    decryptFile(files[i])
                }.show()
        }

        findViewById<Button>(R.id.btn_share_decrypted).setOnClickListener {
            shareDecryptedPhoto()
        }
    }

    private fun decryptFile(file: File) {
        try {
            val bytes = file.readBytes()
            val bis = java.io.ByteArrayInputStream(bytes)

            // 1. Read 4-byte header for key length
            val header = ByteArray(4)
            bis.read(header)
            val keyLength = ((header[0].toInt() and 0xFF) shl 24) or 
                           ((header[1].toInt() and 0xFF) shl 16) or 
                           ((header[2].toInt() and 0xFF) shl 8) or 
                           (header[3].toInt() and 0xFF)

            // 2. Extract Keys and Photo
            val encryptedAesKey = ByteArray(keyLength)
            bis.read(encryptedAesKey)
            val encryptedPhoto = bis.readBytes()

            // 3. Decrypt AES Key with RSA Private Key from KeyStore
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val privateKey = ks.getKey("suraksha_id", null) as java.security.PrivateKey
            val rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            rsaCipher.init(Cipher.DECRYPT_MODE, privateKey)
            val aesKeyBytes = rsaCipher.doFinal(encryptedAesKey)

            // 4. Decrypt Photo with AES
            val aesCipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            aesCipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKeyBytes, "AES"))
            val photoBytes = aesCipher.doFinal(encryptedPhoto)

            // 5. Update UI
            decryptedBitmap = BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size)
            findViewById<ImageView>(R.id.decryptedImageView).setImageBitmap(decryptedBitmap)
            
            // Show share button only after successful decryption
            findViewById<Button>(R.id.btn_share_decrypted).visibility = View.VISIBLE
            
        } catch (e: Exception) {
            Toast.makeText(this, "Decryption Failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareDecryptedPhoto() {
        val bitmap = decryptedBitmap ?: return
        try {
            // Create internal 'shared' cache folder
            val cachePath = File(cacheDir, "shared")
            if (!cachePath.exists()) cachePath.mkdirs()
            
            val file = File(cachePath, "decrypted_share.png")
            
            // Save bitmap to cache for FileProvider access
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            // Secure URI creation
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) 
            }
            startActivity(Intent.createChooser(intent, "Share Decrypted Photo"))
        } catch (e: Exception) {
            Toast.makeText(this, "Share Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}