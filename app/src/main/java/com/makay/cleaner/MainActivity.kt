package com.makay.cleaner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.makay.cleaner.data.ThemeRepository
import com.makay.cleaner.ui.AppWithSplash
import com.makay.cleaner.ui.formatBytes
import com.makay.cleaner.ui.theme.MakayCleanerTheme
import com.makay.cleaner.util.QuickCleanHelper
import java.lang.ref.WeakReference

class MainActivity : FragmentActivity() {

    companion object {
        const val ACTION_QUICK_CLEAN = "com.makay.cleaner.QUICK_CLEAN"
        var instance: WeakReference<MainActivity>? = null
    }

    private lateinit var themeRepository: ThemeRepository

    private val _hasStoragePermission = mutableStateOf(false)
    val hasStoragePermission: State<Boolean> = _hasStoragePermission

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkStoragePermission()
    }

    private val runtimePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        checkStoragePermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        instance = WeakReference(this)
        enableEdgeToEdge()

        themeRepository = ThemeRepository.getInstance(this)
        checkStoragePermission()
        requestRuntimePermissionsIfNeeded()

        setContent {
            MakayCleanerTheme(themeRepository = themeRepository) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppWithSplash()
                }
            }
        }

        handleWidgetIntent(intent)
        handleDeepLink(intent)
    }

    private fun requestRuntimePermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed += Manifest.permission.POST_NOTIFICATIONS
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed += Manifest.permission.READ_MEDIA_IMAGES
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed += Manifest.permission.READ_MEDIA_VIDEO
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed += Manifest.permission.READ_MEDIA_AUDIO
            }
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed += Manifest.permission.READ_EXTERNAL_STORAGE
                needed += Manifest.permission.WRITE_EXTERNAL_STORAGE
            }
        }
        if (needed.isNotEmpty()) {
            runtimePermissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun checkStoragePermission() {
        _hasStoragePermission.value = com.makay.cleaner.util.PermissionHelper.hasStoragePermission(this)
    }

    fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                storagePermissionLauncher.launch(intent)
            } catch (_: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                storagePermissionLauncher.launch(intent)
            }
        } else {
            runtimePermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    override fun onResume() {
        super.onResume()
        checkStoragePermission()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val link = intent?.getStringExtra(com.makay.cleaner.notification.NotificationManager.EXTRA_DEEP_LINK)
        com.makay.cleaner.util.DeepLinkBus.offer(link)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    private fun handleWidgetIntent(intent: Intent?) {
        if (intent?.action == ACTION_QUICK_CLEAN || intent?.getBooleanExtra("quick_clean", false) == true) {
            val result = QuickCleanHelper.cleanAppCaches(this)
            Toast.makeText(
                this,
                if (result.filesDeleted > 0)
                    "Hızlı temizlik: ${formatBytes(result.spaceSaved)}"
                else
                    "Temizlenecek dosya yok",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
