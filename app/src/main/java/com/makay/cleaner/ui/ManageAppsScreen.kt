@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.makay.cleaner.ui

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAppsScreen(
    application: Application,
    onNavigateBack: () -> Unit,
    onOpenAppLock: () -> Unit,
    onOpenAppControl: () -> Unit,
    onOpenUninstall: () -> Unit,
    onOpenProfiles: () -> Unit = {}
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
                    TvIconButton(onClick = onNavigateBack, idleContent = Color.White) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
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
                    QuickTool("🏠", "Profiller", Color(0xFF64D2FF), onOpenProfiles)
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
                        var focused by remember { mutableStateOf(false) }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .tvClickable(corner = 10.dp, onFocused = { focused = it }) {
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
                                .padding(vertical = 14.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                label,
                                color = Color.White,
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                null,
                                tint = Color(0xFF636366),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickTool(emoji: String, label: String, tint: Color, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(100.dp)
            .tvClickable(corner = 16.dp, onFocused = { focused = it }, onClick = onClick)
            .padding(6.dp)
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
        Text(
            label,
            color = Color.White,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}
