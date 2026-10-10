package com.makay.cleaner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.makay.cleaner.R
import com.makay.cleaner.data.AnalyticsRepository
import com.makay.cleaner.util.JunkScanHelper
import com.makay.cleaner.util.PermissionHelper
import com.makay.cleaner.util.PlatformManager
import com.makay.cleaner.util.UnusedAppsHelper
import com.makay.cleaner.util.rememberStoragePermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// Accent renkleri (tema ile uyumlu vurgu)
private val HomeOrange = Color(0xFFFF9F0A)
private val HomeGreen = Color(0xFF30D158)
private val HomeCyan = Color(0xFF64D2FF)
private val HomeRed = Color(0xFFFF453A)
private val HomeWhatsApp = Color(0xFF25D366)

@Composable
private fun homeIsLight() = MaterialTheme.colorScheme.background.luminance() > 0.45f

@Composable
private fun homeBg() = if (homeIsLight()) Color(0xFFF4F4F6) else Color(0xFF000000)

@Composable
private fun homeCard() = if (homeIsLight()) Color(0xFFFFFFFF) else Color(0xFF2C2C2E)

@Composable
private fun homeTextPrimary() = if (homeIsLight()) Color(0xFF111111) else Color(0xFFF2F2F7)

@Composable
private fun homeTextSecondary() = if (homeIsLight()) Color(0xFF3A3A3C) else Color(0xFFD1D1D6)

@Composable
private fun homeAccent() = MaterialTheme.colorScheme.primary

