package com.makay.cleaner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.makay.cleaner.R
import com.makay.cleaner.data.AnalyticsRepository
import com.makay.cleaner.util.BatteryStatsHelper
import com.makay.cleaner.util.JunkScanHelper
import com.makay.cleaner.util.PermissionHelper
import com.makay.cleaner.util.PlatformManager
import com.makay.cleaner.util.UnusedAppsHelper
import com.makay.cleaner.util.rememberStoragePermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(
    viewModel: StorageViewModel = viewModel(),
    onNavigateToCache: () -> Unit = {},
    onNavigateToLargeFiles: () -> Unit = {},
    onNavigateToUninstall: () -> Unit = {},
    onNavigateToSocialMedia: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToAnalytics: () -> Unit = {},
    onNavigateToSuggestions: () -> Unit = {},
    onNavigateToToolbox: () -> Unit = {},
    onNavigateToRecycleBin: () -> Unit = {},
    onNavigateToExclusions: () -> Unit = {},
    onNavigateToDuplicatePhotos: () -> Unit = {},
    onNavigateToPersonMismatch: () -> Unit = {},
    onNavigateToMediaClean: () -> Unit = {},
    onNavigateToPermissions: () -> Unit = {},
    onNavigateToAppControl: () -> Unit = {},
    onNavigateToCleanerHub: () -> Unit = {},
    onNavigateToSecurityScan: () -> Unit = {},
    onNavigateToBattery: () -> Unit = {},
    onNavigateToDataUsage: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToManageApps: () -> Unit = {},
    onNavigateToFileAnalysis: () -> Unit = {},
    onLightClean: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val hasPermission = rememberStoragePermissionState()
    var cleanMessage by remember { mutableStateOf<String?>(null) }
    var junkBytes by remember { mutableLongStateOf(0L) }
    var lockedBytes by remember { mutableLongStateOf(0L) }
    var unusedApps by remember { mutableIntStateOf(0) }
    var optimizing by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf(false) }
    var expandedCategory by remember { mutableStateOf<String?>(null) }
    var showWhy by remember { mutableStateOf(false) }
    var whyDialog by remember { mutableStateOf(false) }
    var confirmOptimization by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val settingsFocus = remember { FocusRequester() }
    var tvFocusEpoch by remember { mutableIntStateOf(0) }
    var batteryPercent by remember {
        mutableIntStateOf(BatteryStatsHelper.readPercent(context))
    }

    LaunchedEffect(cleanMessage) {
        if (cleanMessage == null) return@LaunchedEffect
        delay(5_000)
        cleanMessage = null
    }

    DisposableEffect(Unit) {
        val filter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, intent: android.content.Intent?) {
                batteryPercent = BatteryStatsHelper.readPercent(context)
            }
        }
        context.registerReceiver(receiver, filter)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    // Açılışta junk + öneri listesi; skor yalnızca junk’a bağlı
    LaunchedEffect(hasPermission) {
        if (!hasPermission) {
            junkBytes = 0L
            lockedBytes = 0L
            unusedApps = 0
            scanned = true
            return@LaunchedEffect
        }
        viewModel.refresh()
        val (junk, locked, unused) = withContext(Dispatchers.IO) {
            val report = runCatching { JunkScanHelper.scan(context) }.getOrNull()
            val u = runCatching { UnusedAppsHelper.getUnusedApps(context).size }.getOrDefault(0)
            Triple(report?.cleanableBytes ?: 0L, report?.lockedBytes ?: 0L, u)
        }
        junkBytes = junk
        lockedBytes = locked
        unusedApps = unused
        scanned = true
    }

    val usagePercent = uiState.storageInfo?.usagePercent ?: 0f
    val healthScore = JunkScanHelper.optimizationScore(junkBytes)
    val statusSubtitle = when {
        optimizing -> "Optimizasyon çalışıyor"
        !scanned || uiState.isLoading -> "Cihaz durumu kontrol ediliyor"
        !hasPermission -> "Devam etmek için depolama izni gerekli"
        healthScore >= 100 && junkBytes <= 0L -> "Cihaz temiz görünüyor"
        junkBytes > 0L -> "${formatBytes(junkBytes)} silinebilir alan"
        lockedBytes > 0L -> "Silinemeyenler hariç tutulanlarda"
        else -> "Hızlı optimizasyon önerilir"
    }

    fun runOptimization() {
        if (!hasPermission) {
            onNavigateToPermissions()
            return
        }
        scope.launch {
            optimizing = true
            cleanMessage = null
            val result = withContext(Dispatchers.IO) {
                JunkScanHelper.cleanAll(context)
            }
            val (junk, locked, unused) = withContext(Dispatchers.IO) {
                val report = JunkScanHelper.scan(context)
                val u = UnusedAppsHelper.getUnusedApps(context).size
                if (PlatformManager.isTvMode(context)) {
                    val score = JunkScanHelper.optimizationScore(report.cleanableBytes)
                    com.makay.cleaner.util.TvRecommendationManager.publishRecommendation(
                        context,
                        report.cleanableBytes,
                        score
                    )
                }
                Triple(report.cleanableBytes, report.lockedBytes, u)
            }
            junkBytes = junk
            lockedBytes = locked
            unusedApps = unused
            scanned = true
            viewModel.refresh()
            optimizing = false
            showWhy = junk > 0L
            val removed = result.spaceSaved
            cleanMessage = if (removed > 0L) "${formatBytes(removed)} silindi" else null
            // TV: tarama bitince sayfa başı + Ayarlar odağında kal
            tvFocusEpoch++
        }
    }

    val isTv = PlatformManager.rememberIsTvMode()
    val homeScroll = rememberScrollState()
    LaunchedEffect(isTv, tvFocusEpoch) {
        if (!isTv) return@LaunchedEffect
        homeScroll.scrollTo(0)
        requestTvFocusSafe(settingsFocus)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(homeBg())
            .then(
                if (isTv) Modifier.padding(horizontal = 48.dp, vertical = 28.dp)
                else Modifier.statusBarsPadding().padding(horizontal = 16.dp)
            )
    ) {
        // Başlık + ayarlar kaydırılmaz — TV açılışında / tarama sonrası her zaman görünür
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (isTv) 0.dp else 4.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Makay Cleaner",
                color = homeTextPrimary(),
                fontWeight = FontWeight.SemiBold,
                fontSize = if (isTv) 20.sp else 18.sp,
                letterSpacing = 0.2.sp
            )
            TvIconButton(
                onClick = onNavigateToSettings,
                idleContent = homeTextPrimary(),
                modifier = if (isTv) Modifier.focusRequester(settingsFocus) else Modifier
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Ayarlar"
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(homeScroll)
                .padding(bottom = if (isTv) 20.dp else 28.dp)
        ) {
            // Skor halkası + büyük Optimizasyon / Temizle CTA (halka dışında — kesilmez)
            HomeScoreHero(
                score = healthScore,
                subtitle = statusSubtitle,
                isLoading = uiState.isLoading || optimizing,
                buttonEnabled = !optimizing,
                buttonLabel = when {
                    optimizing -> "Optimize ediliyor…"
                    !scanned -> "Kontrol ediliyor…"
                    healthScore >= 100 -> "Yeniden tara"
                    else -> "Optimizasyon"
                },
                onOptimizeClick = {
                    if (hasPermission) confirmOptimization = true else runOptimization()
                }
            )

            if (onLightClean != null) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        if (!hasPermission) onNavigateToPermissions()
                        else onLightClean()
                    },
                    enabled = !optimizing,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, homeOutline().copy(alpha = 0.45f))
                ) {
                    Text(
                        "Sadece önbellek temizle",
                        color = homeTextPrimary(),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            if (!hasPermission) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = homeCard()),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, homeOutline().copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CompactStatusRow(
                        title = "Depolama izni gerekli",
                        action = "İzin ver",
                        onClick = onNavigateToPermissions
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            cleanMessage?.let { msg ->
                Text(
                    text = msg,
                    color = if (homeIsLight()) Color(0xFF1B7A35) else HomeGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            if (showWhy && (junkBytes > 0L || lockedBytes > 0L)) {
                HomeWhyRow(
                    label = if (junkBytes > 0L) "Kalan ${formatBytes(junkBytes)}" else "Hariç tutulanlar",
                    onWhy = { whyDialog = true }
                )
                Spacer(Modifier.height(10.dp))
            }
            if (hasPermission && scanned) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = homeCard()),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, homeOutline().copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CompactStatusRow(
                        title = if (lockedBytes > 0L)
                            "Hariç tutulanlar · ~${formatBytes(lockedBytes)} kilitli"
                        else
                            "Hariç tutulanlar",
                        action = "Görüntüle",
                        onClick = onNavigateToExclusions
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            if (whyDialog) {
                AlertDialog(
                    onDismissRequest = { whyDialog = false },
                    title = { Text("Neden silinmedi?") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (junkBytes > 0L) {
                                Text(
                                    "Kalan ${formatBytes(junkBytes)} diskte duruyor. Silme denendi, Android dosyayı yerinde bıraktı.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "Bunlar sistem klasörü (/system, /vendor) değil. Başka uygulamanın özel alanı veya yazmaya kapalı önbellek.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "Silmek için: Ayarlar → Uygulamalar → ilgili uygulama → Depolama → Önbelleği temizle. Makay bu klasöre giremez.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            if (lockedBytes > 0L) {
                                Text(
                                    "Sistem sayacı yaklaşık ${formatBytes(lockedBytes)} ek önbellek gösterir. Bu alanlar Hariç tutulanlar listesinde. Skor bunları saymaz — silinebilir junk yoksa skor %100’dür.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            whyDialog = false
                            onNavigateToExclusions()
                        }) { Text("Hariç tutulanlar") }
                    },
                    dismissButton = {
                        TextButton(onClick = { whyDialog = false }) { Text("Kapat") }
                    }
                )
            }
            if (confirmOptimization) {
                AlertDialog(
                    onDismissRequest = { confirmOptimization = false },
                    title = { Text("Optimizasyon başlatılsın mı?") },
                    text = {
                        Text("Bulunan gereksiz dosyalar çöp kutusuna taşınır ve 48 saat içinde geri yüklenebilir. Uygulama önbellekleri doğrudan temizlenir.")
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmOptimization = false
                            runOptimization()
                        }) { Text("Temizle") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmOptimization = false }) { Text("Vazgeç") }
                    }
                )
            }

            val cats = uiState.categories.take(5)
            val catTotal = cats.sumOf { it.sizeBytes }
            val weekUsage = remember {
                AnalyticsRepository(context.applicationContext).getLast7DaysUsage()
            }
            val weekSaved = weekUsage.sumOf { it.spaceSaved }

            HomeInsightStrip(
                storagePercent = usagePercent,
                junkBytes = junkBytes,
                unusedApps = unusedApps,
                weekSaved = weekSaved,
                topCategory = cats.firstOrNull { it.sizeBytes > 0 }?.let { "${it.name} ${it.sizeFormatted}" },
                hasPermission = hasPermission,
                onStorage = {
                    expandedCategory = if (expandedCategory == "storage") null else "storage"
                },
                onUnused = onNavigateToAppControl,
                onWeek = onNavigateToAnalytics
            )

            if (expandedCategory == "storage") {
                Spacer(Modifier.height(8.dp))
                HomeStorageCompact(
                    categories = cats,
                    catTotal = catTotal,
                    isLoading = uiState.isLoading,
                    hasPermission = hasPermission,
                    onCategoryClick = { id ->
                        when (id) {
                            "cache" -> onNavigateToCache()
                            "large" -> onNavigateToLargeFiles()
                            "media" -> onNavigateToMediaClean()
                            "docs" -> onNavigateToLargeFiles()
                            else -> onNavigateToCleanerHub()
                        }
                    },
                    onOpenDetailedAnalysis = onNavigateToFileAnalysis
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = "Kategoriler",
                color = homeTextPrimary(),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            val batterySub = "%$batteryPercent şarj"

            HomeCategoryCard(
                title = "Temizlik",
                subtitle = if (junkBytes > 0L) "${formatBytes(junkBytes)} silinebilir" else "Önbellek, junk, çöp kutusu",
                accent = HomeOrange,
                iconRes = R.drawable.ic_hub_cleaner,
                expanded = expandedCategory == "clean",
                onToggle = { expandedCategory = if (expandedCategory == "clean") null else "clean" },
                actions = listOf(
                    "Dosya analizi" to onNavigateToFileAnalysis,
                    "Ayrıntılı temizleyici" to onNavigateToCleanerHub,
                    "Önbellek" to onNavigateToCache,
                    "Büyük dosya" to onNavigateToLargeFiles,
                    "Çöp kutusu" to onNavigateToRecycleBin,
                    "Hariç tutulanlar" to onNavigateToExclusions
                )
            )
            Spacer(Modifier.height(10.dp))
            HomeCategoryCard(
                title = "Medya",
                subtitle = "Fotoğraf, sosyal, yinelenenler",
                accent = HomeCyan,
                iconRes = R.drawable.ic_hub_data,
                expanded = expandedCategory == "media",
                onToggle = { expandedCategory = if (expandedCategory == "media") null else "media" },
                actions = listOf(
                    "Medya temizliği" to onNavigateToMediaClean,
                    "Sosyal medya" to onNavigateToSocialMedia,
                    "Yinelenen foto" to onNavigateToDuplicatePhotos,
                    "Farklı kişi" to onNavigateToPersonMismatch,
                    "Araç kutusu" to onNavigateToToolbox
                )
            )
            Spacer(Modifier.height(10.dp))
            HomeCategoryCard(
                title = "Uygulamalar",
                subtitle = if (unusedApps > 0) "$unusedApps kullanılmayan · yönet" else "Yönet, kaldır, kilitle",
                accent = Color(0xFF8E8E93),
                iconRes = R.drawable.ic_hub_apps,
                expanded = expandedCategory == "apps",
                onToggle = { expandedCategory = if (expandedCategory == "apps") null else "apps" },
                actions = listOf(
                    "Kullanılmayan" to onNavigateToAppControl,
                    "Uygulamaları yönet" to onNavigateToManageApps,
                    "Uygulama kaldır" to onNavigateToUninstall,
                    "Gizlilik" to onNavigateToPrivacy
                )
            )
            Spacer(Modifier.height(10.dp))
            HomeCategoryCard(
                title = "Cihaz",
                subtitle = "$batterySub · güvenlik ve istatistik",
                accent = HomeGreen,
                iconRes = R.drawable.ic_hub_security,
                expanded = expandedCategory == "device",
                onToggle = { expandedCategory = if (expandedCategory == "device") null else "device" },
                actions = listOf(
                    "Güvenlik özeti" to onNavigateToSecurityScan,
                    "Pil" to onNavigateToBattery,
                    "Veri kullanımı" to onNavigateToDataUsage,
                    "İstatistik" to onNavigateToAnalytics
                )
            )
        }
    }
}

