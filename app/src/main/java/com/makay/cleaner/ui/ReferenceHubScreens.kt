package com.makay.cleaner.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.TrafficStats
import android.os.BatteryManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makay.cleaner.data.CleaningRecorder
import com.makay.cleaner.util.JunkScanHelper
import com.makay.cleaner.util.PermissionHelper
import com.makay.cleaner.util.UnusedAppsHelper
import com.makay.cleaner.R
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val HubOrange = Color(0xFFFF6B35)
private val HubGreen = Color(0xFF2EB050)
private val HubBlue = Color(0xFF3F8CFF)
private val HubCard = Color(0xFF1C1C1E)

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

    fun reload() {
        scope.launch {
            loading = true
            report = withContext(Dispatchers.IO) { JunkScanHelper.scan(application) }
            loading = false
            val withSize = report?.categories?.filter { it.sizeBytes > 0 }?.map { it.id }?.toSet().orEmpty()
            if (withSize.isNotEmpty()) selected = withSize
        }
    }

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
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
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
                Button(
                    onClick = {
                        scope.launch {
                            cleaning = true
                            val ids = selected.ifEmpty {
                                report?.categories?.filter { it.sizeBytes > 0 }?.map { it.id }?.toSet()
                                    ?: setOf("cache", "unused", "apk", "residue", "tmp")
                            }
                            val result = withContext(Dispatchers.IO) {
                                JunkScanHelper.cleanSelected(application, ids)
                            }
                            cleaning = false
                            Toast.makeText(
                                context,
                                when {
                                    result.filesDeleted > 0 || result.spaceSaved > 0 ->
                                        "Temizlendi: ${formatBytes(result.spaceSaved)} · ${result.filesDeleted} öğe"
                                    else -> "Cihaz zaten temiz — silinebilir junk kalmadı"
                                },
                                Toast.LENGTH_LONG
                            ).show()
                            report = withContext(Dispatchers.IO) { JunkScanHelper.scan(application) }
                            val withSize = report?.categories?.filter { it.sizeBytes > 0 }?.map { it.id }?.toSet().orEmpty()
                            if (withSize.isNotEmpty()) selected = withSize
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = HubOrange, contentColor = Color.White),
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
                sizeLabel = if (loading) "…" else formatBytes(report?.totalBytes ?: 0L),
                subtitle = "Çöp boyutu"
            )
            Spacer(Modifier.height(28.dp))
            if (loading) {
                CircularProgressIndicator(color = HubOrange)
                Text(
                    "Taranıyor…",
                    color = Color(0xFF8E8E93),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            } else {
                val needPerm = !com.makay.cleaner.util.PermissionHelper.hasStoragePermission(application)
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
                    TextButton(onClick = {
                        scope.launch {
                            loading = true
                            report = withContext(Dispatchers.IO) { JunkScanHelper.scan(application) }
                            loading = false
                        }
                    }) { Text("Yeniden tara", color = HubOrange) }
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
                Text(
                    "Seçili öğeler 48 saatlik çöp kutusuna taşınır. Sistem dosyaları silinmez.",
                    color = Color(0xFF8E8E93),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp)
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
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onExpand)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("▾ $title", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(sizeLabel, color = Color(0xFF8E8E93), fontSize = 14.sp)
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
        val hasStorage: Boolean,
        val hasUsage: Boolean,
        val score: Int
    )

    var progress by remember { mutableFloatStateOf(0f) }
    var scanning by remember { mutableStateOf(true) }
    var currentLabel by remember { mutableStateOf("Başlatılıyor…") }
    var checks by remember { mutableStateOf(listOf<CheckItem>()) }
    var summary by remember { mutableStateOf<ScanSummary?>(null) }

    fun openCheck(id: String) {
        when (id) {
            "storage" -> onOpenPermissions()
            "privacy" -> onNavigateToSettings()
            "junk" -> onOpenCleaner()
            "apps" -> onOpenManageApps()
        }
    }

    LaunchedEffect(Unit) {
        val steps = listOf(
            "Depolama erişimi kontrol ediliyor…",
            "Gizlilik ayarları…",
            "Junk / kalıntı taranıyor…",
            "Uygulama listesi…",
            "Özet hazırlanıyor…"
        )
        var junk: JunkScanHelper.JunkReport? = null
        var unused = 0
        val hasStorage = PermissionHelper.hasStoragePermission(application)

        for (i in steps.indices) {
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
                1 -> checks = checks + CheckItem(
                    "privacy",
                    "Gizlilik & kilit",
                    "Uygulama kilidi / gizlilik ayarları",
                    true
                )
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

        val j = junk ?: JunkScanHelper.JunkReport(emptyList(), 0)
        val score = JunkScanHelper.optimizationScore(j.totalBytes, unusedApps = unused)
        summary = ScanSummary(
            junkBytes = j.totalBytes,
            junkFiles = j.categories.sumOf { it.fileCount },
            unusedApps = unused,
            hasStorage = hasStorage,
            hasUsage = true, // kullanım erişimi artık istenmez
            score = score
        )
        CleaningRecorder.recordScan(application, "Güvenlik özeti", j.categories.sumOf { it.fileCount }, j.totalBytes)
        progress = 1f
        scanning = false
        currentLabel = "Virüs motoru yok — cihaz sağlık özeti. Satırlara dokunun"
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("Güvenlik taraması", fontWeight = FontWeight.Bold, fontSize = 22.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            painterResource(R.drawable.ic_hub_privacy),
                            contentDescription = null,
                            tint = Color.White,
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
                Button(
                    onClick = onNavigateBack,
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
                    Text(
                        if (scanning) "${(progress * 100).toInt()}%"
                        else "${summary?.score ?: 0}",
                        color = Color.White,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (scanning) "Taranıyor…" else "Sağlık skoru",
                        color = Color.White.copy(0.8f),
                        fontSize = 13.sp
                    )
                }
            }
            Text(currentLabel, color = Color(0xFF8E8E93), fontSize = 12.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            checks.forEach { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !scanning) { openCheck(item.id) }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, color = Color.White, fontSize = 15.sp)
                        Text(item.detail, color = Color(0xFF8E8E93), fontSize = 12.sp)
                    }
                    if (item.ok) {
                        Icon(Icons.Default.CheckCircle, null, tint = HubGreen, modifier = Modifier.size(22.dp))
                    } else {
                        Text("!", color = HubOrange, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Icon(
                        Icons.Default.KeyboardArrowRight,
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
                        Text("• Kullanılmayan uygulama: ${s.unusedApps}", color = Color(0xFFAEAEB2), fontSize = 13.sp)
                        Text(
                            "• Depolama izni: ${if (s.hasStorage) "açık" else "kapalı"}",
                            color = Color(0xFFAEAEB2),
                            fontSize = 13.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!s.hasStorage) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyHubScreen(
    onNavigateBack: () -> Unit,
    onOpenAppLock: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenPro: () -> Unit
) {
    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("Gizlilik koruması", fontWeight = FontWeight.Bold, fontSize = 22.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black, titleContentColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = HubCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Hassas işlemler", fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        "Hassas izinleri talep eden uygulamalar — sistem ayarlarına yönlendirilir",
                        color = Color(0xFF8E8E93),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    PrivacyRow("📍", "Konum", "Sistem konum izinleri") {
                        openAppSettings(it)
                    }
                    PrivacyRow("👤", "Kişiler", "Kişi erişimi") { openAppSettings(it) }
                    PrivacyRow("📞", "Arama kayıtları", "Telefon izinleri") { openAppSettings(it) }
                    PrivacyRow("🎤", "Mikrofon", "Mikrofon erişimi") { openAppSettings(it) }
                    TextButton(
                        onClick = onOpenPermissions,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Tüm izinler", color = HubBlue, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            PrivacyLinkCard("Uygulama kilidi", "PIN / biyometrik kilit", onOpenAppLock)
            PrivacyLinkCard("Özel izinler", "Makay Cleaner depolama ve medya izinleri", onOpenPermissions)
            PrivacyLinkCard(
                "Gizlilik",
                "Reklam yok — veriler cihazınızda kalır",
                onOpenPermissions
            )
        }
    }
}

@Composable
private fun PrivacyRow(emoji: String, title: String, sub: String, onClick: (Context) -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick(context) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 20.sp, modifier = Modifier.width(36.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(sub, color = Color(0xFF8E8E93), fontSize = 12.sp)
        }
        Icon(Icons.Default.KeyboardArrowRight, null, tint = Color(0xFF636366))
    }
}

@Composable
private fun PrivacyLinkCard(title: String, sub: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = HubCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(sub, color = Color(0xFF8E8E93), fontSize = 12.sp)
            }
            Icon(Icons.Default.KeyboardArrowRight, null, tint = Color(0xFF636366))
        }
    }
}

private fun openAppSettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    } catch (_: Exception) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatteryInfoScreenUnused(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val bm = remember {
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    }
    val level = remember {
        bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
    }
    val charging = remember {
        val intent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }
    val fill = level / 100f
    // Çok yumuşak bant: sadece kaba aralık, kesin süre iddiası yok
    val roughBand = when {
        level >= 80 -> "Bir günden uzun sürebilir"
        level >= 50 -> "Yarım gün civarı (yaklaşık)"
        level >= 20 -> "Birkaç saat (yaklaşık)"
        else -> "Yakında şarj önerilir"
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("Pil", fontWeight = FontWeight.Bold, fontSize = 22.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black, titleContentColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.horizontalGradient(
                            0f to HubGreen,
                            fill to HubGreen,
                            fill to Color(0xFF1A5C2E),
                            1f to Color(0xFF1A5C2E)
                        )
                    )
                    .clickable { openBatterySettings(context) }
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        "%$level",
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (charging) "Şarj oluyor · $roughBand" else roughBand,
                        color = Color.White.copy(0.9f),
                        fontSize = 13.sp
                    )
                    Text(
                        "Dokunarak sistem pil ayarlarını aç",
                        color = Color.White.copy(0.65f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            Button(
                onClick = { openBatterySettings(context) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = HubBlue)
            ) {
                Text("Sistem pil ayarlarını aç", fontWeight = FontWeight.SemiBold)
            }
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = HubCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    SettingsNavRow("Pil kullanımı", "Sistem özeti") {
                        openBatterySettings(context)
                    }
                    SettingsNavRow("Pil tasarrufu", null) {
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (_: Exception) {
                            openBatterySettings(context)
                        }
                    }
                    SettingsNavRow("Şarj / koruma seçenekleri", "Cihaz üreticisi ayarları") {
                        openBatterySettings(context)
                    }
                }
            }
            Text(
                "Makay Cleaner pil ömrünü uzattığını iddia etmez. Süre ifadesi kabaca bir banttır; kesin kalan süre için sistem Pil ekranını kullanın.",
                color = Color(0xFF8E8E93),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun SettingsNavRow(title: String, trailing: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White, fontSize = 15.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (trailing != null) {
                Text(trailing, color = Color(0xFF8E8E93), fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
            }
            Icon(Icons.Default.KeyboardArrowRight, null, tint = Color(0xFF636366), modifier = Modifier.size(18.dp))
        }
    }
}

private fun openBatterySettings(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataUsageScreenUnused(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val mobileRx = remember { TrafficStats.getMobileRxBytes().coerceAtLeast(0) }
    val mobileTx = remember { TrafficStats.getMobileTxBytes().coerceAtLeast(0) }
    val totalMobile = mobileRx + mobileTx
    val totalRx = remember { TrafficStats.getTotalRxBytes().coerceAtLeast(0) }
    val totalTx = remember { TrafficStats.getTotalTxBytes().coerceAtLeast(0) }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("Veri kullanımı", fontWeight = FontWeight.Bold, fontSize = 22.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black, titleContentColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.verticalGradient(listOf(Color(0xFF3F8CFF), Color(0xFF1A4A9A)))
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Text(
                            formatBytes(totalMobile),
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Mobil veri (cihaz açılışından beri)",
                            color = Color.White.copy(0.85f),
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text("İndirilen", color = Color.White.copy(0.7f), fontSize = 12.sp)
                                Text(formatBytes(mobileRx), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Yüklenen", color = Color.White.copy(0.7f), fontSize = 12.sp)
                                Text(formatBytes(mobileTx), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                try {
                                    context.startActivity(
                                        Intent(Settings.ACTION_DATA_USAGE_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (_: Exception) {
                                    context.startActivity(
                                        Intent(Settings.ACTION_WIRELESS_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(0.2f),
                                contentColor = Color.White
                            )
                        ) {
                            Text("Veri kullanımını ayarla")
                        }
                    }
                }
            }
            Card(
                onClick = {
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) {
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = HubCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF7B61FF)),
                        contentAlignment = Alignment.Center
                    ) { Text("📶", fontSize = 18.sp) }
                    Spacer(Modifier.width(12.dp))
                    Text("Ağ bağlantısını yönet", color = Color.White, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.KeyboardArrowRight, null, tint = Color(0xFF636366))
                }
            }
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = HubCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Toplam trafik (Wi‑Fi + mobil)", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        "↓ ${formatBytes(totalRx)}  ·  ↑ ${formatBytes(totalTx)}",
                        color = Color(0xFF8E8E93),
                        fontSize = 13.sp
                    )
                    Text(
                        "Operatör tarife limiti bu uygulamada okunamaz; sistem veri ayarlarını kullanın.",
                        color = Color(0xFF8E8E93),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAppsScreen(
    application: Application,
    onNavigateBack: () -> Unit,
    onOpenAppLock: () -> Unit,
    onOpenAppControl: () -> Unit,
    onOpenUninstall: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        apps = withContext(Dispatchers.IO) {
            val pm = application.packageManager
            pm.getInstalledApplications(0)
                .filter { (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
                .map {
                    val label = try {
                        pm.getApplicationLabel(it).toString()
                    } catch (_: Exception) {
                        it.packageName
                    }
                    label to it.packageName
                }
                .sortedBy { it.first.lowercase() }
        }
        loading = false
    }

    val filtered = remember(query, apps) {
        if (query.isBlank()) apps
        else apps.filter {
            it.first.contains(query, true) || it.second.contains(query, true)
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("Uygulamaları yönet", fontWeight = FontWeight.Bold, fontSize = 22.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black, titleContentColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Uygulamaları ara") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = HubCard,
                    unfocusedContainerColor = HubCard,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            Spacer(Modifier.height(12.dp))
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = HubCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    QuickTool("🛡", "Uygulama kilidi", Color(0xFF2EB050), onOpenAppLock)
                    QuickTool("⏱", "Kullanılmayan", Color(0xFFFFCC00), onOpenAppControl)
                    QuickTool("🗑", "Kaldır", Color(0xFF3F8CFF), onOpenUninstall)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Uygulama adına göre sıralanıyor · ${filtered.size}",
                color = Color(0xFF8E8E93),
                fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = HubBlue)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(filtered.size) { i ->
                        val (label, pkg) = filtered[i]
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    try {
                                        application.startActivity(
                                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = android.net.Uri.fromParts("package", pkg, null)
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                        )
                                    } catch (_: Exception) {
                                    }
                                }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
                            Icon(Icons.Default.KeyboardArrowRight, null, tint = Color(0xFF636366), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickTool(emoji: String, label: String, tint: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(100.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(tint),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 18.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 2)
    }
}
