package com.makay.cleaner.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * Tam dosya içerik özeti (SHA-256).
 *
 * Yinelenen dosya tespitinde "aday" aşaması hızlı/kısmi hash ile yapılabilir;
 * ancak bir dosyanın silinmesine karar vermeden önce içerik **tam** doğrulanmalıdır.
 * Aksi halde aynı boyutlu ve aynı başlığa sahip iki farklı dosya "yinelenen"
 * sayılıp kullanıcı verisi kalıcı olarak silinebilir.
 */
object FileHashing {

    /** Disk dosyası için tam SHA-256 (hex). Hata durumunda null. */
    fun sha256File(file: File): String? = try {
        if (!file.isFile) {
            null
        } else {
            FileInputStream(file).use { input -> sha256Hex(input) }
        }
    } catch (_: Exception) {
        null
    }

    /** content:// URI için tam SHA-256 (hex). Hata durumunda null. */
    fun sha256Uri(context: Context, uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { input -> sha256Hex(input) }
    } catch (_: Exception) {
        null
    }

    /**
     * Dosya yolu varsa ondan, yoksa URI'den tam hash üretir.
     * @param path mutlak dosya yolu (content: değilse boş olabilir)
     */
    fun sha256Any(context: Context, path: String, uri: Uri? = null): String? {
        if (path.isNotBlank() && !path.startsWith("content:")) {
            val f = File(path)
            if (f.isFile) return sha256File(f)
        }
        return uri?.let { sha256Uri(context, it) }
    }

    private fun sha256Hex(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n <= 0) break
            digest.update(buffer, 0, n)
        }
        return digest.digest().joinToString("") { b -> "%02x".format(b) }
    }
}
