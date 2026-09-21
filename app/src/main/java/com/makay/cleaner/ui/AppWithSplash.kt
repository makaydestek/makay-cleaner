package com.makay.cleaner.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.makay.cleaner.ai.SmartSuggestions
import com.makay.cleaner.data.AppUninstallRepository
import com.makay.cleaner.security.AppLockManager
import com.makay.cleaner.util.QuickCleanHelper

sealed class AppScreen {
    data object Lock : AppScreen()
    data object Storage : AppScreen()
    data object Cache : AppScreen()
    data object LargeFiles : AppScreen()
    data object Uninstall : AppScreen()
    data object SocialMedia : AppScreen()
    data object Stats : AppScreen()
    data object Settings : AppScreen()
    data object Notifications : AppScreen()
    data object Security : AppScreen()
    data object Analytics : AppScreen()
    data object Suggestions : AppScreen()
    data object Toolbox : AppScreen()
    data object RecycleBin : AppScreen()
    data object Duplicates : AppScreen()
    data object SystemCleaner : AppScreen()
    data object CorpseFinder : AppScreen()
    data object MediaHub : AppScreen()
    data object SimilarPhotos : AppScreen()
    data object MediaCompress : AppScreen()
    data object AppControl : AppScreen()
    data object Pro : AppScreen()
    data object VideoTools : AppScreen()
    data object ApkRemnants : AppScreen()
    data object DuplicatePhotos : AppScreen()
    data object MediaCleanHub : AppScreen()
    data object Permissions : AppScreen()
    data object CleanerHub : AppScreen()
    data object SecurityScan : AppScreen()
    data object PrivacyHub : AppScreen()
    data object BatteryInfo : AppScreen()
    data object DataUsage : AppScreen()
    data object ManageApps : AppScreen()
}

@Composable
fun AppWithSplash() {
    var showSplash by remember { mutableStateOf(true) }

    if (showSplash) {
        SplashScreen(onSplashFinished = { showSplash = false })
    } else {
        MainAppContent()
    }
}

