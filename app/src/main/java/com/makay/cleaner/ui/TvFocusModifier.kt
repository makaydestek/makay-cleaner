package com.makay.cleaner.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makay.cleaner.util.PlatformManager
import kotlinx.coroutines.launch

data class TvFocusPalette(
    val fill: Color,
    val overlay: Color,
    val onFill: Color,
    val borderInner: Color,
    val borderOuter: Color
) {
    companion object {
        /** Açık: koyu dış + beyaz iç çizgi (dolgu yok). */
        val Light = TvFocusPalette(
            fill = Color.Transparent,
            overlay = Color.Transparent,
            onFill = Color(0xFF111111),
            borderInner = Color.White,
            borderOuter = Color(0xFF111111)
        )
        /** Koyu: açık dış + koyu iç çizgi (dolgu yok). */
        val Dark = TvFocusPalette(
            fill = Color.Transparent,
            overlay = Color.Transparent,
            onFill = Color.White,
            borderInner = Color(0xFF111111),
            borderOuter = Color(0xFFF2F2F7)
        )
    }
}

val LocalTvFocusPalette = compositionLocalOf { TvFocusPalette.Light }

@Volatile
internal var tvFocusPaletteNow: TvFocusPalette = TvFocusPalette.Light

object TvFocus {
    val OnFill = Color(0xFF111111)
    val Fill: Color
        @Composable get() = LocalTvFocusPalette.current.fill
    val Overlay: Color
        @Composable get() = LocalTvFocusPalette.current.overlay
}

@Composable
fun rememberTvFocusPalette(): TvFocusPalette {
    val light = MaterialTheme.colorScheme.background.luminance() > 0.45f
    val palette = if (light) TvFocusPalette.Light else TvFocusPalette.Dark
    tvFocusPaletteNow = palette
    return palette
}

/** TV overscan: üst/alt/yan kenar kesilmesin (ayarlar, skor, kırılım). */
fun Modifier.tvSafeContent(): Modifier = composed {
    if (!PlatformManager.rememberIsTvMode()) return@composed this
    this
        .fillMaxSize()
        .padding(horizontal = 48.dp, vertical = 28.dp)
}

/** clickable/switch/radio varsayılan göstergesi — içerik üstünde amber çerçeve. */
val TvFocusIndication: Indication = TvFocusIndicationFactory

private object TvFocusIndicationFactory : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        TvFocusIndicationNode(interactionSource)

    override fun equals(other: Any?): Boolean = other is TvFocusIndicationFactory
    override fun hashCode(): Int = "TvFocusIndication".hashCode()
}

private class TvFocusIndicationNode(
    private val interactionSource: InteractionSource
) : Modifier.Node(), DrawModifierNode {
    private var isFocused = false

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is FocusInteraction.Focus -> {
                        isFocused = true
                        invalidateDraw()
                    }
                    is FocusInteraction.Unfocus -> {
                        isFocused = false
                        invalidateDraw()
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (!isFocused) return
        drawTvFocusChrome(this, palette = tvFocusPaletteNow)
    }
}

private fun drawTvFocusChrome(
    scope: ContentDrawScope,
    corner: Float = 28f,
    palette: TvFocusPalette = tvFocusPaletteNow
) {
    with(scope) {
        val cr = CornerRadius(corner, corner)
        val outer = 5.dp.toPx()
        drawRoundRect(
            color = palette.borderOuter,
            cornerRadius = cr,
            style = Stroke(width = outer)
        )
        val inset = 3.dp.toPx()
        drawRoundRect(
            color = palette.borderInner,
            topLeft = Offset(inset, inset),
            size = Size(
                (size.width - inset * 2).coerceAtLeast(0f),
                (size.height - inset * 2).coerceAtLeast(0f)
            ),
            cornerRadius = CornerRadius((corner - inset).coerceAtLeast(2f)),
            style = Stroke(width = 2.5.dp.toPx())
        )
    }
}

@Composable
fun rememberTvInteraction(): Pair<MutableInteractionSource, Boolean> {
    val isTv = PlatformManager.rememberIsTvMode()
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    return source to (isTv && focused)
}

