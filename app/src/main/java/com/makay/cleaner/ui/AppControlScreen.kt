package com.makay.cleaner.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makay.cleaner.data.AppUninstallRepository
import com.makay.cleaner.R
import com.makay.cleaner.util.UnusedAppInfo
import com.makay.cleaner.util.UnusedAppsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppControlScreen(
    application: Application,
    onNavigateBack: () -> Unit
) {
    var unused by remember { mutableStateOf<List<UnusedAppInfo>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var showUnusedOnly by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val uninstallRepo = remember { AppUninstallRepository(application) }
    val dateFmt = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

    fun reload() {
        scope.launch {
            loading = true
            unused = withContext(Dispatchers.IO) { UnusedAppsHelper.getUnusedApps(application) }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Uygulama kontrolü", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    if (selected.isNotEmpty()) {
                        TextButton(onClick = {
                            scope.launch {
                                selected.forEach { pkg ->
                                    uninstallRepo.markPendingUninstall(pkg)
                                    val intent = Intent(Intent.ACTION_DELETE).apply {
                                        data = Uri.parse("package:$pkg")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                                Toast.makeText(
                                    context,
                                    "${selected.size} uygulama kaldırma ekranı açıldı",
                                    Toast.LENGTH_SHORT
                                ).show()
                                selected = emptySet()
                            }
                        }) { Text("Kaldır (${selected.size})") }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                "Uzun süredir kullanılmayan uygulamalar kurulum tarihine göre listelenir.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = showUnusedOnly,
                    onClick = { showUnusedOnly = true },
                    label = { Text("Kullanılmayan") }
                )
                TextButton(onClick = { reload() }) { Text("Yenile") }
            }
            Spacer(Modifier.height(8.dp))
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                unused.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Kullanılmayan uygulama bulunamadı")
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(unused, key = { it.packageName }) { app ->
                        val pkg = app.packageName
                        Card(
                            onClick = {
                                selected = if (pkg in selected) selected - pkg else selected + pkg
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = pkg in selected,
                                    onCheckedChange = {
                                        selected = if (it) selected + pkg else selected - pkg
                                    }
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(app.label, fontWeight = FontWeight.SemiBold)
                                    Text(pkg, style = MaterialTheme.typography.labelSmall)
                                    val last = if (app.lastUsedMs > 0)
                                        "Son kullanım: ${dateFmt.format(Date(app.lastUsedMs))}"
                                    else
                                        "Kurulum: ${dateFmt.format(Date(app.installTimeMs))}"
                                    Text(last, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolboxScreen(
    onNavigateBack: () -> Unit,
    onOpen: (String) -> Unit
) {
    data class Tool(val id: String, val iconRes: Int?, val emoji: String, val label: String, val tint: Color)
    data class Section(val title: String, val tools: List<Tool>)

    val sections = listOf(
        Section(
            "Temizleyici",
            listOf(
                Tool("cleaner", R.drawable.ic_hub_cleaner, "🗑️", "Derin temizlik", Color(0xFFFF9F0A)),
                Tool("media_clean", null, "💬", "Sosyal / medya", Color(0xFF25D366)),
                Tool("apk", null, "📦", "APK sil", Color(0xFFFFCC00)),
                Tool("corpse", null, "🧩", "Artık dosya", Color(0xFF3F8CFF)),
                Tool("dup_photos", null, "🖼️", "Fotoğrafları sil", Color(0xFFFF6B35)),
                Tool("system_cleaner", null, "🧹", "Sistem junk", Color(0xFFBF5AF2))
            )
        ),
        Section(
            "Araçlar",
            listOf(
                Tool("data_usage", R.drawable.ic_hub_data, "💧", "Veri kullanımı", Color(0xFF3F8CFF)),
                Tool("battery", R.drawable.ic_hub_battery, "🔋", "Pil", Color(0xFF2EB050)),
                Tool("security_scan", R.drawable.ic_hub_security, "🛡", "Güvenlik tarama", Color(0xFF30D158)),
                Tool("app_control", null, "📱", "Uygulama kontrol", Color(0xFFFFCC00)),
                Tool("manage_apps", R.drawable.ic_hub_apps, "▦", "Uygulamaları yönet", Color(0xFF8E8E93)),
                Tool("recycle", R.drawable.ic_hub_cleaner, "♻️", "Çöp kutusu", Color(0xFF64D2FF))
            )
        ),
        Section(
            "Gizlilik koruması",
            listOf(
                Tool("privacy", R.drawable.ic_hub_privacy, "🔒", "Gizlilik", Color(0xFF3F8CFF)),
                Tool("permissions", null, "👁", "İzinler", Color(0xFFBF5AF2)),
                Tool("app_lock", null, "🔑", "Uygulama kilidi", Color(0xFF2EB050))
            )
        )
    )

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("Araç kutusu", fontWeight = FontWeight.Bold, fontSize = 22.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("✕", color = Color.White, fontSize = 18.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            sections.forEach { section ->
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                section.title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            section.tools.chunked(3).forEach { row ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    row.forEach { tool ->
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { onOpen(tool.id) }
                                                .padding(4.dp)
                                        ) {
                                            Box(
                                                Modifier
                                                    .size(48.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(tool.tint.copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (tool.iconRes != null) {
                                                    Icon(
                                                        painter = androidx.compose.ui.res.painterResource(tool.iconRes),
                                                        contentDescription = tool.label,
                                                        tint = Color.Unspecified,
                                                        modifier = Modifier.size(26.dp)
                                                    )
                                                } else {
                                                    Text(tool.emoji, fontSize = 22.sp)
                                                }
                                            }
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                tool.label,
                                                color = Color(0xFFAEAEB2),
                                                fontSize = 11.sp,
                                                textAlign = TextAlign.Center,
                                                maxLines = 2
                                            )
                                        }
                                    }
                                    repeat(3 - row.size) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
