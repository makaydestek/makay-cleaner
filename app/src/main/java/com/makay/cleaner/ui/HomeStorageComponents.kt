@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.makay.cleaner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makay.cleaner.util.PlatformManager
import kotlin.math.roundToInt

@Composable
internal fun HomeWhyRow(label: String, onWhy: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = homeTextSecondary(),
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        TvIconButton(onClick = onWhy, idleContent = homeTextPrimary()) {
            Text("?", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
internal fun CompactStatusRow(title: String, action: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .tvClickable(corner = 12.dp, onFocused = { focused = it }, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            color = homeTextPrimary(),
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            action,
            color = homeAccent(),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
internal fun HomeInsightStrip(
    storagePercent: Float,
    junkBytes: Long,
    unusedApps: Int,
    weekSaved: Long,
    topCategory: String?,
    hasPermission: Boolean,
    onStorage: () -> Unit,
    onUnused: () -> Unit,
    onWeek: () -> Unit
) {
    val chips = buildList {
        if (hasPermission) {
            add(
                Triple(
                    if (storagePercent > 0f) "%${storagePercent.roundToInt()} dolu" else "Depolama",
                    topCategory ?: "Kırılım",
                    onStorage
                )
            )
        }
        if (junkBytes > 0L) {
            add(Triple(formatBytes(junkBytes), "Silinebilir", onStorage))
        }
        if (unusedApps > 0) {
            add(Triple("$unusedApps uygulama", "Kullanılmayan", onUnused))
        }
        if (weekSaved > 0L) {
            add(Triple(formatBytes(weekSaved), "Son 7 gün", onWeek))
        }
    }
    if (chips.isEmpty()) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chips.take(4).forEach { (value, label, click) ->
            val (src, focused) = rememberTvInteraction()
            Card(
                onClick = click,
                interactionSource = src,
                modifier = Modifier
                    .weight(1f)
                    .makayFocus(corner = 12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = tvCardColors(focused, homeCard())
            ) {
                Column(
                    Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        value,
                        color = homeTextPrimary(),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        label,
                        color = homeTextSecondary(),
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
internal fun HomeStorageCompact(
    categories: List<com.makay.cleaner.domain.model.StorageCategory>,
    catTotal: Long,
    isLoading: Boolean,
    hasPermission: Boolean,
    onCategoryClick: (String) -> Unit,
    onOpenDetailedAnalysis: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = homeCard())
    ) {
        Column(Modifier.padding(14.dp)) {
            when {
                isLoading && categories.isEmpty() -> {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = homeAccent()
                    )
                }
                catTotal <= 0L -> {
                    Text(
                        if (!hasPermission) "Depolama izni sonrası kırılım görünür."
                        else "Henüz anlamlı kırılım yok.",
                        color = homeTextSecondary(),
                        fontSize = 12.sp
                    )
                }
                else -> {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StorageBreakdownPie(
                            categories = categories.filter { it.sizeBytes > 0 }.ifEmpty { categories },
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            categories.filter { it.sizeBytes > 0 }.take(4).forEach { cat ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onCategoryClick(cat.id.ifBlank { cat.name }) }
                                        .padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(cat.name, color = homeTextSecondary(), fontSize = 12.sp, maxLines = 1)
                                    Text(cat.sizeFormatted, color = homeTextPrimary(), fontSize = 12.sp)
                                }
                            }
                            TextButton(
                                onClick = onOpenDetailedAnalysis,
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Detaylı dosya analizi →", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun HomeCategoryCard(
    title: String,
    subtitle: String,
    accent: Color,
    iconRes: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    actions: List<Pair<String, () -> Unit>>
) {
    val (interaction, focused) = rememberTvInteraction()
    Card(
        onClick = onToggle,
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .makayFocus(corner = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = tvCardColors(focused, homeCard())
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = title,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = homeTextPrimary(),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Text(
                        subtitle,
                        color = homeTextSecondary(),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    if (expanded) "▲" else "▼",
                    color = homeTextSecondary(),
                    fontSize = 12.sp
                )
            }
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                actions.chunked(2).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { (label, click) ->
                            val (chipSrc, _) = rememberTvInteraction()
                            OutlinedButton(
                                onClick = click,
                                interactionSource = chipSrc,
                                modifier = Modifier
                                    .weight(1f)
                                    .makayFocus(corner = 12.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, homeOutline().copy(alpha = 0.35f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = homeTextPrimary()
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(label, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
internal fun HomeScoreHero(
    score: Int,
    subtitle: String,
    isLoading: Boolean,
    buttonEnabled: Boolean = true,
    buttonLabel: String = "Optimizasyon",
    onOptimizeClick: () -> Unit
) {
    val accent = when {
        score >= 100 -> HomeGreen
        score >= 80 -> homeAccent()
        else -> HomeOrange
    }
    val textPrimary = homeTextPrimary()
    val textSecondary = homeTextSecondary()
    val track = if (homeIsLight()) Color(0xFFD8D8DC) else Color(0xFF3A3A3C)
    val isTv = PlatformManager.rememberIsTvMode()
    // TV: 156dp + 40sp "100" kırpılıyordu — halkayı büyüt, tipografiyi sıkılaştır
    val ring = if (isTv) 176.dp else 188.dp
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(ring),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = if (isTv) 10.dp.toPx() else 12.dp.toPx()
                val pad = stroke / 2
                drawArc(
                    color = track,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(pad, pad),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * (score / 100f).coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(pad, pad),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }

            // Metinler çemberin daralan kenarlarına taşmasın: içerik, dairenin içine
            // sığan güvenli kutuya (safe inset) sabitlenir; alt satır bu kutuya sarılır.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isTv) ring * 0.10f else ring * 0.18f)
            ) {
                Text(
                    text = "$score",
                    color = textPrimary,
                    fontSize = if (isTv) {
                        if (score >= 100) 34.sp else 36.sp
                    } else {
                        46.sp
                    },
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp,
                    maxLines = 1
                )
                Text(
                    text = if (score >= 100) "İyi durumda" else "Skor",
                    color = textSecondary,
                    fontSize = if (isTv) 11.sp else 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.width(64.dp).padding(top = 6.dp),
                        color = accent,
                        trackColor = track
                    )
                } else {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = textSecondary,
                        fontSize = if (isTv) 11.sp else 12.sp,
                        lineHeight = if (isTv) 13.sp else 15.sp,
                        textAlign = TextAlign.Center,
                        maxLines = if (isTv) 1 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        val (optInteraction, optFocused) = rememberTvInteraction()
        Button(
            onClick = onOptimizeClick,
            enabled = buttonEnabled,
            interactionSource = optInteraction,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .makayFocus(),
            shape = RoundedCornerShape(14.dp),
            colors = tvButtonColors(
                focused = optFocused,
                idle = if (score >= 100) homeAccent() else HomeOrange
            )
        ) {
            Text(
                text = buttonLabel,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
internal fun StorageBreakdownPie(
    categories: List<com.makay.cleaner.domain.model.StorageCategory>,
    modifier: Modifier = Modifier
) {
    val total = categories.sumOf { it.sizeBytes }.coerceAtLeast(1L).toFloat()
    val colors = categories.map { Color(it.color) }
    val holeColor = MaterialTheme.colorScheme.background
    Canvas(modifier = modifier) {
        val diameter = minOf(size.width, size.height) * 0.9f
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val pieSize = Size(diameter, diameter)
        var start = -90f
        categories.forEachIndexed { index, cat ->
            val sweep = (cat.sizeBytes / total) * 360f
            drawArc(
                color = colors.getOrElse(index) { HomeOrange },
                startAngle = start,
                sweepAngle = sweep.coerceAtLeast(1.5f),
                useCenter = true,
                topLeft = topLeft,
                size = pieSize
            )
            start += sweep
        }
        val hole = diameter * 0.52f
        drawCircle(
            color = holeColor,
            radius = hole / 2f,
            center = Offset(size.width / 2f, size.height / 2f)
        )
    }
}

@Composable
fun StorageLoadingContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(homeBg()),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = homeAccent())
    }
}
