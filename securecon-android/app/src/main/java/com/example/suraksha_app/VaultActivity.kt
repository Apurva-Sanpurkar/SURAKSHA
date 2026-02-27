package com.example.suraksha_app

import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class VaultActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vault)

        findViewById<Button>(R.id.btn_select_file).setOnClickListener {
            // MUST match MainActivity folder
            val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Suraksha")
            val files = folder.listFiles { f -> f.extension == "sec" } ?: arrayOf()
            
            if (files.isEmpty()) {
                Toast.makeText(this, "No .sec files found in Downloads/Suraksha", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val names = files.map { it.name }.toTypedArray()
            AlertDialog.Builder(this).setTitle("Open File").setItems(names) { _, i ->
                decryptFile(files[i])
            }.show()
        }
    }

    private fun decryptFile(file: File) {
        try {
            val bytes = file.readBytes()
            val bis = java.io.ByteArrayInputStream(bytes)

            // 1. Read 4-byte header
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

            // 3. Decrypt AES Key with RSA Private Key
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val privateKey = ks.getKey("suraksha_id", null) as java.security.PrivateKey
            val rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            rsaCipher.init(Cipher.DECRYPT_MODE, privateKey)
            val aesKeyBytes = rsaCipher.doFinal(encryptedAesKey)

            // 4. Decrypt Photo with AES
            val aesCipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            aesCipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKeyBytes, "AES"))
            val photoBytes = aesCipher.doFinal(encryptedPhoto)

            val bitmap = BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size)
            findViewById<ImageView>(R.id.decryptedImageView).setImageBitmap(bitmap)
            
        } catch (e: Exception) {
            Toast.makeText(this, "Decryption Failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}