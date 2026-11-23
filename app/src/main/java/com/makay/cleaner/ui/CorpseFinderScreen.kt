package com.makay.cleaner.ui

import com.makay.cleaner.R
import androidx.compose.ui.res.stringResource
import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makay.cleaner.data.CleaningRecorder
import com.makay.cleaner.scanner.CorpseFinder
import com.makay.cleaner.util.CleanResultTracker
import com.makay.cleaner.util.PermissionHelper
import com.makay.cleaner.util.rememberStoragePermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorpseFinderScreen(
    application: Application,
    onNavigateBack: () -> Unit
) {
    val finder = remember { CorpseFinder(application) }
    var corpses by remember { mutableStateOf<List<CorpseFinder.Corpse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var summary by remember { mutableStateOf<CleanResultTracker.Summary?>(null) }
    var confirmAll by remember { mutableStateOf(false) }
    var pendingCorpse by remember { mutableStateOf<CorpseFinder.Corpse?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val hasStoragePermission = rememberStoragePermissionState()

    fun deleteCorpses(targets: List<CorpseFinder.Corpse>) {
        if (targets.isEmpty()) return
        scope.launch {
            var deleted = 0
            var saved = 0L
            withContext(Dispatchers.IO) {
                targets.forEach { c ->
                    val (s, n) = finder.deleteCorpse(c)
                    saved += s
                    deleted += n
                }
            }
            CleaningRecorder.record(application, "Kalıntı bulucu", saved, deleted)
            CleanResultTracker.record("Kalıntı bulucu", deleted, saved, movedToTrash = false)
            summary = CleanResultTracker.last
            corpses = withContext(Dispatchers.IO) { finder.scan() }
            AppNotice.show(context.getString(R.string.msg_remnants_cleaned))
        }
    }

    LaunchedEffect(hasStoragePermission) {
        if (hasStoragePermission) {
            loading = true
            corpses = withContext(Dispatchers.IO) { finder.scan() }
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.txt_kal_nt_bulucu), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    if (corpses.isNotEmpty()) {
                        TextButton(onClick = { confirmAll = true }) { Text(stringResource(R.string.txt_t_m_n_temizle)) }
                    }
                }
            )
        }
    ) { padding ->
        when {
            !hasStoragePermission -> PermissionWarningCard(
                "Uygulama kalıntılarını taramak ve seçtiklerinizi temizlemek için depolama erişimi gerekir."
            ) { PermissionHelper.requestStoragePermission(context) }
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            corpses.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.txt_kald_r_lm_uygulama_kal_nt_s_yok))
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        stringResource(R.string.txt_y_kl_olmayan_uygulamalar_n_android_data_ve_a),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(corpses, key = { it.path }) { corpse ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(corpse.packageName, fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(R.string.txt_1_s_dosya_2_s_2, corpse.fileCount, formatBytes(corpse.sizeBytes)),
                                style = MaterialTheme.typography.bodySmall
                            )
                            TextButton(onClick = { pendingCorpse = corpse }) { Text(stringResource(R.string.txt_temizle_2)) }
                        }
                    }
                }
            }
        }
    }

    if (confirmAll) {
        ConfirmDeleteDialog(
            title = "Tüm kalıntılar silinsin mi?",
            message = "${corpses.size} uygulama kalıntısı klasörü kalıcı olarak silinecek.",
            fileCount = corpses.sumOf { it.fileCount },
            totalBytes = corpses.sumOf { it.sizeBytes },
            moveToTrash = false,
            onConfirm = {
                confirmAll = false
                deleteCorpses(corpses)
            },
            onDismiss = { confirmAll = false }
        )
    }

    pendingCorpse?.let { corpse ->
        ConfirmDeleteDialog(
            title = "Kalıntı silinsin mi?",
            message = "${corpse.packageName} klasörü kalıcı olarak silinecek.",
            fileCount = corpse.fileCount,
            totalBytes = corpse.sizeBytes,
            moveToTrash = false,
            onConfirm = {
                pendingCorpse = null
                deleteCorpses(listOf(corpse))
            },
            onDismiss = { pendingCorpse = null }
        )
    }

    summary?.let { s ->
        CleanSummaryDialog(
            category = s.category,
            filesDeleted = s.filesDeleted,
            spaceSaved = s.spaceSaved,
            protectedSkipped = s.protectedSkipped,
            movedToTrash = false,
            onDismiss = { summary = null }
        )
    }
}
