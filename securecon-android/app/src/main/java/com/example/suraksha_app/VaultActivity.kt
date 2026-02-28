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
        // 1. MUST BE FIRST: Block Screenshots and Screen Recording
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vault)

        findViewById<ImageButton>(R.id.btn_vault_back).setOnClickListener {
            finish() 
        }

        findViewById<Button>(R.id.btn_select_file).setOnClickListener {
            val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Suraksha")
            val files = folder.listFiles { f -> f.extension == "sec" } ?: arrayOf()
            
            if (files.isEmpty()) {
                Toast.makeText(this, "No secure files found", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val names = files.map { it.name }.toTypedArray()
            AlertDialog.Builder(this).setTitle("Open File").setItems(names) { _, i ->
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

            val header = ByteArray(4)
            bis.read(header)
            val keyLength = ((header[0].toInt() and 0xFF) shl 24) or 
                           ((header[1].toInt() and 0xFF) shl 16) or 
                           ((header[2].toInt() and 0xFF) shl 8) or 
                           (header[3].toInt() and 0xFF)

            val encryptedAesKey = ByteArray(keyLength)
            bis.read(encryptedAesKey)
            val encryptedPhoto = bis.readBytes()

            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val privateKey = ks.getKey("suraksha_id", null) as java.security.PrivateKey
            val rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            rsaCipher.init(Cipher.DECRYPT_MODE, privateKey)
            val aesKeyBytes = rsaCipher.doFinal(encryptedAesKey)

            val aesCipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            aesCipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKeyBytes, "AES"))
            val photoBytes = aesCipher.doFinal(encryptedPhoto)

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
            // Create a dedicated 'shared' folder in the app's internal cache
            val cachePath = File(cacheDir, "shared")
            cachePath.mkdirs()
            val file = File(cachePath, "decrypted_share.png")
            
            // Save the bitmap to the cache so the FileProvider can find it
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            // Create the Secure URI via FileProvider
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // Give the receiver app temporary access
            }
            startActivity(Intent.createChooser(intent, "Share Decrypted Photo"))
        } catch (e: Exception) {
            Toast.makeText(this, "Share Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}