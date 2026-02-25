package com.example.suraksha_app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class CryptoManager {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    // Generates a unique key inside the Android hardware (KeyStore)
    private fun createKey(): SecretKey {
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("suraksha_key", 
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }

    fun encrypt(bytes: ByteArray, outputStream: OutputStream) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, createKey())
        outputStream.write(cipher.iv.size)
        outputStream.write(cipher.iv)
        outputStream.write(cipher.doFinal(bytes))
    }
}