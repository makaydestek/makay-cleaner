package com.makay.cleaner.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.makay.cleaner.MainActivity

object PermissionHelper {

    fun hasStoragePermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) return true
            // Settings donusu / OEM gecikmesi icin yedek kontrol
            return canReadExternalStorage()
        }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun canReadExternalStorage(): Boolean {
        return try {
            val dir = Environment.getExternalStorageDirectory()
            dir != null && dir.exists() && dir.canRead() && dir.listFiles() != null
        } catch (_: Exception) {
            false
        }
    }

    fun findActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return MainActivity.instance?.get()
    }

    fun requestStoragePermission(context: Context) {
        val activity = findActivity(context) ?: return
        (activity as? MainActivity)?.requestStoragePermission() ?: run {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:${activity.packageName}")
                    activity.startActivity(intent)
                } catch (_: Exception) {
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            } else {
                activity.requestPermissions(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    1001
                )
            }
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}

@Composable
fun rememberStoragePermissionState(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(PermissionHelper.hasStoragePermission(context))
    }

    fun refresh() {
        hasPermission = PermissionHelper.hasStoragePermission(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME || event == Lifecycle.Event.ON_START) {
                refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        refresh()
        kotlinx.coroutines.delay(350)
        refresh()
    }

    val activity = remember { PermissionHelper.findActivity(context) as? MainActivity }
    if (activity != null) {
        val activityPerm by activity.hasStoragePermission
        LaunchedEffect(activityPerm) {
            hasPermission = activityPerm || PermissionHelper.hasStoragePermission(context)
        }
    }

    return hasPermission
}
