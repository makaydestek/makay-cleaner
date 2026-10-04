package com.makay.cleaner.util

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import java.io.File
import java.lang.reflect.Proxy
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * Xiaomi Güvenlik’in sildiği uygulama iç önbelleği.
 * Sideload File.delete ile buna erişemez; sistem allocateBytes / freeStorage
 * ile önbelleği boşaltır — Xiaomi taramasının da düşmesi gerekir.
 */
object SystemCacheReclaimer {

    data class Result(val bytesFreed: Long, val packagesTouched: Int)

    fun queryCacheBytes(context: Context): Long {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return 0L
        val ssm = context.getSystemService(StorageStatsManager::class.java) ?: return 0L
        val sm = context.getSystemService(StorageManager::class.java)
        val uuid = storageUuid(sm)
        val device = deviceWideCacheBytes(ssm, uuid)
        val perPackage = sumPackageCacheBytes(context, ssm)
        return max(device, perPackage)
    }

    fun reclaim(context: Context, targetBytes: Long = queryCacheBytes(context)): Result {
        val before = queryCacheBytes(context)
        val goal = max(targetBytes, before)
        if (goal <= 0L && before <= 0L) {
            val ext = wipeExternalAppCaches(context)
            return Result(ext.spaceSaved, ext.filesDeleted)
        }

        val packages = tryDeleteApplicationCacheFiles(context)
        freeStorage(context, goal)
        evictViaAllocate(context, goal)
        val ext = wipeExternalAppCaches(context)

        val after = queryCacheBytes(context)
        val dropped = (before - after).coerceAtLeast(0L)
        val freed = max(dropped, ext.spaceSaved)
        return Result(freed, packages + if (ext.filesDeleted > 0) 1 else 0)
    }

    private fun deviceWideCacheBytes(ssm: StorageStatsManager, uuid: UUID): Long {
        return try {
            val m = StorageStatsManager::class.java.getMethod("getCacheBytes", UUID::class.java)
            (m.invoke(ssm, uuid) as? Long) ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun sumPackageCacheBytes(context: Context, ssm: StorageStatsManager): Long {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return 0L
        var total = 0L
        val user = android.os.Process.myUserHandle()
        context.packageManager.getInstalledPackages(0).forEach { pkg ->
            if (!SystemFileGuard.canCleanPackageCache(context, pkg.packageName)) return@forEach
            try {
                val uuid = pkg.applicationInfo?.storageUuid ?: StorageManager.UUID_DEFAULT
                val stats = ssm.queryStatsForPackage(uuid, pkg.packageName, user)
                total += stats.cacheBytes
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    total += stats.externalCacheBytes
                }
            } catch (_: Exception) {
            }
        }
        return total
    }

    private fun evictViaAllocate(context: Context, wantBytes: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val sm = context.getSystemService(StorageManager::class.java) ?: return
        val uuid = storageUuid(sm)
        val allocatable = allocatableBytes(sm, uuid)
        val target = min(wantBytes, allocatable).coerceAtLeast(0L)
        if (target <= 0L) return

        val chunk = 128L * 1024 * 1024
        var left = target
        while (left > 0L) {
            val n = min(chunk, left)
            val tmp = File(context.cacheDir, "makay_alloc_${System.nanoTime()}")
            try {
                tmp.createNewFile()
                ParcelFileDescriptor.open(
                    tmp,
                    ParcelFileDescriptor.MODE_READ_WRITE
                ).use { pfd ->
                    sm.allocateBytes(pfd.fileDescriptor, n)
                }
            } catch (_: Exception) {
                left = 0L
            } finally {
                if (tmp.exists()) tmp.delete()
            }
            if (left == 0L) break
            left -= n
        }
    }

    private fun allocatableBytes(sm: StorageManager, uuid: UUID): Long {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return 0L
        // FLAG_ALLOCATE_AGGRESSIVE = 1 — sistem önbelleğini daha agresif boşaltır
        runCatching {
            val m = StorageManager::class.java.getMethod(
                "getAllocatableBytes",
                UUID::class.java,
                Int::class.javaPrimitiveType
            )
            return (m.invoke(sm, uuid, 1) as? Long) ?: 0L
        }
        return runCatching { sm.getAllocatableBytes(uuid) }.getOrDefault(0L)
    }

    private fun freeStorage(context: Context, bytes: Long) {
        val pm = context.packageManager
        val observer = packageDataObserver()
        runCatching {
            val m = pm.javaClass.methods.first {
                it.name == "freeStorageAndNotify" && it.parameterTypes.size == 2
            }
            m.invoke(pm, bytes, observer)
        }
        runCatching {
            val m = pm.javaClass.methods.first {
                it.name == "freeStorage" && it.parameterTypes.size >= 2
            }
            when (m.parameterTypes.size) {
                2 -> m.invoke(pm, bytes, observer)
                3 -> m.invoke(pm, null, bytes, null)
                else -> m.invoke(pm, bytes)
            }
        }
    }

    private fun tryDeleteApplicationCacheFiles(context: Context): Int {
        val pm = context.packageManager
        val method = pm.javaClass.methods.firstOrNull {
            it.name == "deleteApplicationCacheFiles" && it.parameterTypes.isNotEmpty()
        } ?: return 0
        val observer = packageDataObserver()
        var touched = 0
        pm.getInstalledPackages(PackageManager.GET_META_DATA).forEach { pkg ->
            val name = pkg.packageName
            if (name == context.packageName) return@forEach
            if (!SystemFileGuard.canCleanPackageCache(context, name)) return@forEach
            try {
                method.invoke(pm, name, observer)
                touched++
            } catch (_: Exception) {
            }
        }
        return touched
    }

    private fun packageDataObserver(): Any? {
        return try {
            val clz = Class.forName("android.content.pm.IPackageDataObserver")
            Proxy.newProxyInstance(clz.classLoader, arrayOf(clz)) { _, _, _ -> null }
        } catch (_: Exception) {
            null
        }
    }

    private fun wipeExternalAppCaches(context: Context): FilePurger.Result {
        var count = 0
        var saved = 0L
        val roots = mutableListOf<File>()
        val ext = Environment.getExternalStorageDirectory() ?: return FilePurger.Result(0, 0)
        val androidData = File(ext, "Android/data")
        androidData.listFiles()?.forEach { appDir ->
            if (!appDir.isDirectory) return@forEach
            if (!SystemFileGuard.canCleanPackageCache(context, appDir.name)) return@forEach
            roots += File(appDir, "cache")
            roots += File(appDir, "code_cache")
        }
        roots += TvHelper.getTvStreamingCachePaths(context)
        roots.distinctBy { it.absolutePath }.forEach { cacheDir ->
            if (!cacheDir.exists()) return@forEach
            cacheDir.walkTopDown().maxDepth(6).toList().asReversed().forEach { file ->
                if (!file.isFile || file.length() <= 0L) return@forEach
                if (!SystemFileGuard.canDelete(file)) return@forEach
                val size = file.length()
                if (FilePurger.tryDelete(file) && !file.exists()) {
                    count++
                    saved += size
                }
            }
        }
        return FilePurger.Result(count, saved)
    }

    private fun storageUuid(sm: StorageManager?): UUID {
        if (sm == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return StorageManager.UUID_DEFAULT
        }
        return runCatching { sm.getUuidForPath(Environment.getDataDirectory()) }
            .getOrDefault(StorageManager.UUID_DEFAULT)
    }
}
