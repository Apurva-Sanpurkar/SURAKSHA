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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.FileOutputStream

class VaultActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var shareBtn: Button
    private var currentBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep screen secure even in the viewer
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_vault)

        imageView = findViewById(R.id.decryptedImageView)
        shareBtn = findViewById(R.id.btn_share)

        findViewById<Button>(R.id.btn_select_file).setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(intent, 100)
        }

        shareBtn.setOnClickListener {
            shareImage()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            data?.data?.let { uri -> decryptAndDisplay(uri) }
        }
    }

    private fun decryptAndDisplay(uri: Uri) {
        try {
            // Locate the file in the Suraksha folder
            val fileName = uri.path?.split("/")?.last()?.replace("primary:", "") ?: return
            val file = File(android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS), "Suraksha/$fileName")

            val masterKey = MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            val encryptedFile = EncryptedFile.Builder(this, file, masterKey, 
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()

            encryptedFile.openFileInput().use { input ->
                val bytes = input.readBytes()
                currentBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                imageView.setImageBitmap(currentBitmap)
                shareBtn.visibility = View.VISIBLE
                Toast.makeText(this, "Decryption Successful", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareImage() {
        val bitmap = currentBitmap ?: return
        try {
            // Create a temporary file for sharing
            val cachePath = File(cacheDir, "shared_images")
            cachePath.mkdirs()
            val stream = FileOutputStream("$cachePath/temp_share.png")
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()

            val newFile = File(cachePath, "temp_share.png")
            val contentUri = FileProvider.getUriForFile(this, "${packageName}.provider", newFile)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Send Image via..."))
        } catch (e: Exception) {
            Toast.makeText(this, "Sharing failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}