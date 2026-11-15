@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.makay.cleaner.ui

import com.makay.cleaner.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makay.cleaner.data.DuplicatePhotoPrefs
import com.makay.cleaner.scanner.DuplicatePhotoScanner

@Composable
internal fun ScanSetupPanel(
    scanMode: String,
    onScanMode: (String) -> Unit,
    matchingLevel: Float,
    onMatchingLevel: (Float) -> Unit,
    showAdvanced: Boolean,
    onToggleAdvanced: () -> Unit,
    timeIntervalSec: Int,
    onTimeInterval: (Int) -> Unit,
    gpsRadius: Int,
    onGpsRadius: (Int) -> Unit,
    includeRotated: Boolean,
    onIncludeRotated: (Boolean) -> Unit,
    selectedBucket: String?,
    onSelectBucket: (String?) -> Unit,
    buckets: List<DuplicatePhotoScanner.BucketInfo>,
    excludedBuckets: Set<String>,
    protectedBuckets: Set<String>,
    allowedFormats: Set<String>,
    onOpenExclude: () -> Unit,
    onOpenProtect: () -> Unit,
    onOpenFormats: () -> Unit,
    onOpenSelectionAssistant: () -> Unit,
    onStartScan: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            stringResource(R.string.txt_1_tarama_kapsam),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            stringResource(R.string.txt_galeri_cihaz_n_zda_taran_r_hi_bir_foto_raf_s),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip("Hızlı", scanMode == DuplicatePhotoPrefs.MODE_QUICK) {
                onScanMode(DuplicatePhotoPrefs.MODE_QUICK)
            }
            ModeChip("Kamera", scanMode == DuplicatePhotoPrefs.MODE_CAMERA) {
                onScanMode(DuplicatePhotoPrefs.MODE_CAMERA)
            }
            ModeChip("Klasör", scanMode == DuplicatePhotoPrefs.MODE_FOLDER) {
                onScanMode(DuplicatePhotoPrefs.MODE_FOLDER)
            }
            ModeChip("Tam", scanMode == DuplicatePhotoPrefs.MODE_FULL) {
                onScanMode(DuplicatePhotoPrefs.MODE_FULL)
            }
        }
        Text(
            when (scanMode) {
                DuplicatePhotoPrefs.MODE_QUICK -> "Son fotoğraflarda hızlı tarama"
                DuplicatePhotoPrefs.MODE_CAMERA -> "Yalnızca kamera / DCIM klasörü"
                DuplicatePhotoPrefs.MODE_FOLDER -> "Seçtiğiniz albüm klasörü"
                else -> "Tüm galeri (harici depolama dahil)"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (scanMode == DuplicatePhotoPrefs.MODE_FOLDER) {
            if (buckets.isEmpty()) {
                Text(stringResource(R.string.txt_alb_m_bulunamad), style = MaterialTheme.typography.bodySmall)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(buckets.take(40), key = { it.name }) { b ->
                        FilterChip(
                            selected = selectedBucket == b.name,
                            onClick = { onSelectBucket(b.name) },
                            label = { Text(stringResource(R.string.txt_1_s_2_s_5, b.name, b.count)) }
                        )
                    }
                }
            }
        }

        Text(
            stringResource(R.string.txt_2_benzerlik_seviyesi),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        val levelLabel = when (matchingLevel.toInt().coerceIn(0, 2)) {
            0 -> "Düşük — daha fazla benzer sonuç"
            2 -> "Yüksek — neredeyse aynı kareler"
            else -> "Orta — dengeli (önerilen)"
        }
        Text(levelLabel, style = MaterialTheme.typography.bodySmall)
        Slider(
            value = matchingLevel,
            onValueChange = { onMatchingLevel(it) },
            valueRange = 0f..2f,
            steps = 1
        )

        TextButton(onClick = onToggleAdvanced) {
            Text(stringResource(if (showAdvanced) R.string.msg_hide_advanced else R.string.msg_show_advanced))
        }
        if (showAdvanced) {
            Text(stringResource(R.string.msg_scan_interval, if (timeIntervalSec == 0) stringResource(R.string.msg_off) else stringResource(R.string.msg_seconds, timeIntervalSec)))
            Slider(
                value = timeIntervalSec.toFloat().coerceIn(0f, 3600f),
                onValueChange = { onTimeInterval(it.toInt()) },
                valueRange = 0f..3600f
            )
            Text(stringResource(R.string.msg_gps_radius, if (gpsRadius == 0) stringResource(R.string.msg_off) else stringResource(R.string.msg_meters, gpsRadius)))
            Slider(
                value = gpsRadius.toFloat(),
                onValueChange = { onGpsRadius(it.toInt()) },
                valueRange = 0f..100f
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.txt_d_nd_r_lm_aynalanm_kopyalar))
                Switch(checked = includeRotated, onCheckedChange = onIncludeRotated)
            }
            OutlinedButton(onClick = onOpenExclude, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (excludedBuckets.isEmpty()) "Hariç tutulan klasörler"
                    else "Hariç tutulanlar (${excludedBuckets.size})"
                )
            }
            OutlinedButton(onClick = onOpenProtect, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (protectedBuckets.isEmpty()) "Korumalı klasörler"
                    else "Korumalı (${protectedBuckets.size}) — auto-mark dokunmaz"
                )
            }
            OutlinedButton(onClick = onOpenFormats, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.txt_format_filtresi_1_s, allowedFormats.size))
            }
            OutlinedButton(onClick = onOpenSelectionAssistant, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.txt_se_im_asistan_ncelik_kurallar))
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onStartScan,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = scanMode != DuplicatePhotoPrefs.MODE_FOLDER || !selectedBucket.isNullOrBlank()
        ) {
            Text(stringResource(R.string.txt_tara), fontWeight = FontWeight.Bold)
        }
        Text(
            stringResource(R.string.txt_sonu_larda_fazlal_klar_se_im_asistan_kuralla),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun ScanSetupCompact(
    scanMode: String,
    matchingLevel: Int,
    onEdit: () -> Unit
) {
    val modeLabel = when (scanMode) {
        DuplicatePhotoPrefs.MODE_QUICK -> "Hızlı"
        DuplicatePhotoPrefs.MODE_CAMERA -> "Kamera"
        DuplicatePhotoPrefs.MODE_FOLDER -> "Klasör"
        else -> "Tam"
    }
    val matchLabel = when (matchingLevel) {
        0 -> "Benzerlik: düşük"
        2 -> "Benzerlik: yüksek"
        else -> "Benzerlik: orta"
    }
    Surface(tonalElevation = 1.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.txt_1_s_2_s_6, modeLabel, matchLabel), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.txt_ayarlar), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun RowScope.ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.weight(1f)
    )
}
