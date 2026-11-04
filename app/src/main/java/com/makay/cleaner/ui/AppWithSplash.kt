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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    data object Exclusions : AppScreen()
    data object Duplicates : AppScreen()
    data object SystemCleaner : AppScreen()
    data object CorpseFinder : AppScreen()
    data object MediaHub : AppScreen()
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
    data object FileAnalysis : AppScreen()
    data object DuplicateVideos : AppScreen()
    data object PermissionMap : AppScreen()
    data object ResourceUsage : AppScreen()
    data object SoftHibernate : AppScreen()
    data object BlurPhotos : AppScreen()
    data object PersonMismatch : AppScreen()
    data object AppProfiles : AppScreen()
    data object GoodNight : AppScreen()
    data object BrowserCache : AppScreen()
}

@Composable
fun AppWithSplash() {
    var showSplash by remember { mutableStateOf(true) }
    val context = LocalContext.current
    com.makay.cleaner.util.LanguageManager.init(context)
    com.makay.cleaner.util.PlatformManager.init(context)
    val prefs = remember { context.getSharedPreferences("platform_prefs", android.content.Context.MODE_PRIVATE) }
    var showPlatformSelect by remember { mutableStateOf(prefs.getString("platform_mode", null) == null) }
    var showGuide by remember { mutableStateOf(!isFirstLaunchDone(context)) }

    if (showSplash) {
        SplashScreen(onSplashFinished = { showSplash = false })
    } else if (showPlatformSelect) {
        PlatformSelectScreen(context = context, onSelected = { showPlatformSelect = false })
    } else if (showGuide) {
        FirstLaunchGuideScreen(
            context = context,
            onDone = {
                markFirstLaunchDone(context)
                showGuide = false
            }
        )
    } else {
        MainAppContent()
    }
}

