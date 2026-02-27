package com.example.suraksha_app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.FileOutputStream

class VaultActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var shareContainer: LinearLayout
    private var currentBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep screen secure even in viewer
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_vault)

        imageView = findViewById(R.id.decryptedImageView)
        shareContainer = findViewById(R.id.share_container)

        findViewById<Button>(R.id.btn_select_file).setOnClickListener {
            // Using a simple GET_CONTENT intent
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(intent, 100)
        }

        findViewById<Button>(R.id.btn_share).setOnClickListener {
            shareSecurely()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            data?.data?.let { uri -> processUri(uri) }
        }
    }

    private fun processUri(uri: Uri) {
        try {
            // Locate file in Downloads/Suraksha
            val fileName = uri.path?.split("/")?.last()?.replace("primary:", "") ?: return
            val file = File(android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS), "Suraksha/$fileName")

            if (!file.exists()) {
                Toast.makeText(this, "File not found at: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                return
            }

            // Decrypt
            val masterKey = MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            val encryptedFile = EncryptedFile.Builder(this, file, masterKey, 
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()

            encryptedFile.openFileInput().use { input ->
                val bytes = input.readBytes()
                currentBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                
                // Show Preview and Share Button
                imageView.setImageBitmap(currentBitmap)
                shareContainer.visibility = View.VISIBLE
                Toast.makeText(this, "Unlocking Secure Media...", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Decryption Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareSecurely() {
        val bitmap = currentBitmap ?: return
        try {
            // Temporary cache file for sharing
            val cachePath = File(cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "temp_decrypted.png")
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()

            val contentUri = FileProvider.getUriForFile(this, "${packageName}.provider", file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Secure Share via..."))
        } catch (e: Exception) {
            Toast.makeText(this, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}