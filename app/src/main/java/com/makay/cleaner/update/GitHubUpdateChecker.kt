package com.makay.cleaner.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.makay.cleaner.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Güncelleme kaynağı: GitHub Releases
 * https://github.com/makaydestek/makay-cleaner/releases
 *
 * Yayın akışı: tag (v1.0.46) + MakayCleaner_v1.0.46.apk asset yükle.
 */
object GitHubUpdateChecker {

    const val OWNER = "makaydestek"
    const val REPO = "makay-cleaner"
    private const val API =
        "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    private const val RELEASES_PAGE =
        "https://github.com/$OWNER/$REPO/releases/latest"

    data class UpdateInfo(
        val versionName: String,
        val versionCode: Int,
        val apkUrl: String?,
        val releaseNotes: String,
        val htmlUrl: String
    )

    fun currentVersionName(): String = BuildConfig.VERSION_NAME

    fun currentVersionCode(): Int = BuildConfig.VERSION_CODE

    suspend fun checkLatest(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 12_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "MakayCleaner/${BuildConfig.VERSION_NAME}")
            }
            try {
                if (conn.responseCode == 404) return@runCatching null
                check(conn.responseCode in 200..299) {
                    "GitHub API ${conn.responseCode}"
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parseRelease(body)
            } finally {
                conn.disconnect()
            }
        }
    }

    fun isNewer(remote: UpdateInfo): Boolean {
        if (remote.versionCode > currentVersionCode()) return true
        return compareVersionNames(remote.versionName, currentVersionName()) > 0
    }

    private fun parseRelease(json: String): UpdateInfo? {
        val obj = JSONObject(json)
        if (obj.optBoolean("draft", false) || obj.optBoolean("prerelease", false)) {
            return null
        }
        val tag = obj.optString("tag_name", "").trim()
        val versionName = tag.removePrefix("v").removePrefix("V").trim()
        if (versionName.isEmpty()) return null
        val versionCode = versionNameToCode(versionName)
        val assets = obj.optJSONArray("assets")
        var apkUrl: String? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val name = a.optString("name", "")
                val url = a.optString("browser_download_url", "")
                if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                    apkUrl = url
                    if (name.contains(versionName, ignoreCase = true)) break
                }
            }
        }
        return UpdateInfo(
            versionName = versionName,
            versionCode = versionCode,
            apkUrl = apkUrl,
            releaseNotes = obj.optString("body", "").take(800),
            htmlUrl = obj.optString("html_url", RELEASES_PAGE)
        )
    }

    fun versionNameToCode(name: String): Int {
        val parts = name.split('.')
        if (parts.size < 3) return 0
        val major = parts[0].toIntOrNull() ?: return 0
        val minor = parts[1].toIntOrNull() ?: return 0
        val patch = parts[2].filter { it.isDigit() }.toIntOrNull() ?: return 0
        return major * 10000 + minor * 100 + patch
    }

    /** >0 remote daha yeni */
    fun compareVersionNames(a: String, b: String): Int {
        val pa = a.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val pb = b.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }

    suspend fun downloadApk(context: Context, url: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").also { it.mkdirs() }
        val out = File(dir, "MakayCleaner-update.apk")
        if (out.exists()) out.delete()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 120_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "MakayCleaner/${BuildConfig.VERSION_NAME}")
        }
        try {
            check(conn.responseCode in 200..299) { "İndirme ${conn.responseCode}" }
            conn.inputStream.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            out
        } finally {
            conn.disconnect()
        }
    }

    fun installApk(context: Context, apk: File) {
        val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        } else {
            Uri.fromFile(apk)
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    fun openReleasesPage(context: Context, url: String = RELEASES_PAGE) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
