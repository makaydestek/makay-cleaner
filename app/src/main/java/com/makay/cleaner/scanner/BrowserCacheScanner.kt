package com.makay.cleaner.scanner

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import com.makay.cleaner.data.RecycleBinRepository
import com.makay.cleaner.util.FilePurger
import com.makay.cleaner.util.SystemFileGuard
import java.io.File

data class BrowserAppInfo(
    val packageName: String,
    val label: String
)

data class BrowserLeftover(
    val file: File,
    val sizeBytes: Long,
    val reason: String
)

/**
 * Gerçekçi tarayıcı temizliği (root yok):
 * - Bilinen tarayıcı paketleri → sistem depolama ayarı
 * - Downloads içindeki yarım/indirilmemiş tarayıcı artıkları (.crdownload, .part, .tmp)
 * Android/data tarayıcı cache'ine Android 13+'da erişilmez; vaat edilmez.
 */
object BrowserCacheScanner {

    private val BROWSER_PACKAGES = listOf(
        "com.android.chrome" to "Chrome",
        "com.chrome.beta" to "Chrome Beta",
        "org.mozilla.firefox" to "Firefox",
        "com.microsoft.emmx" to "Edge",
        "com.opera.browser" to "Opera",
        "com.brave.browser" to "Brave",
        "com.sec.android.app.sbrowser" to "Samsung Internet",
        "com.mi.globalbrowser" to "Mi Browser",
        "com.android.browser" to "Tarayıcı"
    )

    private val LEFTOVER_SUFFIXES = listOf(
        ".crdownload", ".part", ".tmp", ".download", ".opdownload", ".partial"
    )

    fun installedBrowsers(context: Context): List<BrowserAppInfo> {
        val pm = context.packageManager
        return BROWSER_PACKAGES.mapNotNull { (pkg, fallback) ->
            try {
                pm.getPackageInfo(pkg, 0)
                val label = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (_: Exception) {
                    fallback
                }
                BrowserAppInfo(pkg, label)
            } catch (_: Exception) {
                null
            }
        }
    }

    fun scanLeftovers(context: Context): List<BrowserLeftover> {
        val roots = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            File(Environment.getExternalStorageDirectory(), "Download"),
            File(Environment.getExternalStorageDirectory(), "Downloads")
        ).distinctBy { it.absolutePath }

        val out = mutableListOf<BrowserLeftover>()
        roots.forEach { root ->
            if (!root.exists() || !root.isDirectory) return@forEach
            if (SystemFileGuard.isProtectedPath(root.absolutePath)) return@forEach
            root.listFiles()?.forEach { f ->
                if (!f.isFile) return@forEach
                if (SystemFileGuard.isProtectedPath(f.absolutePath)) return@forEach
                val name = f.name.lowercase()
                val match = LEFTOVER_SUFFIXES.any { name.endsWith(it) }
                if (match && FilePurger.canRemove(context, f)) {
                    out += BrowserLeftover(f, f.length(), "Yarım / geçici indirme")
                }
            }
        }
        return out.sortedByDescending { it.sizeBytes }
    }

    fun cleanLeftovers(context: Context, files: List<File>): Pair<Int, Long> {
        val trash = RecycleBinRepository(context)
        var count = 0
        var saved = 0L
        files.forEach { f ->
            val size = if (f.isFile) f.length() else 0L
            if (FilePurger.remove(context, f, trash = trash, category = "Tarayıcı artığı")) {
                count++
                saved += size
            }
        }
        return count to saved
    }

    fun openBrowserStorage(context: Context, packageName: String) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: Exception) {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
