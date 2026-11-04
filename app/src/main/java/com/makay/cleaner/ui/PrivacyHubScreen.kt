@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.makay.cleaner.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyHubScreen(
    onNavigateBack: () -> Unit,
    onOpenAppLock: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenPermissionMap: () -> Unit = {},
    onOpenBrowserCache: () -> Unit = {},
    onClearClipboard: () -> Unit = {}
) {
    val context = LocalContext.current
    val lockOn = remember {
        com.makay.cleaner.security.AppLockManager(context).isAnyLockEnabled()
    }
    fun permStatus(permission: String): String {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, permission
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return if (granted) "İzin verildi" else "İzin yok / istenmedi"
    }
    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("Gizlilik koruması", fontWeight = FontWeight.Bold, fontSize = 22.sp) },
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
                        "Anlık izin durumu (sistemle tutarlı). Değiştirmek için satıra dokunun.",
                        color = Color(0xFF8E8E93),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    PrivacyRow("📍", "Konum", permStatus(android.Manifest.permission.ACCESS_FINE_LOCATION)) {
                        openAppSettings(it)
                    }
                    PrivacyRow("👤", "Kişiler", permStatus(android.Manifest.permission.READ_CONTACTS)) {
                        openAppSettings(it)
                    }
                    PrivacyRow("📞", "Telefon", permStatus(android.Manifest.permission.READ_PHONE_STATE)) {
                        openAppSettings(it)
                    }
                    PrivacyRow("🎤", "Mikrofon", permStatus(android.Manifest.permission.RECORD_AUDIO)) {
                        openAppSettings(it)
                    }
                    TextButton(
                        onClick = onOpenPermissions,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Tüm izinler", color = HubBlue, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            PrivacyLinkCard(
                "Uygulama kilidi",
                if (lockOn) "Açık — PIN / biyometrik" else "Kapalı — dokunarak ayarlayın",
                onOpenAppLock
            )
            PrivacyLinkCard("Özel izinler", "Makay Cleaner depolama ve medya izinleri", onOpenPermissions)
            PrivacyLinkCard(
                "İzin haritası",
                "Hangi uygulamanın kamera, konum vb. izni var",
                onOpenPermissionMap
            )
            PrivacyLinkCard(
                "Panoyu temizle",
                "Kopyalanmış metni hemen sil",
                onClearClipboard
            )
            PrivacyLinkCard(
                "Tarayıcı artıkları",
                "Yarım indirmeler + sistem önbellek ayarı",
                onOpenBrowserCache
            )
            PrivacyLinkCard(
                "Gizlilik",
                "Tarama ve temizlik cihazda kalır; buluta yüklenmez",
                onOpenPermissions
            )
        }
    }
}

@Composable
private fun PrivacyRow(emoji: String, title: String, sub: String, onClick: (Context) -> Unit) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .tvClickable(corner = 10.dp, onFocused = { focused = it }) { onClick(context) }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 20.sp, modifier = Modifier.width(36.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(
                sub,
                color = Color(0xFF8E8E93),
                fontSize = 12.sp
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = Color(0xFF636366)
        )
    }
}

@Composable
private fun PrivacyLinkCard(title: String, sub: String, onClick: () -> Unit) {
    val (src, focused) = rememberTvInteraction()
    Card(
        onClick = onClick,
        interactionSource = src,
        shape = RoundedCornerShape(16.dp),
        colors = tvCardColors(focused, HubCard),
        modifier = Modifier
            .fillMaxWidth()
            .makayFocus(corner = 16.dp)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    sub,
                    color = Color(0xFF8E8E93),
                    fontSize = 12.sp
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = Color(0xFF636366)
            )
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