@Composable
fun MainAppContent() {
    val nav = remember { AppNavigator(AppScreen.Storage) }
    val currentScreen = nav.current
    @Suppress("UNUSED_VARIABLE")
    val stackDepth = nav.depth

    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val appLockManager = remember { AppLockManager(app) }
    var isLocked by remember { mutableStateOf(appLockManager.shouldLock()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val uninstallRepo = remember { AppUninstallRepository(context.applicationContext) }
    val deepLink by com.makay.cleaner.util.DeepLinkBus.link.collectAsState()
    val resumeScope = rememberCoroutineScope()

    fun go(screen: AppScreen) = nav.navigate(screen)
    fun back() {
        if (!nav.pop()) nav.resetTo(AppScreen.Storage)
    }

    LaunchedEffect(deepLink) {
        val link = com.makay.cleaner.util.DeepLinkBus.consume() ?: return@LaunchedEffect
        if (isLocked) return@LaunchedEffect
        when (link) {
            com.makay.cleaner.notification.NotificationManager.DEEP_HOME -> nav.resetTo(AppScreen.Storage)
            com.makay.cleaner.notification.NotificationManager.DEEP_CACHE -> {
                nav.resetTo(AppScreen.Storage)
                go(AppScreen.Cache)
            }
            com.makay.cleaner.notification.NotificationManager.DEEP_STATS -> {
                nav.resetTo(AppScreen.Storage)
                go(AppScreen.Stats)
            }
            com.makay.cleaner.notification.NotificationManager.DEEP_SUGGESTIONS -> {
                nav.resetTo(AppScreen.Storage)
                go(AppScreen.Suggestions)
            }
            com.makay.cleaner.notification.NotificationManager.DEEP_TOOLBOX -> {
                nav.resetTo(AppScreen.Storage)
                go(AppScreen.Toolbox)
            }
            com.makay.cleaner.notification.NotificationManager.DEEP_RECYCLE -> {
                nav.resetTo(AppScreen.Storage)
                go(AppScreen.RecycleBin)
            }
            com.makay.cleaner.notification.NotificationManager.DEEP_LARGE -> {
                nav.resetTo(AppScreen.Storage)
                go(AppScreen.LargeFiles)
            }
            com.makay.cleaner.notification.NotificationManager.DEEP_MEDIA -> {
                nav.resetTo(AppScreen.Storage)
                go(AppScreen.MediaCleanHub)
            }
            com.makay.cleaner.notification.NotificationManager.DEEP_DUP_PHOTOS -> {
                nav.resetTo(AppScreen.Storage)
                go(AppScreen.DuplicatePhotos)
            }
            com.makay.cleaner.notification.NotificationManager.DEEP_GOOD_NIGHT -> {
                nav.resetTo(AppScreen.Storage)
                go(AppScreen.GoodNight)
            }
            com.makay.cleaner.notification.NotificationManager.DEEP_LIGHT_CLEAN -> {
                val result = QuickCleanHelper.cleanAppCaches(context)
                Toast.makeText(
                    context,
                    if (result.filesDeleted > 0)
                        "Önbellek temizlendi: ${formatBytes(result.spaceSaved)}"
                    else
                        "Önbellek zaten temiz",
                    Toast.LENGTH_SHORT
                ).show()
                nav.resetTo(AppScreen.Storage)
            }
            else -> nav.resetTo(AppScreen.Storage)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (appLockManager.shouldLock()) {
                        isLocked = true
                        nav.resetTo(AppScreen.Lock)
                    }
                    resumeScope.launch(Dispatchers.IO) {
                        val result = uninstallRepo.processPendingUninstallCleanup()
                        if (result != null) {
                            val (pkg, cleanup) = result
                            val (saved, count) = cleanup
                            if (count > 0 || saved > 0) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        "$pkg kalıntıları temizlendi (${formatBytes(saved)})",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
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
            nav.resetTo(AppScreen.Lock)
        }
    }

    BackHandler(enabled = currentScreen !is AppScreen.Storage && currentScreen !is AppScreen.Lock) {
        back()
    }

    when (currentScreen) {
        AppScreen.Lock -> {
            AppLockScreen(
                application = app,
                onSuccess = {
                    isLocked = false
                    nav.resetTo(AppScreen.Storage)
                }
            )
        }
        AppScreen.Storage -> {
            StorageScreen(
                onNavigateToCache = { go(AppScreen.Cache) },
                onNavigateToLargeFiles = { go(AppScreen.LargeFiles) },
                onNavigateToUninstall = { go(AppScreen.Uninstall) },
                onNavigateToSocialMedia = { go(AppScreen.SocialMedia) },
                onNavigateToStats = { go(AppScreen.Stats) },
                onNavigateToSettings = { go(AppScreen.Settings) },
                onNavigateToAnalytics = { go(AppScreen.Analytics) },
                onNavigateToSuggestions = { go(AppScreen.Suggestions) },
                onNavigateToToolbox = { go(AppScreen.Toolbox) },
                onNavigateToRecycleBin = { go(AppScreen.RecycleBin) },
                onNavigateToExclusions = { go(AppScreen.Exclusions) },
                onNavigateToDuplicatePhotos = { go(AppScreen.DuplicatePhotos) },
                onNavigateToPersonMismatch = { go(AppScreen.PersonMismatch) },
                onNavigateToMediaClean = { go(AppScreen.MediaCleanHub) },
                onNavigateToPermissions = { go(AppScreen.Permissions) },
                onNavigateToAppControl = { go(AppScreen.AppControl) },
                onNavigateToCleanerHub = { go(AppScreen.CleanerHub) },
                onNavigateToSecurityScan = { go(AppScreen.SecurityScan) },
                onNavigateToBattery = { go(AppScreen.BatteryInfo) },
                onNavigateToDataUsage = { go(AppScreen.DataUsage) },
                onNavigateToPrivacy = { go(AppScreen.PrivacyHub) },
                onNavigateToManageApps = { go(AppScreen.ManageApps) },
                onNavigateToFileAnalysis = { go(AppScreen.FileAnalysis) },
                onLightClean = {
                    resumeScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            QuickCleanHelper.cleanAppCaches(context)
                        }
                        Toast.makeText(
                            context,
                            if (result.filesDeleted > 0)
                                "Önbellek temizlendi: ${formatBytes(result.spaceSaved)}"
                            else
                                "Önbellek zaten temiz",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }
        AppScreen.Cache -> {
            AppCacheScreen(application = app, onNavigateBack = { back() })
        }
        AppScreen.LargeFiles -> {
            LargeFileScreen(application = app, onNavigateBack = { back() })
        }
        AppScreen.Uninstall -> {
            AppUninstallScreen(application = app, onNavigateBack = { back() })
        }
        AppScreen.SocialMedia -> {
            SocialMediaScreen(application = app, onNavigateBack = { back() })
        }
        AppScreen.Stats -> {
            CleaningStatsScreen(application = app, onNavigateBack = { back() })
        }
        AppScreen.Settings -> {
            SettingsScreen(
                application = app,
                onNavigateBack = { back() },
                onNavigateToNotifications = { go(AppScreen.Notifications) },
                onNavigateToSecurity = { go(AppScreen.Security) },
                onNavigateToPro = { go(AppScreen.Pro) },
                onNavigateToRecycleBin = { go(AppScreen.RecycleBin) },
                onNavigateToExclusions = { go(AppScreen.Exclusions) }
            )
        }
        AppScreen.Notifications -> {
            NotificationScreen(application = app, onNavigateBack = { back() })
        }
        AppScreen.Security -> {
            SecuritySettingsScreen(application = app, onNavigateBack = { back() })
        }
        AppScreen.Analytics -> {
            AnalyticsScreen(application = app, onNavigateBack = { back() })
        }
        AppScreen.Suggestions -> {
            SuggestionsScreen(
                application = app,
                onNavigateBack = { back() },
                onSuggestionClick = { suggestion ->
                    SmartSuggestions(app).saveUserPreference(suggestion.category, 1)
                    when (suggestion.id) {
                        "cleaning_reminder", "urgent_storage", "high_storage",
                        "cache_cleanup", "cache_tip" -> {
                            resumeScope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    QuickCleanHelper.cleanAppCaches(context)
                                }
                                Toast.makeText(
                                    context,
                                    if (result.filesDeleted > 0)
                                        "Önbellek temizlendi: ${formatBytes(result.spaceSaved)}"
                                    else
                                        "Önbellek zaten temiz",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        "wa_dup_videos" -> go(AppScreen.DuplicateVideos)
                        "large_files" -> go(AppScreen.LargeFiles)
                        "unused_apps" -> go(AppScreen.AppControl)
                        "social_media" -> go(AppScreen.SocialMedia)
                        else -> when (suggestion.category) {
                            SmartSuggestions.Category.CACHE -> go(AppScreen.Cache)
                            SmartSuggestions.Category.LARGE_FILES -> go(AppScreen.LargeFiles)
                            SmartSuggestions.Category.UNUSED_APPS -> go(AppScreen.AppControl)
                            SmartSuggestions.Category.SOCIAL_MEDIA -> go(AppScreen.SocialMedia)
                            SmartSuggestions.Category.DUPLICATE_MEDIA -> go(AppScreen.DuplicateVideos)
                            SmartSuggestions.Category.STORAGE -> go(AppScreen.Cache)
                        }
                    }
                }
            )
        }
        AppScreen.Toolbox -> {
            ToolboxScreen(
                onNavigateBack = { back() },
                onOpen = { id ->
                    when (id) {
                        "cleaner" -> go(AppScreen.CleanerHub)
                        "media_clean" -> go(AppScreen.MediaCleanHub)
                        "recycle" -> go(AppScreen.RecycleBin)
                        "system_cleaner" -> go(AppScreen.SystemCleaner)
                        "corpse" -> go(AppScreen.CorpseFinder)
                        "apk" -> go(AppScreen.ApkRemnants)
                        "dup_photos" -> go(AppScreen.DuplicatePhotos)
                        "file_analysis" -> go(AppScreen.FileAnalysis)
                        "data_usage" -> go(AppScreen.DataUsage)
                        "battery" -> go(AppScreen.BatteryInfo)
                        "security_scan" -> go(AppScreen.SecurityScan)
                        "app_control" -> go(AppScreen.AppControl)
                        "manage_apps" -> go(AppScreen.ManageApps)
                        "privacy" -> go(AppScreen.PrivacyHub)
                        "permissions" -> go(AppScreen.Permissions)
                        "permission_map" -> go(AppScreen.PermissionMap)
                        "browser_cache" -> go(AppScreen.BrowserCache)
                        "resource_usage" -> go(AppScreen.ResourceUsage)
                        "profiles" -> go(AppScreen.AppProfiles)
                        "good_night" -> go(AppScreen.GoodNight)
                        "app_lock" -> go(AppScreen.Security)
                    }
                }
            )
        }
        AppScreen.MediaCleanHub -> {
            MediaCleanHubScreen(
                application = app,
                onNavigateBack = { back() },
                onOpen = { id ->
                    when (id) {
                        "dup_photos", "similar" -> go(AppScreen.DuplicatePhotos)
                        "media_hub" -> go(AppScreen.MediaHub)
                        "video" -> go(AppScreen.VideoTools)
                        "duplicates" -> go(AppScreen.Duplicates)
                        "dup_videos" -> go(AppScreen.DuplicateVideos)
                        "compress" -> go(AppScreen.MediaCompress)
                        "blur" -> go(AppScreen.BlurPhotos)
                        "person_mismatch" -> go(AppScreen.PersonMismatch)
                        "file_analysis" -> go(AppScreen.FileAnalysis)
                    }
                }
            )
        }
        AppScreen.Permissions -> {
            PermissionScreen(
                onPermissionsGranted = { back() },
                onPermissionsDenied = { back() }
            )
        }
        AppScreen.RecycleBin -> {
            RecycleBinScreen(application = app, onNavigateBack = { back() })
        }
        AppScreen.Exclusions -> {
            ExclusionsScreen(application = app, onNavigateBack = { back() })
        }
        AppScreen.Duplicates -> {
            DuplicatesScreen(
                application = app,
                onNavigateBack = { back() },
                onRequirePro = { go(AppScreen.Pro) }
            )
        }
        AppScreen.SystemCleaner -> {
            SystemCleanerScreen(application = app, onNavigateBack = { back() })
        }
        AppScreen.CorpseFinder -> {
            CorpseFinderScreen(application = app, onNavigateBack = { back() })
        }
        AppScreen.MediaHub -> {
            MediaHubScreen(application = app, onNavigateBack = { back() })
        }
        AppScreen.MediaCompress -> {
            MediaCompressScreen(
                application = app,
                onNavigateBack = { back() },
                onRequirePro = { go(AppScreen.Pro) }
            )
        }
        AppScreen.AppControl -> {
            AppControlScreen(application = app, onNavigateBack = { back() })
        }
        AppScreen.VideoTools -> {
            VideoToolsScreen(application = app, onNavigateBack = { back() })
        }
        AppScreen.ApkRemnants -> {
            ApkRemnantScreen(application = app, onNavigateBack = { back() })
        }
        AppScreen.DuplicatePhotos -> {
            DuplicatePhotosScreen(application = app, onNavigateBack = { back() })
        }
        AppScreen.FileAnalysis -> {
            FileAnalysisScreen(
                application = app,
                onNavigateBack = { back() },
                onOpenLargeFiles = { go(AppScreen.LargeFiles) },
                onOpenDuplicatePhotos = { go(AppScreen.DuplicatePhotos) },
                onOpenDuplicates = { go(AppScreen.Duplicates) },
                onOpenMediaClean = { go(AppScreen.MediaCleanHub) },
                onOpenApk = { go(AppScreen.ApkRemnants) }
            )
        }
        AppScreen.DuplicateVideos -> {
            DuplicateVideosScreen(application = app, onNavigateBack = { back() })
        }
        AppScreen.Pro -> {
            if (com.makay.cleaner.util.ProManager.isProUiVisible()) {
                ProScreen(application = app, onNavigateBack = { back() })
            } else {
                LaunchedEffect(Unit) { back() }
            }
        }
        AppScreen.CleanerHub -> {
            CleanerHubScreen(
                application = app,
                onNavigateBack = { back() },
                onOpenCategory = { id ->
                    when (id) {
                        "cache" -> go(AppScreen.Cache)
                        "apk" -> go(AppScreen.ApkRemnants)
                        "residue" -> go(AppScreen.CorpseFinder)
                        "tmp" -> go(AppScreen.SystemCleaner)
                        "unused" -> go(AppScreen.LargeFiles)
                        else -> go(AppScreen.Cache)
                    }
                }
            )
        }
        AppScreen.SecurityScan -> {
            SecurityScanScreen(
                application = app,
                onNavigateBack = { back() },
                onNavigateToSettings = { go(AppScreen.Settings) },
                onOpenPermissions = { go(AppScreen.Permissions) },
                onOpenCleaner = { go(AppScreen.CleanerHub) },
                onOpenManageApps = { go(AppScreen.AppControl) }
            )
        }
        AppScreen.PrivacyHub -> {
            PrivacyHubScreen(
                onNavigateBack = { back() },
                onOpenAppLock = { go(AppScreen.Security) },
                onOpenPermissions = { go(AppScreen.Permissions) },
                onOpenPermissionMap = { go(AppScreen.PermissionMap) },
                onOpenBrowserCache = { go(AppScreen.BrowserCache) },
                onClearClipboard = {
                    Toast.makeText(context, clearClipboardNow(context), Toast.LENGTH_SHORT).show()
                }
            )
        }
        AppScreen.BatteryInfo -> {
            BatteryInfoScreen(
                onNavigateBack = { back() },
                onOpenResourceUsage = { go(AppScreen.ResourceUsage) },
                onOpenSoftHibernate = { go(AppScreen.SoftHibernate) },
                onOpenGoodNight = { go(AppScreen.GoodNight) }
            )
        }
        AppScreen.PermissionMap -> {
            PermissionMapScreen(onNavigateBack = { back() })
        }
        AppScreen.ResourceUsage -> {
            ResourceUsageScreen(
                onNavigateBack = { back() },
                onOpenSoftHibernate = { go(AppScreen.SoftHibernate) }
            )
        }
        AppScreen.SoftHibernate -> {
            SoftHibernateScreen(onNavigateBack = { back() })
        }
        AppScreen.BlurPhotos -> {
            BlurPhotosScreen(application = app, onNavigateBack = { back() })
        }
        AppScreen.PersonMismatch -> {
            PersonMismatchScreen(application = app, onNavigateBack = { back() })
        }
        AppScreen.AppProfiles -> {
            AppProfilesScreen(onNavigateBack = { back() })
        }
        AppScreen.GoodNight -> {
            GoodNightScreen(onNavigateBack = { back() })
        }
        AppScreen.BrowserCache -> {
            BrowserCacheScreen(onNavigateBack = { back() })
        }
        AppScreen.DataUsage -> {
            DataUsageScreen(onNavigateBack = { back() })
        }
        AppScreen.ManageApps -> {
            ManageAppsScreen(
                application = app,
                onNavigateBack = { back() },
                onOpenAppLock = { go(AppScreen.Security) },
                onOpenAppControl = { go(AppScreen.AppControl) },
                onOpenUninstall = { go(AppScreen.Uninstall) },
                onOpenProfiles = { go(AppScreen.AppProfiles) }
            )
        }
    }
}
