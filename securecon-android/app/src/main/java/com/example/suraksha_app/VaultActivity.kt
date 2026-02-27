package com.example.suraksha_app

import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.security.KeyStore
import java.security.PrivateKey
import javax.crypto.Cipher

class VaultActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vault)

        findViewById<Button>(R.id.btn_select_file).setOnClickListener {
            val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Suraksha")
            val files = folder.listFiles { f -> f.extension == "sec" } ?: arrayOf()
            
            if (files.isEmpty()) {
                Toast.makeText(this, "No secure files found.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val names = files.map { it.name }.toTypedArray()
            AlertDialog.Builder(this).setTitle("Open Secure Photo").setItems(names) { _, i ->
                decryptFile(files[i])
            }.show()
        }
    }

    private fun decryptFile(file: File) {
        try {
            val encryptedBytes = file.readBytes()
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val privateKey = ks.getKey("suraksha_id", null) as PrivateKey

            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.DECRYPT_MODE, privateKey)
            
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            val bitmap = BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size)
            findViewById<ImageView>(R.id.decryptedImageView).setImageBitmap(bitmap)
            Toast.makeText(this, "Decryption Successful!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: You do not have the key for this file.", Toast.LENGTH_LONG).show()
        }
    }
}