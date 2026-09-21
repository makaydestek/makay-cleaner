package com.makay.cleaner.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.io.File

/**
 * İşletim sistemi ve sistem uygulamalarına ait yolları / paketleri korur.
 * Tarama sonuçlarından filtreler; silme işlemlerinde engeller.
 */
object SystemFileGuard {

    @Volatile
    private var skippedCount: Int = 0

    fun recordSkipped() {
        skippedCount++
    }

    fun consumeSkippedCount(): Int {
        val n = skippedCount
        skippedCount = 0
        return n
    }

    fun peekSkippedCount(): Int = skippedCount

    private val PROTECTED_PATH_PREFIXES = listOf(
        "/system",
        "/vendor",
        "/product",
        "/odm",
        "/oem",
        "/apex",
        "/data/system",
        "/data/misc",
        "/data/user_de",
        "/data/adb",
        "/data/dalvik-cache",
        "/cache/recovery",
        "/efs",
        "/proc",
        "/sys",
        "/dev"
    )

    private val PROTECTED_PATH_SEGMENTS = listOf(
        "/Android/obb/com.android.",
        "/Android/data/com.android.",
        "/Android/data/com.google.android.gms",
        "/Android/data/com.google.android.gsf",
        "/Android/data/com.google.android.ext.",
        "/Android/data/com.samsung.android.",
        "/Android/data/com.sec.",
        "/Android/data/com.miui.",
        "/Android/data/com.huawei.",
        "/Android/data/com.coloros.",
        "/Android/data/com.oppo.",
        "/Android/data/com.vivo.",
        "/Android/data/com.oneplus.",
        "/Android/media/com.android.",
        "/.Trash",
        "/lost+found"
    )

    private val PROTECTED_PACKAGE_PREFIXES = listOf(
        "android",
        "com.android.",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.google.android.ext.",
        "com.google.android.webview",
        "com.google.android.permissioncontroller",
        "com.google.android.packageinstaller",
        "com.google.android.providers.",
        "com.samsung.android.",
        "com.sec.android.",
        "com.miui.",
        "com.xiaomi.misettings",
        "com.huawei.system",
        "com.huawei.hwid",
        "com.coloros.",
        "com.oppo.",
        "com.oneplus.",
        "com.vivo.",
        "com.qualcomm.",
        "com.mediatek."
    )

    /** Mutlak yol sistem / kritik bölgede mi? */
    fun isProtectedPath(path: String?): Boolean {
        if (path.isNullOrBlank()) return true
        if (path.startsWith("content://")) {
            // MediaStore URI — kullanıcı medyası; sistem yolu değil
            return false
        }
        val normalized = path.replace('\\', '/').lowercase()
        if (PROTECTED_PATH_PREFIXES.any { normalized.startsWith(it) }) return true
        if (PROTECTED_PATH_SEGMENTS.any { normalized.contains(it.lowercase()) }) return true
        // /data/data/<system-pkg> veya /data/user/0/<system-pkg>
        val pkgFromData = extractPackageFromDataPath(normalized)
        if (pkgFromData != null && isProtectedPackageName(pkgFromData)) return true
        val pkgFromAndroidData = extractPackageFromAndroidData(normalized)
        if (pkgFromAndroidData != null && isProtectedPackageName(pkgFromAndroidData)) return true
        return false
    }

    fun isProtectedFile(file: File?): Boolean {
        if (file == null) return true
        return isProtectedPath(file.absolutePath)
    }

    fun isProtectedPackageName(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return true
        val pkg = packageName.lowercase()
        return PROTECTED_PACKAGE_PREFIXES.any { prefix ->
            if (prefix.endsWith(".")) pkg.startsWith(prefix) else pkg == prefix || pkg.startsWith("$prefix.")
        }
    }

    fun isSystemApp(context: Context, packageName: String): Boolean {
        if (isProtectedPackageName(packageName)) return true
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            true // şüphede koru
        }
    }

    /** Silmeye izin var mı? */
    fun canDelete(path: String?): Boolean = !isProtectedPath(path)

    fun canDelete(file: File?): Boolean = !isProtectedFile(file)

    fun canCleanPackageCache(context: Context, packageName: String): Boolean {
        if (packageName == context.packageName) return true
        return !isSystemApp(context, packageName)
    }

    private fun extractPackageFromAndroidData(path: String): String? {
        val markers = listOf("/android/data/", "/android/obb/", "/android/media/")
        for (marker in markers) {
            val idx = path.indexOf(marker)
            if (idx >= 0) {
                val rest = path.substring(idx + marker.length)
                return rest.substringBefore('/')
            }
        }
        return null
    }

    private fun extractPackageFromDataPath(path: String): String? {
        val markers = listOf("/data/data/", "/data/user/0/", "/data/user_de/0/")
        for (marker in markers) {
            val idx = path.indexOf(marker)
            if (idx >= 0) {
                val rest = path.substring(idx + marker.length)
                return rest.substringBefore('/')
            }
        }
        return null
    }
}
