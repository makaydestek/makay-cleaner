package com.makay.cleaner.util

import android.content.Context
import android.os.Environment
import java.io.File

object TvHelper {

    /**
     * Android TV'de sık kullanılan streaming/medya uygulamalarının önbellek dizinleri (YouTube TV, Netflix, VLC, IPTV vb.)
     */
    fun getTvStreamingCachePaths(context: Context): List<File> {
        val ext = Environment.getExternalStorageDirectory() ?: return emptyList()
        val androidData = File(ext, "Android/data")
        val tvPackages = listOf(
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "org.videolan.vlc",
            "com.amazon.firetv.youtube",
            "com.iptv.xtream",
            "com.tivimate.companion"
        )
        val paths = mutableListOf<File>()
        tvPackages.forEach { pkg ->
            val cacheDir = File(androidData, "$pkg/cache")
            if (cacheDir.exists() && cacheDir.isDirectory) {
                paths.add(cacheDir)
            }
            val filesDir = File(androidData, "$pkg/files")
            if (filesDir.exists() && filesDir.isDirectory) {
                paths.add(filesDir)
            }
        }
        return paths
    }
}
