package com.makay.cleaner.ui

import android.content.Context
import android.content.Intent
import android.net.TrafficStats
import android.os.BatteryManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makay.cleaner.R
import kotlin.math.roundToInt

private val HubGreen = Color(0xFF2EB050)
private val HubBlue = Color(0xFF3F8CFF)
private val HubCard = Color(0xFF1C1C1E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryInfoScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val battery = remember {
        val intent = context.registerReceiver(
            null,
            android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val levelRaw = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = (intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
        val pct = if (levelRaw >= 0) {
            ((levelRaw * 100f) / scale).roundToInt().coerceIn(0, 100)
        } else {
            (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        }
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL ||
            plugged != 0
        BatteryUi(
            percent = pct,
            charging = charging,
            health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1,
            tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1,
            voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1,
            plugged = plugged
        )
    }
    val fill = battery.percent / 100f
    val statusText = when {
        battery.charging && battery.percent >= 100 -> "Tam şarj"
        battery.charging -> "Şarj oluyor"
        else -> "Şarj edilmiyor"
    }
    val plugText = when (battery.plugged) {
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_AC -> "Priz (AC)"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Kablosuz"
        else -> "—"
    }
    val healthText = when (battery.health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "İyi"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Aşırı sıcak"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Ölü"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Aşırı voltaj"
        BatteryManager.BATTERY_HEALTH_COLD -> "Soğuk"
        else -> "Bilinmiyor"
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
                    .clickable { openSystemBattery(context) }
                    .padding(20.dp)
            ) {
                Column {
                    Text("%${battery.percent}", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Text(statusText, color = Color.White.copy(0.9f), fontSize = 14.sp)
                    Text(
                        "Sistem durum çubuğu ile aynı kaynak",
                        color = Color.White.copy(0.65f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = HubCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FactRow("Durum", statusText)
                    FactRow("Bağlantı", plugText)
                    FactRow("Sağlık", healthText)
                    if (battery.tempTenths > 0) {
                        FactRow("Sıcaklık", String.format("%.1f °C", battery.tempTenths / 10f))
                    }
                    if (battery.voltage > 0) {
                        FactRow("Voltaj", "${battery.voltage} mV")
                    }
                }
            }
            Button(
                onClick = { openSystemBattery(context) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = HubBlue)
            ) {
                Text("Sistem ayarlarında yönet", fontWeight = FontWeight.SemiBold)
            }
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = HubCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    NavRow("Pil kullanımı", "Sistem özeti") { openSystemBattery(context) }
                    NavRow("Pil tasarrufu", null) {
                        startSettings(context, Settings.ACTION_BATTERY_SAVER_SETTINGS) {
                            openSystemBattery(context)
                        }
                    }
                    NavRow("Şarj / koruma", "Üretici ayarları") { openSystemBattery(context) }
                }
            }
            Text(
                "Kalan süre tahmini gösterilmez. Ayrıntılı grafik için sistem Pil ekranını kullanın.",
                color = Color(0xFF8E8E93),
                fontSize = 12.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataUsageScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    fun rx(v: Long) = if (v < 0) 0L else v
    val mobileRx = remember { rx(TrafficStats.getMobileRxBytes()) }
    val mobileTx = remember { rx(TrafficStats.getMobileTxBytes()) }
    val totalMobile = mobileRx + mobileTx
    val totalRx = remember { rx(TrafficStats.getTotalRxBytes()) }
    val totalTx = remember { rx(TrafficStats.getTotalTxBytes()) }
    val wifiRx = (totalRx - mobileRx).coerceAtLeast(0)
    val wifiTx = (totalTx - mobileTx).coerceAtLeast(0)

    fun openDataSettings() {
        val actions = listOf(
            Settings.ACTION_DATA_USAGE_SETTINGS,
            Settings.ACTION_DATA_ROAMING_SETTINGS,
            Settings.ACTION_WIRELESS_SETTINGS,
            Settings.ACTION_NETWORK_OPERATOR_SETTINGS,
            Settings.ACTION_SETTINGS
        )
        for (a in actions) {
            try {
                context.startActivity(Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
            }
        }
        Toast.makeText(context, "Veri ayarları bu cihazda açılamadı", Toast.LENGTH_SHORT).show()
    }

    fun openNetworkSettings() {
        val actions = listOf(
            Settings.ACTION_WIFI_SETTINGS,
            Settings.ACTION_WIRELESS_SETTINGS,
            Settings.ACTION_NETWORK_OPERATOR_SETTINGS,
            Settings.ACTION_SETTINGS
        )
        for (a in actions) {
            try {
                context.startActivity(Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
            }
        }
        Toast.makeText(context, "Ağ ayarları bu cihazda açılamadı", Toast.LENGTH_SHORT).show()
    }

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
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF3F8CFF), Color(0xFF1A4A9A))))
                    .padding(20.dp)
            ) {
                Column {
                    Text(formatBytes(totalMobile), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Mobil veri (cihaz açılışından / sayaç sıfırından beri)",
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
                        onClick = { openDataSettings() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(0.22f),
                            contentColor = Color.White
                        )
                    ) { Text("Sistem ayarlarında yönet") }
                }
            }
            Card(
                onClick = { openNetworkSettings() },
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
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_hub_data),
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
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
                        "Wi‑Fi (yaklaşık): ↓ ${formatBytes(wifiRx)} · ↑ ${formatBytes(wifiTx)}",
                        color = Color(0xFF8E8E93),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Text(
                        "Operatör tarife limiti okunamaz. Dönemsel kullanım için sistem Veri kullanımı ekranını açın.",
                        color = Color(0xFF8E8E93),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

private data class BatteryUi(
    val percent: Int,
    val charging: Boolean,
    val health: Int,
    val tempTenths: Int,
    val voltage: Int,
    val plugged: Int
)

@Composable
private fun FactRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFF8E8E93), fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NavRow(title: String, trailing: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            if (trailing != null) Text(trailing, color = Color(0xFF8E8E93), fontSize = 12.sp)
        }
        Icon(Icons.Default.KeyboardArrowRight, null, tint = Color(0xFF636366), modifier = Modifier.size(18.dp))
    }
}

private fun openSystemBattery(context: Context) {
    val intents = listOf(
        Intent(Intent.ACTION_POWER_USAGE_SUMMARY),
        Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
        Intent(Settings.ACTION_SETTINGS)
    )
    for (i in intents) {
        try {
            context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Exception) {
        }
    }
    Toast.makeText(context, "Pil ayarları açılamadı", Toast.LENGTH_SHORT).show()
}

private fun startSettings(context: Context, action: String, fallback: () -> Unit) {
    try {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        fallback()
    }
}
