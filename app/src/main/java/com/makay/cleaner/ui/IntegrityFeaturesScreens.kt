package com.makay.cleaner.ui

import com.makay.cleaner.R
import androidx.compose.ui.res.stringResource
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
                title = { Text(stringResource(R.string.txt_zin_haritas), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri")
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
                    stringResource(R.string.txt_kritik_izinlere_sahip_uygulamalar_durdurma_y),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!hasUsage) {
                item {
                    Card(onClick = { ResourceUsageHelper.openUsageAccessSettings(context) }) {
                        Column(Modifier.padding(14.dp)) {
                            Text(stringResource(R.string.txt_kullan_m_eri_imi_kapal), fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(R.string.txt_pheli_skor_i_in_kullan_m_istatisti_i_gerekir),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            if (loading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            } else if (rows.isEmpty()) {
                item { Text(stringResource(R.string.txt_kritik_izinli_nc_parti_uygulama_bulunamad)) }
            } else {
                items(rows, key = { it.packageName }) { row ->
                    Card(
                        onClick = { PermissionMapHelper.openAppDetails(context, row.packageName) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(row.label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(stringResource(R.string.txt_skor_1_s, row.suspicionScore), style = MaterialTheme.typography.labelMedium)
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
                title = { Text(stringResource(R.string.txt_kaynak_t_ketimi), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri")
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
                    stringResource(R.string.txt_son_7_g_nde_n_planda_en_ok_zaman_harcayan_uy),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                OutlinedButton(onClick = onOpenSoftHibernate, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.txt_k_uykusu_nerileri))
                }
            }
            if (!hasUsage) {
                item {
                    Button(
                        onClick = { ResourceUsageHelper.openUsageAccessSettings(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.txt_kullan_m_eri_imini_a)) }
                }
            } else if (loading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            } else if (rows.isEmpty()) {
                item { Text(stringResource(R.string.txt_veri_yok_birka_g_n_kullan_m_sonras_yeniden_d)) }
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
                                    ResourceUsageHelper.formatDuration(row.totalForegroundMs, context),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(stringResource(R.string.txt_ayarlar_2), style = MaterialTheme.typography.labelMedium)
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
                title = { Text(stringResource(R.string.txt_k_uykusu_nerileri), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri")
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
                    stringResource(R.string.txt_uygulamalar_zorla_durdurulmaz_sistem_uygulam),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                OutlinedButton(
                    onClick = { ResourceUsageHelper.openBatteryOptimization(context) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.txt_sistem_pil_optimizasyonu_listesi)) }
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else if (unused.isEmpty()) {
                item { Text(stringResource(R.string.txt_uzun_s_redir_kullan_lmayan_uygulama_aday_yok)) }
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
                                stringResource(R.string.txt_pil_ayar_na_git_1_s, app.packageName),
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
    var confirmTrash by remember { mutableStateOf(false) }

    fun trashSelected() {
        scope.launch {
            busy = true
            val trash = RecycleBinRepository(context)
            var n = 0
            var saved = 0L
            withContext(Dispatchers.IO) {
                trash.beginBatch()
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
            loading = true
            items = withContext(Dispatchers.Default) { BlurPhotoScanner.scan(application) }
            selected = emptySet()
            loading = false
        }
    }

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
                title = { Text(stringResource(R.string.txt_bulan_k_foto_adaylar), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri")
                    }
                },
                actions = {
                    TextButton(onClick = { refresh() }, enabled = !loading && !busy) {
                        Text(stringResource(R.string.txt_tara))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                stringResource(R.string.txt_laplacian_keskinlik_skoru_d_k_g_rseller_ncel),
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
                        item { Text(stringResource(R.string.txt_bulan_k_aday_bulunamad)) }
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
                        onClick = { confirmTrash = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (busy) stringResource(R.string.msg_moving_action) else stringResource(R.string.msg_selected_trash, selected.size))
                    }
                }
            }
        }
    }

    if (confirmTrash) {
        ConfirmDeleteDialog(
            title = "Seçilen fotoğraflar çöpe atılsın mı?",
            message = "${selected.size} fotoğraf 48 saat içinde geri yüklenebilir.",
            fileCount = selected.size,
            totalBytes = items.filter { it.path in selected }.sumOf { it.sizeBytes },
            moveToTrash = true,
            previewPaths = selected.toList(),
            onConfirm = {
                confirmTrash = false
                trashSelected()
            },
            onDismiss = { confirmTrash = false }
        )
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
                title = { Text(stringResource(R.string.txt_performans_profilleri), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri")
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
                stringResource(R.string.txt_ev_pil_yaln_zca_makaycleaner_otomatik_temizl),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AppProfile.entries.forEach { profile ->
                val selected = active == profile
                val title = stringResource(profile.titleRes)
                Card(
                    onClick = {
                        repo.apply(profile)
                        active = profile
                        Toast.makeText(
                            context,
                            context.getString(R.string.msg_profile_applied, title),
                            Toast.LENGTH_SHORT
                        ).show()
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
                            if (selected) stringResource(R.string.profile_active, title) else title,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(profile.descRes),
                            style = MaterialTheme.typography.bodySmall
                        )
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
    var exactAllowed by remember { mutableStateOf(repo.canScheduleExactAlarms()) }

    // Kullanıcı sistem ayarından döndüğünde izin durumunu tazele.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                exactAllowed = repo.canScheduleExactAlarms()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.txt_yi_geceler), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri")
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
                stringResource(R.string.txt_belirlenen_saatte_hat_rlatma_bildirimi_gelir),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.txt_hat_rlatmay_a), fontWeight = FontWeight.SemiBold)
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        repo.save(it, hour, minute)
                    }
                )
            }
            Text(stringResource(R.string.msg_clock, "%02d:%02d".format(hour, minute)), fontWeight = FontWeight.Medium)
            Text(stringResource(R.string.txt_saat), style = MaterialTheme.typography.labelMedium)
            Slider(
                value = hour.toFloat(),
                onValueChange = { hour = it.toInt() },
                valueRange = 0f..23f,
                steps = 22
            )
            Text(stringResource(R.string.txt_dakika), style = MaterialTheme.typography.labelMedium)
            Slider(
                value = minute.toFloat(),
                onValueChange = { minute = it.toInt() },
                valueRange = 0f..59f,
                steps = 58
            )
            Button(
                onClick = {
                    repo.save(enabled, hour, minute)
                    Toast.makeText(context, context.getString(R.string.msg_saved), Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.txt_kaydet)) }

            if (!exactAllowed) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            stringResource(R.string.txt_kesin_alarm_izni_kapal),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.txt_hat_rlatma_birka_dakika_gecikebilir_tam_zama),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val intent = repo.exactAlarmSettingsIntent()
                                if (intent != null) {
                                    runCatching { context.startActivity(intent) }
                                }
                            }
                        ) { Text(stringResource(R.string.txt_zin_ver_2)) }
                    }
                }
            }

            OutlinedButton(
                onClick = { GoodNightReceiver.openWifiSettings(context) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.txt_imdi_wi_fi_ayar_n_a)) }
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
                title = { Text(stringResource(R.string.txt_taray_c_art_klar), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri")
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
                    stringResource(R.string.txt_taray_c_ge_mi_i_cookie_veritaban_na_eri_ilem),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                Text(stringResource(R.string.txt_y_kl_taray_c_lar), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            if (browsers.isEmpty()) {
                item { Text(stringResource(R.string.txt_bilinen_taray_c_bulunamad)) }
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
                                stringResource(R.string.txt_depolama_nbelle_i_sistemden_temizle),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            item {
                Text(stringResource(R.string.txt_ndirme_art_klar), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            if (loading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            } else if (leftovers.isEmpty()) {
                item { Text(stringResource(R.string.txt_yar_m_indirme_art_yok)) }
            } else {
                items(leftovers, key = { it.file.absolutePath }) { left ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(left.file.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(R.string.txt_1_s_2_s_10, formatBytes(left.sizeBytes), left.reason),
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
                        Text(if (busy) stringResource(R.string.msg_cleaning_action) else stringResource(R.string.msg_clean_remnants))
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
