package com.makay.cleaner.ui

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.makay.cleaner.util.JunkScanHelper
import com.makay.cleaner.util.PermissionHelper
import com.makay.cleaner.util.UnusedAppsHelper
import com.makay.cleaner.util.rememberStoragePermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// Accent renkleri (tema ile uyumlu vurgu)
private val HomeOrange = Color(0xFFFF9F0A)
private val HomeGreen = Color(0xFF30D158)
private val HomePurple = Color(0xFFBF5AF2)
private val HomeCyan = Color(0xFF64D2FF)
private val HomeRed = Color(0xFFFF453A)
private val HomeWhatsApp = Color(0xFF25D366)

@Composable
private fun homeBg() = MaterialTheme.colorScheme.background

@Composable
private fun homeCard() = MaterialTheme.colorScheme.surfaceVariant

@Composable
private fun homeTextPrimary() = MaterialTheme.colorScheme.onBackground

@Composable
private fun homeTextSecondary() = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
private fun homeAccent() = MaterialTheme.colorScheme.primary

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
    var unusedApps by remember { mutableIntStateOf(0) }
    var optimizing by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Açılışta tarama: junk + kullanılmayan uygulama → skor 100'de kilitlenmez
    LaunchedEffect(hasPermission, uiState.storageInfo?.usagePercent) {
        val (junk, unused) = withContext(Dispatchers.IO) {
            val j = runCatching { JunkScanHelper.scan(context).totalBytes }.getOrDefault(0L)
            val u = runCatching { UnusedAppsHelper.getUnusedApps(context).size }.getOrDefault(0)
            j to u
        }
        junkBytes = junk
        unusedApps = unused
        scanned = true
    }

    val usagePercent = uiState.storageInfo?.usagePercent ?: 0f
    val healthScore = JunkScanHelper.optimizationScore(junkBytes, usagePercent, unusedApps)
    val statusSubtitle = when {
        optimizing -> "Taranıyor ve temizleniyor…"
        !scanned || uiState.isLoading -> "Cihaz kontrol ediliyor…"
        !hasPermission -> "Depolama izni gerekli"
        healthScore >= 100 -> "Cihazınız iyi durumda"
        else -> {
            val n = listOf(junkBytes > 0L, unusedApps > 0, usagePercent >= 75f).count { it }
            if (n <= 0) "Birkaç öge optimize edilebilir"
            else "$n öge optimize edilebilir"
        }
    }

    fun runOptimization() {
        if (!hasPermission) {
            onNavigateToPermissions()
            return
        }
        scope.launch {
            optimizing = true
            val result = withContext(Dispatchers.IO) {
                if (junkBytes > 0L) JunkScanHelper.cleanAll(context)
                else JunkScanHelper.CleanResult(0, 0, 0)
            }
            val (junk, unused) = withContext(Dispatchers.IO) {
                val j = JunkScanHelper.scan(context).totalBytes
                val u = UnusedAppsHelper.getUnusedApps(context).size
                j to u
            }
            junkBytes = junk
            unusedApps = unused
            scanned = true
            viewModel.refresh()
            optimizing = false
            val score = JunkScanHelper.optimizationScore(
                junk, uiState.storageInfo?.usagePercent ?: usagePercent, unused
            )
            cleanMessage = when {
                score >= 100 -> "Optimizasyon tamam — önbellek cezası kalktı, skor %100"
                junk == 0L ->
                    "Önbellek cezası kalktı. Skor %$score — kalan: ${buildHealthHint(junk, unused, usagePercent)}"
                else ->
                    "Kısmen temizlendi (${formatBytes(result.spaceSaved)}). Skor %$score — ${buildHealthHint(junk, unused, usagePercent)}"
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(homeBg())
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp)
        ) {
            // Üst bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Makay Cleaner",
                    color = homeTextPrimary(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Ayarlar",
                        tint = homeTextPrimary()
                    )
                }
            }

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
                    junkBytes > 0 -> "Optimizasyon"
                    else -> "Optimizasyon"
                },
                onOptimizeClick = { runOptimization() }
            )

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onNavigateToCleanerHub,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(50)
            ) {
                Text("Ayrıntılı temizleyici")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Yalnızca depolama izni (kullanım erişimi istenmez — sideload uyarılarına yol açmaz)
            if (!hasPermission) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = homeCard()),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CompactStatusRow(
                        title = "Depolama izni gerekli",
                        action = "İzin ver",
                        onClick = onNavigateToPermissions
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            } else if (scanned && healthScore < 100) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = homeCard()),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(
                            "Optimize edilebilir",
                            color = homeTextPrimary(),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                        if (junkBytes > 0L) {
                            CompactStatusRow(
                                title = "${formatBytes(junkBytes)} temizlenebilir önbellek",
                                action = "Temizle",
                                onClick = { runOptimization() }
                            )
                        }
                        if (unusedApps > 0) {
                            CompactStatusRow(
                                title = "$unusedApps kullanılmayan uygulama",
                                action = "Yönet",
                                onClick = onNavigateToManageApps
                            )
                        }
                        if (usagePercent >= 75f) {
                            CompactStatusRow(
                                title = "Depolama %${usagePercent.roundToInt()} dolu",
                                action = "Ayrıntı",
                                onClick = onNavigateToCleanerHub
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            cleanMessage?.let {
                Text(
                    text = it,
                    color = HomeGreen,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Depolama kırılımı + pasta
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Depolama kırılımı",
                color = homeTextPrimary(),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            val cats = uiState.categories.take(5)
            val catTotal = cats.sumOf { it.sizeBytes }
            when {
                uiState.isLoading && cats.isEmpty() -> {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        color = homeAccent()
                    )
                }
                catTotal <= 0L -> {
                    Text(
                        if (!hasPermission)
                            "Medya ve junk boyutları için depolama izni verin."
                        else
                            "Henüz anlamlı kırılım yok — Optimizasyon ile junk taraması yapın.",
                        color = homeTextSecondary(),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                else -> {
                    StorageBreakdownPie(
                        categories = cats.filter { it.sizeBytes > 0 }.ifEmpty { cats },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .padding(bottom = 10.dp)
                    )
                    cats.forEach { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    when {
                                        cat.name.contains("Önbellek", ignoreCase = true) ->
                                            onNavigateToCache()
                                        cat.name.contains("Büyük", ignoreCase = true) ->
                                            onNavigateToLargeFiles()
                                        cat.name.contains("Medya", ignoreCase = true) ->
                                            onNavigateToMediaClean()
                                        cat.name.contains("Belge", ignoreCase = true) ->
                                            onNavigateToLargeFiles()
                                        else -> onNavigateToToolbox()
                                    }
                                }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(cat.color.toLong() and 0xFFFFFFFFL))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(cat.name, color = homeTextSecondary(), fontSize = 13.sp)
                            }
                            Text(cat.sizeFormatted, color = homeTextPrimary(), fontSize = 13.sp)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // 2x3 özellik kartları — referans Security ana sayfa
            val storageLabel = uiState.storageInfo?.let {
                "%${it.usagePercent.roundToInt()} depolama alanı"
            } ?: "Depolama analizi"

            val batterySub = remember(context) {
                try {
                    val bm = context.getSystemService(android.content.Context.BATTERY_SERVICE)
                        as android.os.BatteryManager
                    val lvl = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    "%$lvl şarj · sistemde yönet"
                } catch (_: Exception) {
                    "Sistem pil ayarları"
                }
            }
            val dataSub = remember(context) {
                val mobile = android.net.TrafficStats.getMobileRxBytes() +
                    android.net.TrafficStats.getMobileTxBytes()
                if (mobile > 0) "${formatBytes(mobile)} mobil · sistemde yönet"
                else "Sayaç özeti · sistemde yönet"
            }

            HomeFeatureGrid(
                items = listOf(
                    HomeFeature(
                        title = "Temizleyici",
                        subtitle = storageLabel,
                        iconRes = R.drawable.ic_hub_cleaner,
                        accent = HomeOrange,
                        onClick = onNavigateToCleanerHub
                    ),
                    HomeFeature(
                        title = "Güvenlik taraması",
                        subtitle = "Virüs motoru yok · sağlık özeti",
                        iconRes = R.drawable.ic_hub_security,
                        accent = HomeGreen,
                        onClick = onNavigateToSecurityScan
                    ),
                    HomeFeature(
                        title = "Pil",
                        subtitle = batterySub,
                        iconRes = R.drawable.ic_hub_battery,
                        accent = HomeGreen,
                        onClick = onNavigateToBattery
                    ),
                    HomeFeature(
                        title = "Veri kullanımı",
                        subtitle = dataSub,
                        iconRes = R.drawable.ic_hub_data,
                        accent = HomeCyan,
                        onClick = onNavigateToDataUsage
                    ),
                    HomeFeature(
                        title = "Gizlilik koruması",
                        subtitle = "Kilitle ve izinleri yönet",
                        iconRes = R.drawable.ic_hub_privacy,
                        accent = HomeCyan,
                        onClick = onNavigateToPrivacy
                    ),
                    HomeFeature(
                        title = "Uygulamaları yönet",
                        subtitle = "Ara, kilitle, kaldır",
                        iconRes = R.drawable.ic_hub_apps,
                        accent = Color(0xFF8E8E93),
                        onClick = onNavigateToManageApps
                    )
                )
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Ortak özellikler",
                color = homeTextPrimary(),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 14.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                HomeQuickAction(iconRes = R.drawable.ic_hub_privacy, label = "Gizlilik", onClick = onNavigateToPrivacy)
                HomeQuickAction(iconRes = R.drawable.ic_hub_apps, label = "Araçlar", onClick = onNavigateToToolbox)
                HomeQuickAction(iconRes = R.drawable.ic_hub_cleaner, label = "Çöp kutusu", onClick = onNavigateToRecycleBin)
                HomeQuickAction(iconRes = R.drawable.ic_hub_data, label = "Medya", onClick = onNavigateToMediaClean)
            }

            Spacer(modifier = Modifier.height(20.dp))

            HomeToolboxCard(
                onCache = onNavigateToCleanerHub,
                onSocial = onNavigateToSocialMedia,
                onLarge = onNavigateToLargeFiles,
                onUninstall = onNavigateToUninstall,
                onStats = onNavigateToAnalytics,
                onSuggestions = onNavigateToToolbox
            )
        }
    }
}

@Composable
private fun CompactStatusRow(title: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = homeTextPrimary(), fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(action, color = homeAccent(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun buildHealthHint(junkBytes: Long, unusedApps: Int, usagePercent: Float): String {
    val parts = mutableListOf<String>()
    if (junkBytes > 0L) parts += "${formatBytes(junkBytes)} önbellek"
    if (unusedApps > 0) parts += "$unusedApps uygulama"
    if (usagePercent >= 75f) parts += "depolama %${usagePercent.roundToInt()}"
    return if (parts.isEmpty()) "Birkaç öge optimize edilebilir"
    else parts.joinToString(" · ")
}

private data class HomeFeature(
    val title: String,
    val subtitle: String,
    val iconRes: Int,
    val accent: Color,
    val onClick: () -> Unit
)

@Composable
private fun HomeScoreHero(
    score: Int,
    subtitle: String,
    isLoading: Boolean,
    buttonEnabled: Boolean = true,
    buttonLabel: String = "Optimizasyon",
    onOptimizeClick: () -> Unit
) {
    val accent = homeAccent()
    val textPrimary = homeTextPrimary()
    val textSecondary = homeTextSecondary()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 14.dp.toPx()
                val pad = stroke / 2
                drawArc(
                    color = Color(0xFF2C2C2E),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(pad, pad),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(accent, Color(0xFF5AC8FA), accent)
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * (score / 100f),
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
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold
                )
                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.width(72.dp).padding(top = 4.dp),
                        color = accent,
                        trackColor = Color(0xFF2C2C2E)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = textSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp),
                    maxLines = 2
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        // CTA halka dışında — kesilmez / tarama sırasında da tıklanabilir
        Button(
            onClick = onOptimizeClick,
            enabled = buttonEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = HomeOrange,
                contentColor = Color.White,
                disabledContainerColor = HomeOrange.copy(alpha = 0.5f)
            )
        ) {
            Text(
                text = buttonLabel,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun HomeFeatureGrid(items: List<HomeFeature>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { feature ->
                    HomeFeatureCard(
                        feature = feature,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HomeFeatureCard(
    feature: HomeFeature,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = feature.onClick,
        modifier = modifier.height(112.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = homeCard())
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(feature.accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(feature.iconRes),
                    contentDescription = feature.title,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column {
                Text(
                    text = feature.title,
                    color = homeTextPrimary(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = feature.subtitle,
                    color = homeTextSecondary(),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HomeQuickAction(
    iconRes: Int,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(96.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(homeCard()),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = Color.Unspecified,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            color = homeTextSecondary(),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
private fun HomeToolboxCard(
    onCache: () -> Unit,
    onSocial: () -> Unit,
    onLarge: () -> Unit,
    onUninstall: () -> Unit,
    onStats: () -> Unit,
    onSuggestions: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = homeCard())
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Temizleyici araçları",
                color = homeTextPrimary(),
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            val tools = listOf(
                Triple("🗑️", "Derin temizlik", onCache),
                Triple("💬", "WhatsApp", onSocial),
                Triple("📁", "Büyük dosya", onLarge),
                Triple("📱", "Uygulama kaldır", onUninstall),
                Triple("📈", "Analitik", onStats),
                Triple("🧰", "Tüm araçlar", onSuggestions)
            )
            tools.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { (emoji, label, click) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(onClick = click)
                                .padding(4.dp)
                        ) {
                            Text(text = emoji, fontSize = 28.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = label,
                                color = homeTextSecondary(),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
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
