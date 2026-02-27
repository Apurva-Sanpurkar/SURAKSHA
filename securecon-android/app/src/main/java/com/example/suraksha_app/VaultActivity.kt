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
            val folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val files = folder.listFiles { f -> f.extension == "sec" } ?: arrayOf()
            
            val names = files.map { it.name }.toTypedArray()
            AlertDialog.Builder(this).setTitle("Open File").setItems(names) { _, i ->
                decryptFile(files[i])
            }.show()
        }
    }

    private fun decryptFile(file: File) {
        try {
            val fis = FileInputStream(file)
            
            // 1. Read Key Size and Encrypted AES Key
            val keySize = fis.read()
            val encryptedAesKey = ByteArray(keySize)
            fis.read(encryptedAesKey)

            // 2. Read Encrypted Photo
            val encryptedPhoto = fis.readBytes()

            // 3. Decrypt AES Key using RSA Private Key
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val privateKey = ks.getKey("suraksha_id", null) as java.security.PrivateKey
            
            val rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            rsaCipher.init(Cipher.DECRYPT_MODE, privateKey)
            val aesKeyBytes = rsaCipher.doFinal(encryptedAesKey)
            val aesKey = SecretKeySpec(aesKeyBytes, "AES")

            // 4. Decrypt Photo using AES Key
            val aesCipher = Cipher.getInstance("AES")
            aesCipher.init(Cipher.DECRYPT_MODE, aesKey)
            val photoBytes = aesCipher.doFinal(encryptedPhoto)

            val bitmap = BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size)
            findViewById<ImageView>(R.id.decryptedImageView).setImageBitmap(bitmap)
            
        } catch (e: Exception) {
            Toast.makeText(this, "Decryption Failed", Toast.LENGTH_SHORT).show()
        }
    }
}