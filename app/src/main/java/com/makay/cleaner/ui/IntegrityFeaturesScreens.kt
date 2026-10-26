package com.makay.cleaner.ui

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makay.cleaner.data.AppProfile
import com.makay.cleaner.data.AppProfilesRepository
import com.makay.cleaner.data.GoodNightRepository
import com.makay.cleaner.data.RecycleBinRepository
import com.makay.cleaner.scanner.BlurPhotoCandidate
import com.makay.cleaner.scanner.BlurPhotoScanner
import com.makay.cleaner.scanner.BrowserCacheScanner
import com.makay.cleaner.scanner.BrowserLeftover
import com.makay.cleaner.util.ClipboardCleanHelper
import com.makay.cleaner.util.FilePurger
import com.makay.cleaner.util.PermissionMapHelper
import com.makay.cleaner.util.ResourceUsageHelper
import com.makay.cleaner.util.UnusedAppsHelper
import com.makay.cleaner.worker.GoodNightReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionMapScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var rows by remember { mutableStateOf<List<com.makay.cleaner.util.PermissionAppRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val hasUsage = remember { UnusedAppsHelper.hasUsageAccess(context) }

    LaunchedEffect(Unit) {
        loading = true
        rows = withContext(Dispatchers.Default) { PermissionMapHelper.scan(context) }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("İzin haritası", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Kritik izinlere sahip uygulamalar. Durdurma yok — satıra dokununca sistem ayarı açılır.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!hasUsage) {
                item {
                    Card(onClick = { ResourceUsageHelper.openUsageAccessSettings(context) }) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Kullanım erişimi kapalı", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Şüpheli skor için kullanım istatistiği gerekir. Dokunarak açın.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            if (loading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            } else if (rows.isEmpty()) {
                item { Text("Kritik izinli üçüncü parti uygulama bulunamadı.") }
            } else {
                items(rows, key = { it.packageName }) { row ->
                    Card(
                        onClick = { PermissionMapHelper.openAppDetails(context, row.packageName) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(row.label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("Skor ${row.suspicionScore}", style = MaterialTheme.typography.labelMedium)
                            }
                            Text(
                                row.criticalPermissions.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourceUsageScreen(
    onNavigateBack: () -> Unit,
    onOpenSoftHibernate: () -> Unit = {}
) {
    val context = LocalContext.current
    var rows by remember { mutableStateOf<List<com.makay.cleaner.util.ResourceAppRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val hasUsage = remember { UnusedAppsHelper.hasUsageAccess(context) }

    LaunchedEffect(hasUsage) {
        if (!hasUsage) {
            rows = emptyList()
            loading = false
            return@LaunchedEffect
        }
        loading = true
        rows = withContext(Dispatchers.Default) { ResourceUsageHelper.topConsumers(context) }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kaynak tüketimi", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Son 7 günde ön planda en çok zaman harcayan uygulamalar (UsageStats).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                OutlinedButton(onClick = onOpenSoftHibernate, modifier = Modifier.fillMaxWidth()) {
                    Text("Kış uykusu önerileri")
                }
            }
            if (!hasUsage) {
                item {
                    Button(
                        onClick = { ResourceUsageHelper.openUsageAccessSettings(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Kullanım erişimini aç") }
                }
            } else if (loading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            } else if (rows.isEmpty()) {
                item { Text("Veri yok. Birkaç gün kullanım sonrası yeniden deneyin.") }
            } else {
                items(rows, key = { it.packageName }) { row ->
                    Card(
                        onClick = { ResourceUsageHelper.openAppDetails(context, row.packageName) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(14.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(row.label, fontWeight = FontWeight.Bold)
                                Text(
                                    ResourceUsageHelper.formatDuration(row.totalForegroundMs),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text("Ayarlar →", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoftHibernateScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var unused by remember {
        mutableStateOf<List<com.makay.cleaner.util.UnusedAppInfo>>(emptyList())
    }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        unused = withContext(Dispatchers.Default) {
            UnusedAppsHelper.getUnusedApps(context, unusedDays = 30)
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kış uykusu önerileri", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Uygulamalar zorla durdurulmaz. Sistem uygulama bilgisinden pil optimizasyonunu açmanız önerilir.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                OutlinedButton(
                    onClick = { ResourceUsageHelper.openBatteryOptimization(context) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Sistem pil optimizasyonu listesi") }
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else if (unused.isEmpty()) {
                item { Text("Uzun süredir kullanılmayan uygulama adayı yok.") }
            } else {
                items(unused.take(40), key = { it.packageName }) { app ->
                    Card(
                        onClick = {
                            ResourceUsageHelper.openBatteryOptimization(context, app.packageName)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(app.label, fontWeight = FontWeight.Bold)
                            Text(
                                "Pil ayarına git · ${app.packageName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlurPhotosScreen(application: Application, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<BlurPhotoCandidate>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            loading = true
            items = withContext(Dispatchers.Default) { BlurPhotoScanner.scan(application) }
            selected = emptySet()
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bulanık foto adayları", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    TextButton(onClick = { refresh() }, enabled = !loading && !busy) {
                        Text("Tara")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                "Laplacian keskinlik skoru düşük görseller. İnceleyin; silme çöp kutusuna taşır.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (items.isEmpty()) {
                        item { Text("Bulanık aday bulunamadı.") }
                    }
                    items(items, key = { it.path }) { photo ->
                        val on = photo.path in selected
                        Card(
                            onClick = {
                                selected = if (on) selected - photo.path else selected + photo.path
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (on)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(photo.displayName, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${formatBytes(photo.sizeBytes)} · skor ${"%.0f".format(photo.blurScore)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                if (selected.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                val trash = RecycleBinRepository(context)
                                var n = 0
                                var saved = 0L
                                withContext(Dispatchers.IO) {
                                    selected.forEach { path ->
                                        val f = File(path)
                                        val size = if (f.isFile) f.length() else 0L
                                        if (FilePurger.remove(context, f, trash = trash, category = "Bulanık foto")) {
                                            n++
                                            saved += size
                                        }
                                    }
                                }
                                busy = false
                                Toast.makeText(
                                    context,
                                    if (n > 0) "$n foto çöpe · ${formatBytes(saved)}"
                                    else "Silinemedi",
                                    Toast.LENGTH_SHORT
                                ).show()
                                refresh()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (busy) "Taşınıyor…" else "Seçilenleri çöpe at (${selected.size})")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppProfilesScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { AppProfilesRepository(context) }
    var active by remember { mutableStateOf(repo.getActive()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Performans profilleri", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
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
            Text(
                "Ev / İş / Pil yalnızca MakayCleaner otomatik temizlik davranışını ayarlar. Wi‑Fi veya CPU’ya dokunulmaz.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AppProfile.entries.forEach { profile ->
                val selected = active == profile
                Card(
                    onClick = {
                        repo.apply(profile)
                        active = profile
                        Toast.makeText(context, "${profile.titleTr} uygulandı", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            if (selected) "${profile.titleTr} · aktif" else profile.titleTr,
                            fontWeight = FontWeight.Bold
                        )
                        Text(profile.descTr, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoodNightScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { GoodNightRepository(context) }
    var enabled by remember { mutableStateOf(repo.isEnabled()) }
    var hour by remember { mutableIntStateOf(repo.getHour()) }
    var minute by remember { mutableIntStateOf(repo.getMinute()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("İyi Geceler", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
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
            Text(
                "Belirlenen saatte hatırlatma bildirimi gelir. Wi‑Fi otomatik kapatılmaz; tek dokunuşla sistem ayarı açılır.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Hatırlatmayı aç", fontWeight = FontWeight.SemiBold)
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        repo.save(it, hour, minute)
                    }
                )
            }
            Text("Saat: %02d:%02d".format(hour, minute), fontWeight = FontWeight.Medium)
            Text("Saat", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = hour.toFloat(),
                onValueChange = { hour = it.toInt() },
                valueRange = 0f..23f,
                steps = 22
            )
            Text("Dakika", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = minute.toFloat(),
                onValueChange = { minute = it.toInt() },
                valueRange = 0f..59f,
                steps = 58
            )
            Button(
                onClick = {
                    repo.save(enabled, hour, minute)
                    Toast.makeText(context, "Kaydedildi", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Kaydet") }
            OutlinedButton(
                onClick = { GoodNightReceiver.openWifiSettings(context) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Şimdi Wi‑Fi ayarını aç") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserCacheScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var browsers by remember { mutableStateOf(BrowserCacheScanner.installedBrowsers(context)) }
    var leftovers by remember { mutableStateOf<List<BrowserLeftover>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            loading = true
            browsers = BrowserCacheScanner.installedBrowsers(context)
            leftovers = withContext(Dispatchers.IO) { BrowserCacheScanner.scanLeftovers(context) }
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tarayıcı artıkları", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Tarayıcı geçmişi / cookie veritabanına erişilemez. İndirmelerdeki yarım dosyalar temizlenir; tam önbellek için sistem uygulama bilgisini kullanın.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                Text("Yüklü tarayıcılar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            if (browsers.isEmpty()) {
                item { Text("Bilinen tarayıcı bulunamadı.") }
            } else {
                items(browsers, key = { it.packageName }) { b ->
                    Card(
                        onClick = {
                            BrowserCacheScanner.openBrowserStorage(context, b.packageName)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(b.label, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Depolama / önbelleği sistemden temizle",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            item {
                Text("İndirme artıkları", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            if (loading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            } else if (leftovers.isEmpty()) {
                item { Text("Yarım indirme artığı yok.") }
            } else {
                items(leftovers, key = { it.file.absolutePath }) { left ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(left.file.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${formatBytes(left.sizeBytes)} · ${left.reason}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                item {
                    Button(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                val (n, saved) = withContext(Dispatchers.IO) {
                                    BrowserCacheScanner.cleanLeftovers(
                                        context,
                                        leftovers.map { it.file }
                                    )
                                }
                                busy = false
                                Toast.makeText(
                                    context,
                                    if (n > 0) "$n dosya · ${formatBytes(saved)}"
                                    else "Silinemedi",
                                    Toast.LENGTH_SHORT
                                ).show()
                                refresh()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (busy) "Temizleniyor…" else "Artıkları temizle")
                    }
                }
            }
        }
    }
}

/** Privacy hub içinden tek tuş pano temizliği — ayrı ekran gerekmez. */
fun clearClipboardNow(context: android.content.Context): String {
    return if (ClipboardCleanHelper.clear(context)) {
        "Pano temizlendi"
    } else {
        "Pano temizlenemedi"
    }
}
