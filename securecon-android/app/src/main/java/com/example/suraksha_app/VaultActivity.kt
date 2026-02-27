package com.example.suraksha_app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_vault)

        imageView = findViewById(R.id.decryptedImageView)
        shareContainer = findViewById(R.id.share_container)

        findViewById<Button>(R.id.btn_select_file).setOnClickListener {
            scanAndShowFiles() // Use our custom scanner instead of the system picker
        }

        findViewById<Button>(R.id.btn_share).setOnClickListener {
            shareSecurely()
        }
    }

    private fun scanAndShowFiles() {
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Suraksha")
        
        if (!folder.exists() || folder.listFiles().isNullOrEmpty()) {
            Toast.makeText(this, "Vault is empty! Take a photo first.", Toast.LENGTH_LONG).show()
            return
        }

        // Get list of .sec files
        val files = folder.listFiles { file -> file.extension == "sec" }
        val fileNames = files?.map { it.name }?.toTypedArray() ?: arrayOf()

        // Show a professional list dialog
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select a Secure File")
        builder.setItems(fileNames) { _, which ->
            val selectedFile = files?.get(which)
            if (selectedFile != null) {
                decryptAndDisplay(selectedFile)
            }
        }
        builder.show()
    }

    private fun decryptAndDisplay(file: File) {
        try {
            val masterKey = MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            val encryptedFile = EncryptedFile.Builder(this, file, masterKey, 
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()

            encryptedFile.openFileInput().use { input ->
                val bytes = input.readBytes()
                currentBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                
                imageView.setImageBitmap(currentBitmap)
                shareContainer.visibility = View.VISIBLE
                Toast.makeText(this, "File Decrypted Successfully", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Decryption Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareSecurely() {
        val bitmap = currentBitmap ?: return
        try {
            val cachePath = File(cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "temp_share.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

            val contentUri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share via..."))
        } catch (e: Exception) {
            Toast.makeText(this, "Share failed", Toast.LENGTH_SHORT).show()
        }
    }
}