@Composable
private fun homeOutline() = if (homeIsLight()) Color(0xFF1C1C1E) else Color(0xFFAEAEB2)

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
    onNavigateToDuplicatePhotos: () -> Unit = {},
    onNavigateToMediaClean: () -> Unit = {},
    onNavigateToPermissions: () -> Unit = {},
    onNavigateToAppControl: () -> Unit = {},
    onNavigateToCleanerHub: () -> Unit = {},
    onNavigateToSecurityScan: () -> Unit = {},
    onNavigateToBattery: () -> Unit = {},
    onNavigateToDataUsage: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToManageApps: () -> Unit = {}
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

    LaunchedEffect(cleanMessage) {
        if (cleanMessage == null) return@LaunchedEffect
        delay(5_000)
        cleanMessage = null
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
    val healthScore = JunkScanHelper.optimizationScore(junkBytes, usagePercent, unusedApps)
    val statusSubtitle = when {
        optimizing -> "Optimizasyon çalışıyor"
        !scanned || uiState.isLoading -> "Cihaz durumu kontrol ediliyor"
        !hasPermission -> "Devam etmek için depolama izni gerekli"
        healthScore >= 100 && junkBytes <= 0L -> "Cihaz temiz görünüyor"
        junkBytes > 0L -> "${formatBytes(junkBytes)} silinebilir alan"
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
        }
    }

    val isTv = PlatformManager.rememberIsTvMode()
    val homeScroll = rememberScrollState()
    LaunchedEffect(isTv) {
        if (isTv) homeScroll.scrollTo(0)
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
        // Başlık + ayarlar kaydırılmaz — TV açılışında her zaman görünür
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
                modifier = if (isTv) Modifier.tvRequestInitialFocus() else Modifier
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
                    label = if (junkBytes > 0L) "Kalan ${formatBytes(junkBytes)}" else "Silinemeyen önbellek",
                    onWhy = { whyDialog = true }
                )
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
                                    "Sistem sayacı yaklaşık ${formatBytes(lockedBytes)} ek önbellek gösterir. Bu bir dosya listesi değil. Xiaomi ve Samsung bunu sistem uygulaması oldukları için silebilir; sideload uygulama silemez. Skor bu alanı saymaz.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { whyDialog = false }) { Text("Tamam") }
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
                    }
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

            val batterySub = remember(context) {
                try {
                    val bm = context.getSystemService(android.content.Context.BATTERY_SERVICE)
                        as android.os.BatteryManager
                    val lvl = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    "%$lvl şarj"
                } catch (_: Exception) {
                    "Pil özeti"
                }
            }

            HomeCategoryCard(
                title = "Temizlik",
                subtitle = if (junkBytes > 0L) "${formatBytes(junkBytes)} silinebilir" else "Önbellek, junk, çöp kutusu",
                accent = HomeOrange,
                iconRes = R.drawable.ic_hub_cleaner,
                expanded = expandedCategory == "clean",
                onToggle = { expandedCategory = if (expandedCategory == "clean") null else "clean" },
                actions = listOf(
                    "Ayrıntılı temizleyici" to onNavigateToCleanerHub,
                    "Önbellek" to onNavigateToCache,
                    "Büyük dosya" to onNavigateToLargeFiles,
                    "Çöp kutusu" to onNavigateToRecycleBin
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

@Composable
private fun HomeWhyRow(label: String, onWhy: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = homeTextSecondary(),
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        TvIconButton(onClick = onWhy, idleContent = homeTextPrimary()) {
            Text("?", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
private fun CompactStatusRow(title: String, action: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .tvClickable(corner = 12.dp, onFocused = { focused = it }, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            color = homeTextPrimary(),
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            action,
            color = homeAccent(),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun HomeInsightStrip(
    storagePercent: Float,
    junkBytes: Long,
    unusedApps: Int,
    weekSaved: Long,
    topCategory: String?,
    hasPermission: Boolean,
    onStorage: () -> Unit,
    onUnused: () -> Unit,
    onWeek: () -> Unit
) {
    val chips = buildList {
        if (hasPermission) {
            add(
                Triple(
                    if (storagePercent > 0f) "%${storagePercent.roundToInt()} dolu" else "Depolama",
                    topCategory ?: "Kırılım",
                    onStorage
                )
            )
        }
        if (junkBytes > 0L) {
            add(Triple(formatBytes(junkBytes), "Silinebilir", onStorage))
        }
        if (unusedApps > 0) {
            add(Triple("$unusedApps uygulama", "Kullanılmayan", onUnused))
        }
        if (weekSaved > 0L) {
            add(Triple(formatBytes(weekSaved), "Son 7 gün", onWeek))
        }
    }
    if (chips.isEmpty()) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chips.take(4).forEach { (value, label, click) ->
            val (src, focused) = rememberTvInteraction()
            Card(
                onClick = click,
                interactionSource = src,
                modifier = Modifier
                    .weight(1f)
                    .makayFocus(corner = 12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = tvCardColors(focused, homeCard())
            ) {
                Column(
                    Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        value,
                        color = homeTextPrimary(),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        label,
                        color = homeTextSecondary(),
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeStorageCompact(
    categories: List<com.makay.cleaner.domain.model.StorageCategory>,
    catTotal: Long,
    isLoading: Boolean,
    hasPermission: Boolean,
    onCategoryClick: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = homeCard())
    ) {
        Column(Modifier.padding(14.dp)) {
            when {
                isLoading && categories.isEmpty() -> {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = homeAccent()
                    )
                }
                catTotal <= 0L -> {
                    Text(
                        if (!hasPermission) "Depolama izni sonrası kırılım görünür."
                        else "Henüz anlamlı kırılım yok.",
                        color = homeTextSecondary(),
                        fontSize = 12.sp
                    )
                }
                else -> {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StorageBreakdownPie(
                            categories = categories.filter { it.sizeBytes > 0 }.ifEmpty { categories },
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            categories.filter { it.sizeBytes > 0 }.take(4).forEach { cat ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onCategoryClick(cat.id.ifBlank { cat.name }) }
                                        .padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(cat.name, color = homeTextSecondary(), fontSize = 12.sp, maxLines = 1)
                                    Text(cat.sizeFormatted, color = homeTextPrimary(), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeCategoryCard(
    title: String,
    subtitle: String,
    accent: Color,
    iconRes: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    actions: List<Pair<String, () -> Unit>>
) {
    val (interaction, focused) = rememberTvInteraction()
    Card(
        onClick = onToggle,
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .makayFocus(corner = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = tvCardColors(focused, homeCard())
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = title,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = homeTextPrimary(),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Text(
                        subtitle,
                        color = homeTextSecondary(),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    if (expanded) "▲" else "▼",
                    color = homeTextSecondary(),
                    fontSize = 12.sp
                )
            }
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                actions.chunked(2).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { (label, click) ->
                            val (chipSrc, _) = rememberTvInteraction()
                            OutlinedButton(
                                onClick = click,
                                interactionSource = chipSrc,
                                modifier = Modifier
                                    .weight(1f)
                                    .makayFocus(corner = 12.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, homeOutline().copy(alpha = 0.35f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = homeTextPrimary()
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(label, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun HomeScoreHero(
    score: Int,
    subtitle: String,
    isLoading: Boolean,
    buttonEnabled: Boolean = true,
    buttonLabel: String = "Optimizasyon",
    onOptimizeClick: () -> Unit
) {
    val accent = when {
        score >= 100 -> HomeGreen
        score >= 80 -> homeAccent()
        else -> HomeOrange
    }
    val textPrimary = homeTextPrimary()
    val textSecondary = homeTextSecondary()
    val track = if (homeIsLight()) Color(0xFFD8D8DC) else Color(0xFF3A3A3C)
    val isTv = PlatformManager.rememberIsTvMode()
    val ring = if (isTv) 156.dp else 188.dp
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(ring),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 12.dp.toPx()
                val pad = stroke / 2
                drawArc(
                    color = track,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(pad, pad),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * (score / 100f).coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(pad, pad),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$score",
                    color = textPrimary,
                    fontSize = if (isTv) 42.sp else 52.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp
                )
                Text(
                    text = if (score >= 100) "İyi durumda" else "Skor",
                    color = textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.width(64.dp).padding(top = 8.dp),
                        color = accent,
                        trackColor = track
                    )
                } else {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = subtitle,
                        color = textSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        maxLines = 2
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        val (optInteraction, optFocused) = rememberTvInteraction()
        Button(
            onClick = onOptimizeClick,
            enabled = buttonEnabled,
            interactionSource = optInteraction,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .makayFocus(),
            shape = RoundedCornerShape(14.dp),
            colors = tvButtonColors(
                focused = optFocused,
                idle = if (score >= 100) homeAccent() else HomeOrange
            )
        ) {
            Text(
                text = buttonLabel,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun StorageBreakdownPie(
    categories: List<com.makay.cleaner.domain.model.StorageCategory>,
    modifier: Modifier = Modifier
) {
    val total = categories.sumOf { it.sizeBytes }.coerceAtLeast(1L).toFloat()
    val colors = categories.map { Color(it.color) }
    val holeColor = MaterialTheme.colorScheme.background
    Canvas(modifier = modifier) {
        val diameter = minOf(size.width, size.height) * 0.9f
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val pieSize = Size(diameter, diameter)
        var start = -90f
        categories.forEachIndexed { index, cat ->
            val sweep = (cat.sizeBytes / total) * 360f
            drawArc(
                color = colors.getOrElse(index) { HomeOrange },
                startAngle = start,
                sweepAngle = sweep.coerceAtLeast(1.5f),
                useCenter = true,
                topLeft = topLeft,
                size = pieSize
            )
            start += sweep
        }
        val hole = diameter * 0.52f
        drawCircle(
            color = holeColor,
            radius = hole / 2f,
            center = Offset(size.width / 2f, size.height / 2f)
        )
    }
}

@Composable
fun StorageLoadingContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(homeBg()),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = homeAccent())
    }
}