@Composable
fun tvButtonColors(focused: Boolean, idle: Color, idleContent: Color = Color.White) =
    ButtonDefaults.buttonColors(
        containerColor = idle,
        contentColor = idleContent,
        disabledContainerColor = idle.copy(alpha = 0.45f),
        disabledContentColor = idleContent.copy(alpha = 0.7f)
    )

@Composable
fun tvCardColors(focused: Boolean, idle: Color) =
    CardDefaults.cardColors(
        containerColor = idle,
        contentColor = Color.Unspecified
    )

@Composable
fun tvIconButtonColors(focused: Boolean, idleContent: Color = Color.Unspecified) =
    IconButtonDefaults.iconButtonColors(
        containerColor = Color.Transparent,
        contentColor = idleContent
    )

fun Modifier.tvFocusable(
    focusRequester: FocusRequester = FocusRequester.Default,
    onFocused: (Boolean) -> Unit = {}
): Modifier = makayFocus(focusRequester = focusRequester, onFocused = onFocused)

/**
 * Kumanda odağında: dolgu yok, yalnızca çift kenarlık.
 */
fun Modifier.makayFocus(
    corner: Dp = 14.dp,
    focusRequester: FocusRequester = FocusRequester.Default,
    onFocused: (Boolean) -> Unit = {}
): Modifier = composed {
    val isTv = PlatformManager.rememberIsTvMode()
    if (!isTv) return@composed this

    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.02f else 1f, label = "tvFocusScale")
    val palette = LocalTvFocusPalette.current

    this
        .then(
            if (focusRequester !== FocusRequester.Default) {
                Modifier.focusRequester(focusRequester)
            } else {
                Modifier
            }
        )
        .onFocusChanged {
            focused = it.isFocused
            onFocused(it.isFocused)
        }
        .scale(scale)
        .drawWithContent {
            drawContent()
            if (!focused) return@drawWithContent
            drawTvFocusChrome(this, corner.toPx(), palette)
        }
}

fun Modifier.tvFocusFill(focused: Boolean, corner: Dp = 14.dp): Modifier = this

/** Satır/hücre: odak dolgusu + çerçeve + tıklama. */
fun Modifier.tvClickable(
    enabled: Boolean = true,
    corner: Dp = 12.dp,
    onFocused: (Boolean) -> Unit = {},
    onClick: () -> Unit
): Modifier = composed {
    val isTv = PlatformManager.rememberIsTvMode()
    var focused by remember { mutableStateOf(false) }
    this
        .then(
            if (isTv) {
                Modifier.makayFocus(corner = corner, onFocused = {
                    focused = it
                    onFocused(it)
                })
            } else {
                Modifier
            }
        )
        .clickable(enabled = enabled, onClick = onClick)
}

/** İçindeki Radio/Switch kumandada ayrı hedef olmasın. */
fun Modifier.tvSkipChildFocus(): Modifier = composed {
    if (!PlatformManager.rememberIsTvMode()) return@composed this
    this.focusProperties { canFocus = false }
}

fun Modifier.tvRequestInitialFocus(): Modifier = composed {
    val isTv = PlatformManager.rememberIsTvMode()
    if (!isTv) return@composed this
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            requester.requestFocus()
        } catch (_: Exception) {
        }
    }
    this.focusRequester(requester)
}

@Composable
fun rememberTvFocusRequester(): FocusRequester = remember { FocusRequester() }

@Composable
fun TvFocusRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    corner: Dp = 10.dp,
    content: @Composable RowScope.(focused: Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .tvClickable(corner = corner, onFocused = { focused = it }, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content(focused)
    }
}

@Composable
fun TvActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    idle: Color,
    idleContent: Color = Color.White,
    content: @Composable RowScope.() -> Unit
) {
    val (source, focused) = rememberTvInteraction()
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = source,
        modifier = modifier.makayFocus(),
        colors = tvButtonColors(focused, idle, idleContent),
        content = content
    )
}

@Composable
fun TvIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    idleContent: Color = Color.Unspecified,
    content: @Composable () -> Unit
) {
    val (source, focused) = rememberTvInteraction()
    IconButton(
        onClick = onClick,
        interactionSource = source,
        modifier = modifier.makayFocus(corner = 24.dp),
        colors = tvIconButtonColors(focused, idleContent),
        content = content
    )
}
