package com.makay.cleaner.ui

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makay.cleaner.R
import com.makay.cleaner.util.ProManager

private data class ProBenefit(val title: String, val detail: String)

private val proBenefits = listOf(
    ProBenefit("Wi-Fi / şarj zamanlaması", "Otomatik temizlik yalnızca Wi-Fi veya şarjdayken."),
    ProBenefit("Otomatik junk", "Arka planda önbellek ve geçici dosyalar."),
    ProBenefit("Benzer fotoğraflar", "Benzer kareleri siz seçerek çöp kutusuna alırsınız."),
    ProBenefit("JPEG sıkıştırma", "Seçtiğiniz fotoğrafların boyutu düşer."),
    ProBenefit("Yinelenen dosyalar", "Kopyaları listeler; silmeyi siz seçersiniz.")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProScreen(
    application: Application,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var isPro by remember { mutableStateOf(ProManager.isPro(application)) }
    var code by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pro_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
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
                if (isPro) stringResource(R.string.pro_active) else stringResource(R.string.pro_free),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (isPro) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(stringResource(R.string.pro_features_desc), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.pro_sideload_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                stringResource(R.string.pro_free_keeps),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                stringResource(R.string.pro_free_list),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                stringResource(R.string.pro_gain_title),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            proBenefits.forEach { benefit ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(benefit.title, fontWeight = FontWeight.SemiBold)
                        Text(
                            benefit.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                stringResource(R.string.privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isPro) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text(stringResource(R.string.pro_unlock_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    onClick = {
                        if (ProManager.unlockWithCode(application, code)) {
                            isPro = true
                            Toast.makeText(context, context.getString(R.string.pro_unlocked), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, context.getString(R.string.pro_invalid), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.pro_apply)) }
                Text(
                    stringResource(R.string.pro_code_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    "Pro özellikleri açık: zamanlama, otomatik junk, benzer fotoğraf, sıkıştırma ve yinelenen dosyalar.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(
                    onClick = {
                        ProManager.setPro(application, false)
                        isPro = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.pro_disable_test)) }
            }
        }
    }
}
