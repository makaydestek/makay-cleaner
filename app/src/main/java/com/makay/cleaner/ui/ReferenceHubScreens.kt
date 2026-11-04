package com.makay.cleaner.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.TrafficStats
import android.os.BatteryManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makay.cleaner.R
import com.makay.cleaner.data.CleaningRecorder
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
fun CleanerHubScreen(
    application: Application,
    onNavigateBack: () -> Unit,
    onOpenCategory: (String) -> Unit = {}
) {
    var report by remember { mutableStateOf<JunkScanHelper.JunkReport?>(null) }
    var loading by remember { mutableStateOf(true) }
    var cleaning by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf("cache", "unused", "apk", "residue", "tmp")) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val liveStorage = rememberStoragePermissionState()

    fun reload() {
        scope.launch {
            loading = true
            report = null
            report = withContext(Dispatchers.IO) { JunkScanHelper.deepScan(application) }
            loading = false
            val withSize = report?.categories?.filter { it.sizeBytes > 0 }?.map { it.id }?.toSet().orEmpty()
            if (withSize.isNotEmpty()) selected = withSize
            else selected = emptySet()
        }
    }

    // Her açılışta tarama (Optimize’in hızlı temizliğinden kalanları da bulur)
    LaunchedEffect(Unit) { reload() }

    val totalSelected = report?.categories
        ?.filter { it.id in selected }
        ?.sumOf { it.sizeBytes } ?: 0L

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("Temizleyici", fontWeight = FontWeight.Bold, fontSize = 22.sp) },
                navigationIcon = {
                    TvIconButton(
                        onClick = onNavigateBack,
                        idleContent = Color.White,
                        modifier = Modifier.tvRequestInitialFocus()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    val (scanSrc, scanFocused) = rememberTvInteraction()
                    TextButton(
                        onClick = { if (!loading && !cleaning) reload() },
                        enabled = !loading && !cleaning,
                        interactionSource = scanSrc,
                        modifier = Modifier.makayFocus(corner = 12.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = HubOrange,
                            containerColor = Color.Transparent
                        )
                    ) {
                        Text("Tara", fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(16.dp)
            ) {
                val (cleanSrc, cleanFocused) = rememberTvInteraction()
                Button(
                    onClick = {
                        scope.launch {
                            cleaning = true
                            val ids = selected.ifEmpty {
                                report?.categories?.filter { it.sizeBytes > 0 }?.map { it.id }?.toSet()
                                    ?: setOf("cache", "unused", "apk", "residue", "tmp")
                            }
                            val before = report?.cleanableBytes ?: 0L
                            val result = withContext(Dispatchers.IO) {
                                JunkScanHelper.cleanSelected(
                                    application,
                                    ids,
                                    recordAs = "Temizleyici",
                                    useTrash = true,
                                    reclaimSystemCache = "cache" in ids
                                )
                            }
                            cleaning = false
                            val removed = result.spaceSaved
                            val trashed = result.movedToTrashBytes
                            Toast.makeText(
                                context,
                                when {
                                    removed > 0L && trashed > 0L ->
                                        "Boşalan: ${formatBytes(removed)} · Çöpe: ${formatBytes(trashed)}"
                                    removed > 0L ->
                                        "Boşalan alan: ${formatBytes(removed)}"
                                    trashed > 0L ->
                                        "Çöpe alındı: ${formatBytes(trashed)} (diskte yer kaplar)"
                                    before > 0L ->
                                        "Dosyalar duruyor. Skor, silinemeyen öğeler için yükselmez."
                                    else -> "Silinecek dosya kalmadı"
                                },
                                Toast.LENGTH_LONG
                            ).show()
                            reload()
                        }
                    },
                    interactionSource = cleanSrc,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .makayFocus(),
                    shape = RoundedCornerShape(14.dp),
                    colors = tvButtonColors(cleanFocused, HubOrange),
                    enabled = !loading && !cleaning && ((report?.totalBytes ?: 0L) > 0 || totalSelected > 0)
                ) {
                    if (cleaning) {
                        CircularProgressIndicator(
                            Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            "Temizle: ${formatBytes(totalSelected)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))
            JunkHeroOrb(
                sizeLabel = if (loading) "…" else formatBytes(report?.cleanableBytes ?: 0L),
                subtitle = if (loading) "Tarama yapılıyor…" else "Silinebilir junk"
            )
            Spacer(Modifier.height(28.dp))
            if (loading) {
                CircularProgressIndicator(color = HubOrange)
                Text(
                    "Optimize’ten daha geniş tarama…",
                    color = Color(0xFF8E8E93),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            } else {
                val needPerm = !liveStorage
                if (needPerm && (report?.totalBytes ?: 0L) == 0L) {
                    Text(
                        "Junk boyutları için depolama izni gerekli. İzin verdikten sonra tekrar tarayın.",
                        color = Color(0xFF8E8E93),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Button(
                        onClick = {
                            com.makay.cleaner.util.PermissionHelper.requestStoragePermission(context)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = HubBlue)
                    ) { Text("İzin ver") }
                    Spacer(Modifier.height(12.dp))
                }
                if ((report?.totalBytes ?: 0L) == 0L && !needPerm) {
                    Text(
                        "Şu an silinebilir ek junk bulunamadı. Büyük dosya / medya için alt kategorilere bakın.",
                        color = Color(0xFF8E8E93),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                report?.categories?.forEach { cat ->
                    CleanerCategoryRow(
                        title = cat.title,
                        sizeLabel = formatBytes(cat.sizeBytes),
                        checked = cat.id in selected && cat.sizeBytes > 0,
                        enabled = cat.sizeBytes > 0 && !cleaning,
                        onToggle = {
                            selected = if (cat.id in selected) selected - cat.id else selected + cat.id
                        },
                        onExpand = { onOpenCategory(cat.id) }
                    )
                }
                if ((report?.lockedBytes ?: 0L) > 0L) {
                    Text(
                        "Xiaomi yaklaşık ${formatBytes(report!!.lockedBytes)} daha sistem önbelleği gösterir; bu alan sideload ile silinemez.",
                        color = Color(0xFF8E8E93),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                Text(
                    "Silinebilir önbellek + sistem boşaltması. Sistem dosyalarına dokunulmaz.",
                    color = Color(0xFF8E8E93),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun JunkHeroOrb(sizeLabel: String, subtitle: String) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2.2f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(HubOrange.copy(alpha = 0.35f), Color.Transparent)
                ),
                radius = r * 1.15f
            )
            drawCircle(color = Color(0xFF2A1810), radius = r * 0.92f)
            drawCircle(
                color = HubOrange.copy(alpha = 0.7f),
                radius = r * 0.92f,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(sizeLabel, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color(0xFF8E8E93), fontSize = 13.sp)
        }
    }
}

@Composable
private fun CleanerCategoryRow(
    title: String,
    sizeLabel: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onExpand: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .tvClickable(corner = 12.dp, enabled = true, onFocused = { focused = it }, onClick = onExpand)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "▾ $title",
            color = Color.White,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            sizeLabel,
            color = Color(0xFF8E8E93),
            fontSize = 14.sp
        )
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .clickable(enabled = enabled, onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(HubOrange),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color(0xFF636366),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScanScreen(
    application: Application,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onOpenCleaner: () -> Unit = {},
    onOpenPermissions: () -> Unit = {},
    onOpenManageApps: () -> Unit = {}
) {
    data class CheckItem(
        val id: String,
        val title: String,
        val detail: String,
        val ok: Boolean
    )
    data class ScanSummary(
        val junkBytes: Long,
        val junkFiles: Int,
        val unusedApps: Int,
        val appsScanned: Boolean,
        val hasStorage: Boolean,
        val hasUsage: Boolean,
        val score: Int
    )

    var progress by remember { mutableFloatStateOf(0f) }
    var scanning by remember { mutableStateOf(true) }
    var currentLabel by remember { mutableStateOf("Başlatılıyor…") }
    var checks by remember { mutableStateOf(listOf<CheckItem>()) }
    var summary by remember { mutableStateOf<ScanSummary?>(null) }
    var scanToken by remember { mutableIntStateOf(0) }
    var stopRequested by remember { mutableStateOf(false) }
    val liveStorage = rememberStoragePermissionState()
    var knownStorage by remember { mutableStateOf<Boolean?>(null) }
    val settingsFocus = rememberTvFocusRequester()
    var tvFocusEpoch by remember { mutableIntStateOf(0) }
    val isTv = PlatformManager.rememberIsTvMode()

    fun openCheck(id: String) {
        when (id) {
            "storage" -> onOpenPermissions()
            "privacy" -> onNavigateToSettings()
            "junk" -> onOpenCleaner()
            "apps" -> onOpenManageApps()
        }
    }

    // İzin durumu değişince (verildi / geri alındı) tarama kendiliğinden yenilensin
    LaunchedEffect(liveStorage) {
        val prev = knownStorage
        knownStorage = liveStorage
        if (prev != null && prev != liveStorage && !scanning) scanToken++
    }

    LaunchedEffect(scanToken) {
        stopRequested = false
        scanning = true
        summary = null
        checks = emptyList()
        progress = 0f
        val steps = listOf(
            "Depolama erişimi kontrol ediliyor…",
            "Gizlilik ayarları…",
            "Junk / kalıntı taranıyor…",
            "Uygulama listesi…",
            "Özet hazırlanıyor…"
        )
        var junk: JunkScanHelper.JunkReport? = null
        var unused = 0
        var appsScanned = false
        val hasStorage = liveStorage

        for (i in steps.indices) {
            if (stopRequested) break
            currentLabel = steps[i]
            delay(450)
            progress = (i + 1) / steps.size.toFloat()
            when (i) {
                0 -> checks = listOf(
                    CheckItem(
                        "storage",
                        "Depolama ortamı",
                        if (hasStorage) "Erişim açık" else "İzin gerekli · dokunun",
                        hasStorage
                    )
                )
                1 -> {
                    val lockOn = try {
                        com.makay.cleaner.security.AppLockManager(application).isAnyLockEnabled()
                    } catch (_: Exception) {
                        false
                    }
                    checks = checks + CheckItem(
                        "privacy",
                        "Gizlilik & kilit",
                        if (lockOn) "Uygulama kilidi açık"
                        else "Uygulama kilidi kapalı · Ayarlardan açın",
                        lockOn
                    )
                }
                2 -> {
                    junk = withContext(Dispatchers.IO) { JunkScanHelper.scan(application) }
                    val bytes = junk!!.totalBytes
                    checks = checks + CheckItem(
                        "junk",
                        "Junk / kalıntı",
                        "${junk!!.categories.sumOf { it.fileCount }} aday · ${formatBytes(bytes)}",
                        bytes < 50L * 1024 * 1024
                    )
                }
                3 -> {
                    unused = withContext(Dispatchers.IO) {
                        UnusedAppsHelper.getUnusedApps(application).size
                    }
                    appsScanned = true
                    checks = checks + CheckItem(
                        "apps",
                        "Uygulama sağlığı",
                        if (unused == 0) "Kullanılmayan uygulama yok"
                        else "$unused kullanılmayan · Yönet",
                        unused < 8
                    )
                }
            }
        }

        val j = junk
        if (j == null) {
            // Tarama daha ilk adımda durduruldu: eksik veriden skor gösterme
            summary = null
            progress = 1f
            scanning = false
            currentLabel = "Tarama durduruldu — yeniden tarayarak başlayın"
            return@LaunchedEffect
        }
        val score = JunkScanHelper.optimizationScore(j.cleanableBytes, unusedApps = unused)
        summary = ScanSummary(
            junkBytes = j.cleanableBytes,
            junkFiles = j.categories.sumOf { it.fileCount },
            unusedApps = unused,
            appsScanned = appsScanned,
            hasStorage = hasStorage,
            hasUsage = UnusedAppsHelper.hasUsageAccess(application),
            score = score
        )
        // Durdurulmuş tarama geçmişe işlenmez
        if (!stopRequested) {
            CleaningRecorder.recordScan(application, "Güvenlik özeti", j.categories.sumOf { it.fileCount }, j.totalBytes)
        }
        progress = 1f
        scanning = false
        currentLabel = if (stopRequested) {
            "Tarama durduruldu — sonuçlar eksik olabilir"
        } else {
            "Bu bir virüs taraması değildir — cihaz sağlık özeti. Satırlara dokunun"
        }
        tvFocusEpoch++
    }

    LaunchedEffect(isTv, tvFocusEpoch, scanning) {
        if (!isTv || scanning) return@LaunchedEffect
        requestTvFocusSafe(settingsFocus)
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("Güvenlik taraması", fontWeight = FontWeight.Bold, fontSize = 22.sp) },
                navigationIcon = {
                    TvIconButton(
                        onClick = onNavigateBack,
                        idleContent = Color.White
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    TvIconButton(
                        onClick = onNavigateToSettings,
                        idleContent = Color.White,
                        modifier = Modifier.focusRequester(settingsFocus)
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_hub_privacy),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black, titleContentColor = Color.White)
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!scanning && summary != null && summary!!.junkBytes > 0) {
                    Button(
                        onClick = onOpenCleaner,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = HubOrange)
                    ) {
                        Text("Temizleyiciye git · ${formatBytes(summary!!.junkBytes)}", fontWeight = FontWeight.Bold)
                    }
                }
                if (!scanning) {
                    OutlinedButton(
                        onClick = { scanToken++ },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .makayFocus(),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, Color(0xFF636366)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("Yeniden tara", fontWeight = FontWeight.SemiBold)
                    }
                }
                Button(
                    onClick = {
                        if (scanning) {
                            stopRequested = true
                        } else {
                            onNavigateBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2C2C2E),
                        contentColor = Color.White
                    )
                ) {
                    Text(if (scanning) "Dur" else "Tamam", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2.3f
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(HubGreen.copy(0.45f), Color.Transparent)
                        ),
                        radius = r * 1.2f
                    )
                    drawCircle(Color(0xFF0A2A14), radius = r * 0.9f)
                    drawArc(
                        color = HubGreen,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.coerceIn(0.05f, 1f),
                        useCenter = false,
                        style = Stroke(8.dp.toPx(), cap = StrokeCap.Round),
                        topLeft = Offset(size.width / 2 - r * 0.9f, size.height / 2 - r * 0.9f),
                        size = androidx.compose.ui.geometry.Size(r * 1.8f, r * 1.8f)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val ringLabel = if (scanning) "${(progress * 100).toInt()}%"
                    else summary?.let { "${it.score}" } ?: "—"
                    Text(
                        ringLabel,
                        color = Color.White,
                        fontSize = if (ringLabel.length >= 4) 32.sp else 40.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        when {
                            scanning -> "Taranıyor…"
                            summary == null -> "Sonuç yok — yeniden tarayın"
                            else -> "Sağlık skoru"
                        },
                        color = Color.White.copy(0.8f),
                        fontSize = 13.sp
                    )
                }
            }
            Text(currentLabel, color = Color(0xFF8E8E93), fontSize = 12.sp, textAlign = TextAlign.Center)
            if (!scanning) {
                Text(
                    "Makay antivirüs değildir. İzin, junk ve uygulama sağlığı özeti gösterir.",
                    color = Color(0xFF8E8E93),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            checks.forEach { item ->
                var focused by remember { mutableStateOf(false) }
                val isStorage = item.id == "storage"
                val ok = if (isStorage) liveStorage else item.ok
                val detail = if (isStorage) {
                    if (liveStorage) "Erişim açık" else "İzin gerekli · dokunun"
                } else item.detail
                Row(
                    Modifier
                        .fillMaxWidth()
                        .tvClickable(
                            enabled = !scanning,
                            corner = 10.dp,
                            onFocused = { focused = it }
                        ) { openCheck(item.id) }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, color = Color.White, fontSize = 15.sp)
                        Text(
                            detail,
                            color = Color(0xFF8E8E93),
                            fontSize = 12.sp
                        )
                    }
                    if (ok) {
                        Icon(Icons.Default.CheckCircle, null, tint = HubGreen, modifier = Modifier.size(22.dp))
                    } else {
                        Text("!", color = HubOrange, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        null,
                        tint = Color(0xFF636366),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            summary?.let { s ->
                Spacer(Modifier.height(12.dp))
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = HubCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Sonuç özeti", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("• Junk adayı: ${formatBytes(s.junkBytes)} (${s.junkFiles} öğe)", color = Color(0xFFAEAEB2), fontSize = 13.sp)
                        Text(
                            "• Kullanılmayan uygulama: " +
                                (if (s.appsScanned) "${s.unusedApps}" else "— (adım atlandı)"),
                            color = Color(0xFFAEAEB2),
                            fontSize = 13.sp
                        )
                        Text(
                            "• Depolama izni: ${if (liveStorage) "açık" else "kapalı"}",
                            color = Color(0xFFAEAEB2),
                            fontSize = 13.sp
                        )
                        Text(
                            "• Kullanım istatistiği izni: ${if (s.hasUsage) "açık" else "kapalı (isteğe bağlı)"}",
                            color = Color(0xFFAEAEB2),
                            fontSize = 13.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!liveStorage) {
                                TextButton(onClick = onOpenPermissions) {
                                    Text("İzinler", color = HubBlue)
                                }
                            }
                            if (s.unusedApps > 0) {
                                TextButton(onClick = onOpenManageApps) {
                                    Text("Uygulamalar", color = HubBlue)
                                }
                            }
                            if (s.junkBytes > 0) {
                                TextButton(onClick = onOpenCleaner) {
                                    Text("Temizle", color = HubOrange)
                                }
                            }
                        }
                        Text(
                            "Virüs motoru yok — bu bir cihaz sağlık özetidir. Satıra dokunarak ilgili ekranı açın.",
                            color = Color(0xFF636366),
                            fontSize = 11.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(100.dp))
        }
    }
}