@Composable
fun MainAppContent() {
    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Storage) }
    var recycleReturn by remember { mutableStateOf<AppScreen>(AppScreen.Toolbox) }
    var dupPhotosReturn by remember { mutableStateOf<AppScreen>(AppScreen.Toolbox) }
    var mediaHubReturn by remember { mutableStateOf<AppScreen>(AppScreen.MediaCleanHub) }
    var appControlReturn by remember { mutableStateOf<AppScreen>(AppScreen.Toolbox) }
    var hubReturn by remember { mutableStateOf<AppScreen>(AppScreen.Storage) }
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val appLockManager = remember { AppLockManager(app) }
    var isLocked by remember { mutableStateOf(appLockManager.shouldLock()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val uninstallRepo = remember { AppUninstallRepository(context.applicationContext) }
    val deepLink by com.makay.cleaner.util.DeepLinkBus.link.collectAsState()

    LaunchedEffect(deepLink) {
        val link = com.makay.cleaner.util.DeepLinkBus.consume() ?: return@LaunchedEffect
        if (isLocked) return@LaunchedEffect
        currentScreen = when (link) {
            com.makay.cleaner.notification.NotificationManager.DEEP_CACHE -> AppScreen.Cache
            com.makay.cleaner.notification.NotificationManager.DEEP_STATS -> AppScreen.Stats
            com.makay.cleaner.notification.NotificationManager.DEEP_SUGGESTIONS -> AppScreen.Suggestions
            com.makay.cleaner.notification.NotificationManager.DEEP_TOOLBOX -> AppScreen.Toolbox
            com.makay.cleaner.notification.NotificationManager.DEEP_RECYCLE -> {
                recycleReturn = AppScreen.Storage
                AppScreen.RecycleBin
            }
            com.makay.cleaner.notification.NotificationManager.DEEP_LARGE -> AppScreen.LargeFiles
            com.makay.cleaner.notification.NotificationManager.DEEP_MEDIA -> AppScreen.MediaCleanHub
            com.makay.cleaner.notification.NotificationManager.DEEP_DUP_PHOTOS -> {
                dupPhotosReturn = AppScreen.Storage
                AppScreen.DuplicatePhotos
            }
            else -> AppScreen.Storage
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (appLockManager.shouldLock()) {
                        isLocked = true
                        currentScreen = AppScreen.Lock
                    }
                    val result = uninstallRepo.processPendingUninstallCleanup()
                    if (result != null) {
                        val (pkg, cleanup) = result
                        val (saved, count) = cleanup
                        if (count > 0 || saved > 0) {
                            Toast.makeText(
                                context,
                                "$pkg kalıntıları temizlendi (${formatBytes(saved)})",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (isLocked && currentScreen !is AppScreen.Lock) {
        LaunchedEffect(Unit) {
            currentScreen = AppScreen.Lock
        }
    }

    BackHandler(enabled = currentScreen !is AppScreen.Storage && currentScreen !is AppScreen.Lock) {
        currentScreen = when (currentScreen) {
            AppScreen.Notifications, AppScreen.Security, AppScreen.Pro -> AppScreen.Settings
            AppScreen.RecycleBin -> recycleReturn
            AppScreen.SystemCleaner, AppScreen.CorpseFinder, AppScreen.ApkRemnants,
            AppScreen.MediaCleanHub -> AppScreen.Toolbox
            AppScreen.CleanerHub, AppScreen.SecurityScan, AppScreen.PrivacyHub,
            AppScreen.BatteryInfo, AppScreen.DataUsage, AppScreen.ManageApps -> hubReturn
            AppScreen.AppControl -> appControlReturn
            AppScreen.VideoTools, AppScreen.MediaHub, AppScreen.SimilarPhotos, AppScreen.MediaCompress,
            AppScreen.Duplicates -> mediaHubReturn
            AppScreen.DuplicatePhotos -> dupPhotosReturn
            AppScreen.Permissions -> AppScreen.Storage
            else -> AppScreen.Storage
        }
    }

    when (currentScreen) {
        AppScreen.Lock -> {
            AppLockScreen(
                application = app,
                onSuccess = {
                    isLocked = false
                    currentScreen = AppScreen.Storage
                }
            )
        }
        AppScreen.Storage -> {
            StorageScreen(
                onNavigateToCache = { currentScreen = AppScreen.Cache },
                onNavigateToLargeFiles = { currentScreen = AppScreen.LargeFiles },
                onNavigateToUninstall = { currentScreen = AppScreen.Uninstall },
                onNavigateToSocialMedia = { currentScreen = AppScreen.SocialMedia },
                onNavigateToStats = { currentScreen = AppScreen.Stats },
                onNavigateToSettings = { currentScreen = AppScreen.Settings },
                onNavigateToAnalytics = { currentScreen = AppScreen.Analytics },
                onNavigateToSuggestions = { currentScreen = AppScreen.Suggestions },
                onNavigateToToolbox = { currentScreen = AppScreen.Toolbox },
                onNavigateToRecycleBin = {
                    recycleReturn = AppScreen.Storage
                    currentScreen = AppScreen.RecycleBin
                },
                onNavigateToDuplicatePhotos = {
                    dupPhotosReturn = AppScreen.Storage
                    currentScreen = AppScreen.DuplicatePhotos
                },
                onNavigateToMediaClean = { currentScreen = AppScreen.MediaCleanHub },
                onNavigateToPermissions = { currentScreen = AppScreen.Permissions },
                onNavigateToAppControl = {
                    appControlReturn = AppScreen.Storage
                    currentScreen = AppScreen.AppControl
                },
                onNavigateToCleanerHub = {
                    hubReturn = AppScreen.Storage
                    currentScreen = AppScreen.CleanerHub
                },
                onNavigateToSecurityScan = {
                    hubReturn = AppScreen.Storage
                    currentScreen = AppScreen.SecurityScan
                },
                onNavigateToBattery = {
                    hubReturn = AppScreen.Storage
                    currentScreen = AppScreen.BatteryInfo
                },
                onNavigateToDataUsage = {
                    hubReturn = AppScreen.Storage
                    currentScreen = AppScreen.DataUsage
                },
                onNavigateToPrivacy = {
                    hubReturn = AppScreen.Storage
                    currentScreen = AppScreen.PrivacyHub
                },
                onNavigateToManageApps = {
                    hubReturn = AppScreen.Storage
                    currentScreen = AppScreen.ManageApps
                }
            )
        }
        AppScreen.Cache -> {
            AppCacheScreen(application = app, onNavigateBack = { currentScreen = AppScreen.Storage })
        }
        AppScreen.LargeFiles -> {
            LargeFileScreen(application = app, onNavigateBack = { currentScreen = AppScreen.Storage })
        }
        AppScreen.Uninstall -> {
            AppUninstallScreen(application = app, onNavigateBack = { currentScreen = AppScreen.Storage })
        }
        AppScreen.SocialMedia -> {
            SocialMediaScreen(application = app, onNavigateBack = { currentScreen = AppScreen.Storage })
        }
        AppScreen.Stats -> {
            CleaningStatsScreen(application = app, onNavigateBack = { currentScreen = AppScreen.Storage })
        }
        AppScreen.Settings -> {
            SettingsScreen(
                application = app,
                onNavigateBack = { currentScreen = AppScreen.Storage },
                onNavigateToNotifications = { currentScreen = AppScreen.Notifications },
                onNavigateToSecurity = { currentScreen = AppScreen.Security },
                onNavigateToPro = { currentScreen = AppScreen.Pro },
                onNavigateToRecycleBin = {
                    recycleReturn = AppScreen.Settings
                    currentScreen = AppScreen.RecycleBin
                }
            )
        }
        AppScreen.Notifications -> {
            NotificationScreen(application = app, onNavigateBack = { currentScreen = AppScreen.Settings })
        }
        AppScreen.Security -> {
            SecuritySettingsScreen(application = app, onNavigateBack = { currentScreen = AppScreen.Settings })
        }
        AppScreen.Analytics -> {
            AnalyticsScreen(application = app, onNavigateBack = { currentScreen = AppScreen.Storage })
        }
        AppScreen.Suggestions -> {
            SuggestionsScreen(
                application = app,
                onNavigateBack = { currentScreen = AppScreen.Storage },
                onSuggestionClick = { suggestion ->
                    SmartSuggestions(app).saveUserPreference(suggestion.category, 1)
                    when (suggestion.id) {
                        "cleaning_reminder", "urgent_storage", "high_storage" -> {
                            val result = QuickCleanHelper.cleanAppCaches(context)
                            Toast.makeText(
                                context,
                                if (result.filesDeleted > 0)
                                    "Temizlik tamamlandı: ${formatBytes(result.spaceSaved)}"
                                else
                                    "Önbellek zaten temiz",
                                Toast.LENGTH_SHORT
                            ).show()
                            currentScreen = AppScreen.Cache
                        }
                        else -> currentScreen = when (suggestion.category) {
                            SmartSuggestions.Category.CACHE -> AppScreen.Cache
                            SmartSuggestions.Category.LARGE_FILES -> AppScreen.LargeFiles
                            SmartSuggestions.Category.UNUSED_APPS -> {
                                appControlReturn = AppScreen.Suggestions
                                AppScreen.AppControl
                            }
                            SmartSuggestions.Category.SOCIAL_MEDIA -> AppScreen.SocialMedia
                            SmartSuggestions.Category.STORAGE -> AppScreen.Cache
                        }
                    }
                }
            )
        }
        AppScreen.Toolbox -> {
            ToolboxScreen(
                onNavigateBack = { currentScreen = AppScreen.Storage },
                onOpen = { id ->
                    hubReturn = AppScreen.Toolbox
                    currentScreen = when (id) {
                        "cleaner" -> AppScreen.CleanerHub
                        "media_clean" -> AppScreen.MediaCleanHub
                        "recycle" -> {
                            recycleReturn = AppScreen.Toolbox
                            AppScreen.RecycleBin
                        }
                        "system_cleaner" -> AppScreen.SystemCleaner
                        "corpse" -> AppScreen.CorpseFinder
                        "apk" -> AppScreen.ApkRemnants
                        "dup_photos" -> {
                            dupPhotosReturn = AppScreen.Toolbox
                            AppScreen.DuplicatePhotos
                        }
                        "data_usage" -> AppScreen.DataUsage
                        "battery" -> AppScreen.BatteryInfo
                        "security_scan" -> AppScreen.SecurityScan
                        "app_control" -> {
                            appControlReturn = AppScreen.Toolbox
                            AppScreen.AppControl
                        }
                        "manage_apps" -> AppScreen.ManageApps
                        "privacy" -> AppScreen.PrivacyHub
                        "permissions" -> AppScreen.Permissions
                        "app_lock" -> AppScreen.Security
                        else -> AppScreen.Toolbox
                    }
                }
            )
        }
        AppScreen.MediaCleanHub -> {
            MediaCleanHubScreen(
                application = app,
                onNavigateBack = { currentScreen = AppScreen.Toolbox },
                onOpen = { id ->
                    mediaHubReturn = AppScreen.MediaCleanHub
                    currentScreen = when (id) {
                        "dup_photos" -> {
                            dupPhotosReturn = AppScreen.MediaCleanHub
                            AppScreen.DuplicatePhotos
                        }
                        "media_hub" -> AppScreen.MediaHub
                        "video" -> AppScreen.VideoTools
                        "similar" -> AppScreen.SimilarPhotos
                        "duplicates" -> AppScreen.Duplicates
                        "compress" -> AppScreen.MediaCompress
                        else -> AppScreen.MediaCleanHub
                    }
                }
            )
        }
        AppScreen.Permissions -> {
            PermissionScreen(
                onPermissionsGranted = { currentScreen = AppScreen.Storage }
            )
        }
        AppScreen.RecycleBin -> {
            RecycleBinScreen(
                application = app,
                onNavigateBack = { currentScreen = recycleReturn }
            )
        }
        AppScreen.Duplicates -> {
            DuplicatesScreen(
                application = app,
                onNavigateBack = { currentScreen = mediaHubReturn },
                onRequirePro = { currentScreen = AppScreen.Pro }
            )
        }
        AppScreen.SystemCleaner -> {
            SystemCleanerScreen(application = app, onNavigateBack = { currentScreen = AppScreen.Toolbox })
        }
        AppScreen.CorpseFinder -> {
            CorpseFinderScreen(application = app, onNavigateBack = { currentScreen = AppScreen.Toolbox })
        }
        AppScreen.MediaHub -> {
            MediaHubScreen(application = app, onNavigateBack = { currentScreen = mediaHubReturn })
        }
        AppScreen.SimilarPhotos -> {
            SimilarPhotosScreen(
                application = app,
                onNavigateBack = { currentScreen = mediaHubReturn },
                onRequirePro = { currentScreen = AppScreen.Pro }
            )
        }
        AppScreen.MediaCompress -> {
            MediaCompressScreen(
                application = app,
                onNavigateBack = { currentScreen = mediaHubReturn },
                onRequirePro = { currentScreen = AppScreen.Pro }
            )
        }
        AppScreen.AppControl -> {
            AppControlScreen(application = app, onNavigateBack = { currentScreen = appControlReturn })
        }
        AppScreen.VideoTools -> {
            VideoToolsScreen(application = app, onNavigateBack = { currentScreen = mediaHubReturn })
        }
        AppScreen.ApkRemnants -> {
            ApkRemnantScreen(application = app, onNavigateBack = { currentScreen = AppScreen.Toolbox })
        }
        AppScreen.DuplicatePhotos -> {
            DuplicatePhotosScreen(
                application = app,
                onNavigateBack = { currentScreen = dupPhotosReturn }
            )
        }
        AppScreen.Pro -> {
            ProScreen(application = app, onNavigateBack = { currentScreen = AppScreen.Settings })
        }
        AppScreen.CleanerHub -> {
            CleanerHubScreen(
                application = app,
                onNavigateBack = { currentScreen = hubReturn },
                onOpenCategory = { id ->
                    currentScreen = when (id) {
                        "cache" -> AppScreen.Cache
                        "apk" -> AppScreen.ApkRemnants
                        "residue" -> AppScreen.CorpseFinder
                        "tmp" -> AppScreen.SystemCleaner
                        "unused" -> AppScreen.LargeFiles
                        else -> AppScreen.Cache
                    }
                }
            )
        }
        AppScreen.SecurityScan -> {
            SecurityScanScreen(
                application = app,
                onNavigateBack = { currentScreen = hubReturn },
                onNavigateToSettings = { currentScreen = AppScreen.Settings },
                onOpenCleaner = {
                    hubReturn = AppScreen.Storage
                    currentScreen = AppScreen.CleanerHub
                },
                onOpenPermissions = { currentScreen = AppScreen.Permissions },
                onOpenManageApps = {
                    hubReturn = AppScreen.SecurityScan
                    currentScreen = AppScreen.ManageApps
                }
            )
        }
        AppScreen.PrivacyHub -> {
            PrivacyHubScreen(
                onNavigateBack = { currentScreen = hubReturn },
                onOpenAppLock = { currentScreen = AppScreen.Security },
                onOpenPermissions = { currentScreen = AppScreen.Permissions },
                onOpenPro = { }
            )
        }
        AppScreen.BatteryInfo -> {
            BatteryInfoScreen(onNavigateBack = { currentScreen = hubReturn })
        }
        AppScreen.DataUsage -> {
            DataUsageScreen(onNavigateBack = { currentScreen = hubReturn })
        }
        AppScreen.ManageApps -> {
            ManageAppsScreen(
                application = app,
                onNavigateBack = { currentScreen = hubReturn },
                onOpenAppLock = { currentScreen = AppScreen.Security },
                onOpenAppControl = {
                    appControlReturn = AppScreen.ManageApps
                    currentScreen = AppScreen.AppControl
                },
                onOpenUninstall = { currentScreen = AppScreen.Uninstall }
            )
        }
    }
}
