package com.makay.cleaner.util

import android.os.Environment
import android.os.StatFs

/** Depolama boş alan ölçümü — temizlik kazancını doğrulamak için. */
object StorageFreeSpace {

    data class Snapshot(
        val dataFree: Long,
        val externalFree: Long
    ) {
        val totalFree: Long get() = dataFree + externalFree
    }

    fun snapshot(): Snapshot {
        val data = freeOf(Environment.getDataDirectory())
        val ext = freeOf(Environment.getExternalStorageDirectory())
        return Snapshot(dataFree = data, externalFree = ext)
    }

    fun gained(before: Snapshot, after: Snapshot): Long {
        val dataGain = (after.dataFree - before.dataFree).coerceAtLeast(0L)
        val extGain = (after.externalFree - before.externalFree).coerceAtLeast(0L)
        // Aynı birimde çift sayımı önlemek için max (çoğu cihazda data≈external)
        return maxOf(dataGain, extGain)
    }

    private fun freeOf(path: java.io.File?): Long {
        if (path == null || !path.exists()) return 0L
        return try {
            StatFs(path.absolutePath).availableBytes
        } catch (_: Exception) {
            0L
        }
    }
}
