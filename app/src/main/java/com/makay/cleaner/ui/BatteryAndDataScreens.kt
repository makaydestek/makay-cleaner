package com.makay.cleaner.ui

import androidx.compose.ui.res.stringResource
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import com.makay.cleaner.util.BatteryStatsHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryInfoScreen(
    onNavigateBack: () -> Unit,
    onOpenResourceUsage: () -> Unit = {},
    onOpenSoftHibernate: () -> Unit = {},
    onOpenGoodNight: () -> Unit = {}
) {
    val context = LocalContext.current
    var battery by remember { mutableStateOf(readBatteryUi(context)) }
    DisposableEffect(Unit) {
        val filter = android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                battery = readBatteryUi(context)
            }
        }
        context.registerReceiver(receiver, filter)
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
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
    // EXTRA_HEALTH kapasite sağlığı (%) değildir — anlık durum kodudur
    val healthText = when (battery.health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Normal"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Aşırı sıcak"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Arızalı"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Aşırı voltaj"
        BatteryManager.BATTERY_HEALTH_COLD -> "Çok soğuk"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Belirsiz hata"
        else -> "Bildirilmedi"
    }

    Scaffold(
        containerColor = homeBg(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.txt_pil), fontWeight = FontWeight.Bold, fontSize = 22.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = homeTextPrimary())
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = homeBg(), titleContentColor = homeTextPrimary())
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
                    Text(stringResource(R.string.txt_1_s, battery.percent), color = homeTextPrimary(), fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Text(statusText, color = homeTextPrimary().copy(0.9f), fontSize = 14.sp)
                    Text(
                        stringResource(R.string.txt_sistem_durum_ubu_u_ile_ayn_kaynak),
                        color = homeTextPrimary().copy(0.65f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = hubPanel()),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FactRow("Durum", statusText)
                    FactRow("Bağlantı", plugText)
                    FactRow("Pil durumu (sistem kodu)", healthText)
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
                Text(stringResource(R.string.txt_sistem_ayarlar_nda_y_net), fontWeight = FontWeight.SemiBold)
            }
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = hubPanel()),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    NavRow("Kaynak tüketimi", "Ön plan kullanım listesi") { onOpenResourceUsage() }
                    NavRow("Kış uykusu önerileri", "Sistem pil ayarına yönlendir") { onOpenSoftHibernate() }
                    NavRow("İyi Geceler", "Hatırlatma — otomatik Wi‑Fi kapatma yok") { onOpenGoodNight() }
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
                "“Pil durumu” kapasite aşınması (%) değildir; Android’in anlık durum kodudur. " +
                    "Kalan süre tahmini gösterilmez. Ayrıntılı grafik için sistem Pil ekranını kullanın.",
                color = Color(0xFF8E8E93),
                fontSize = 12.sp
            )
        }
    }
}

private fun readBatteryUi(context: Context): BatteryUi {
    val snap = BatteryStatsHelper.readSnapshot(context)
    return BatteryUi(
        percent = snap.percent,
        charging = snap.charging,
        health = snap.health,
        tempTenths = snap.tempTenths,
        voltage = snap.voltage,
        plugged = snap.plugged
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataUsageScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    fun rx(v: Long) = if (v < 0) 0L else v
    // TrafficStats: cihaz açılışından beri — faturalama dönemi değil
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5_000)
            tick++
        }
    }
    val mobileRx = remember(tick) { rx(TrafficStats.getMobileRxBytes()) }
    val mobileTx = remember(tick) { rx(TrafficStats.getMobileTxBytes()) }
    val totalMobile = mobileRx + mobileTx
    val totalRx = remember(tick) { rx(TrafficStats.getTotalRxBytes()) }
    val totalTx = remember(tick) { rx(TrafficStats.getTotalTxBytes()) }
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
        Toast.makeText(context, context.getString(R.string.msg_data_settings_failed), Toast.LENGTH_SHORT).show()
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
        Toast.makeText(context, context.getString(R.string.msg_network_settings_failed), Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        containerColor = homeBg(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.txt_veri_kullan_m), fontWeight = FontWeight.Bold, fontSize = 22.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = homeTextPrimary())
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = homeBg(), titleContentColor = homeTextPrimary())
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
                    Text(formatBytes(totalMobile), color = homeTextPrimary(), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.txt_mobil_veri_cihaz_a_l_ndan_beri_trafficstats),
                        color = homeTextPrimary().copy(0.85f),
                        fontSize = 13.sp
                    )
                    Text(
                        stringResource(R.string.txt_sistem_ayarlar_ndaki_ayl_k_faturalama_d_nemi),
                        color = homeTextPrimary().copy(0.65f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.txt_ndirilen), color = homeTextPrimary().copy(0.7f), fontSize = 12.sp)
                            Text(formatBytes(mobileRx), color = homeTextPrimary(), fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.txt_y_klenen), color = homeTextPrimary().copy(0.7f), fontSize = 12.sp)
                            Text(formatBytes(mobileTx), color = homeTextPrimary(), fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { openDataSettings() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) { Text(stringResource(R.string.txt_sistem_ayarlar_nda_y_net)) }
                }
            }
            Card(
                onClick = { openNetworkSettings() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = hubPanel()),
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
                    Text(stringResource(R.string.txt_a_ba_lant_s_n_y_net), color = homeTextPrimary(), modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color(0xFF636366))
                }
            }
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = hubPanel()),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.txt_toplam_trafik_wi_fi_mobil), color = homeTextPrimary(), fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.txt_1_s_2_s, formatBytes(totalRx), formatBytes(totalTx)),
                        color = Color(0xFF8E8E93),
                        fontSize = 13.sp
                    )
                    Text(
                        stringResource(R.string.txt_wi_fi_yakla_k_toplam_mobil_1_s_2_s, formatBytes(wifiRx), formatBytes(wifiTx)),
                        color = Color(0xFF8E8E93),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Text(
                        stringResource(R.string.txt_ayl_k_kota_operat_r_d_nemi_i_in_sistem_veri),
                        color = Color(0xFF8E8E93),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        stringResource(R.string.txt_operat_r_tarife_limiti_okunamaz_d_nemsel_kul),
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
        Text(value, color = homeTextPrimary(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
            Text(title, color = homeTextPrimary(), fontSize = 15.sp)
            if (trailing != null) Text(trailing, color = Color(0xFF8E8E93), fontSize = 12.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color(0xFF636366), modifier = Modifier.size(18.dp))
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
    Toast.makeText(context, context.getString(R.string.msg_battery_settings_failed), Toast.LENGTH_SHORT).show()
}

private fun startSettings(context: Context, action: String, fallback: () -> Unit) {
    try {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        fallback()
    }
}
