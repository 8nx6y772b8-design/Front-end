package com.tuner.tv.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.tuner.tv.data.Channel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ------------------------------------------------------------------ background

/** Slowly drifting teal / violet glow. Gradients only (no blur), so it's cheap on a Fire Stick. */
@Composable
fun AuroraBackground() {
    val t = rememberInfiniteTransition(label = "aurora")
    val a = t.animateFloat(0f, 1f, infiniteRepeatable(tween(22_000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b = t.animateFloat(0f, 1f, infiniteRepeatable(tween(27_000, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(Bg)
        fun glow(color: Color, alpha: Float, cx: Float, cy: Float, r: Float) {
            val c = Offset(cx, cy)
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), c, r), r, c)
        }
        glow(Teal, 0.34f, w * (0.02f + 0.14f * a.value), h * (0.02f + 0.18f * a.value), w * 0.42f)
        glow(Violet, 0.36f, w * (0.98f - 0.16f * b.value), h * (0.25f + 0.15f * b.value), w * 0.46f)
        glow(Sky, 0.16f, w * (0.35f + 0.20f * b.value), h * (1.05f - 0.10f * a.value), w * 0.36f)
        drawRect(
            Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), Offset(w / 2, h * 0.4f), w * 0.75f)
        )
    }
}

// ------------------------------------------------------------------ building blocks

@Composable
fun Glass(modifier: Modifier = Modifier, radius: Dp = 16.dp, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(radius)
    Box(modifier.clip(shape).background(GlassFill).border(1.dp, GlassLine, shape), content = content)
}

/** Text painted with the brand gradient. */
@Composable
fun GradientText(text: String, size: TextUnit, weight: FontWeight = FontWeight.Bold, modifier: Modifier = Modifier, letterSpacing: TextUnit = TextUnit.Unspecified) {
    Text(
        text,
        fontSize = size,
        fontWeight = weight,
        letterSpacing = letterSpacing,
        color = Color.White,
        maxLines = 1,
        modifier = modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                drawRect(BrandGradient, blendMode = BlendMode.SrcAtop)
            },
    )
}

@Composable
fun LogoMark(size: Dp = 26.dp) {
    Box(
        Modifier.size(size).shadow(10.dp, RoundedCornerShape(size * 0.3f), ambientColor = Teal, spotColor = Teal)
            .clip(RoundedCornerShape(size * 0.3f)).background(BrandGradient),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.42f)) {
            val p = Path().apply {
                moveTo(this@Canvas.size.width * 0.12f, 0f)
                lineTo(this@Canvas.size.width, this@Canvas.size.height / 2)
                lineTo(this@Canvas.size.width * 0.12f, this@Canvas.size.height)
                close()
            }
            drawPath(p, Bg)
        }
    }
}

@Composable
fun Wordmark() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LogoMark()
        GradientText("TUNER", 20.sp, FontWeight.ExtraBold, letterSpacing = 3.sp)
    }
}

/**
 * A focusable row with the app's focus look: gradient fill, teal outline, glow and a
 * small zoom. [content] gets whether it's focused.
 */
@Composable
fun FocusRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    height: Dp = 48.dp,
    radius: Dp = 12.dp,
    zoom: Float = 1.035f,
    onFocus: (() -> Unit)? = null,
    content: @Composable RowScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) zoom else 1f, tween(160), label = "zoom")
    val shape = RoundedCornerShape(radius)
    Row(
        modifier
            .height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focused) Modifier.shadow(14.dp, shape, ambientColor = Teal, spotColor = Teal) else Modifier)
            .clip(shape)
            .then(
                when {
                    focused -> Modifier.background(FocusFill).border(1.5.dp, Teal.copy(alpha = 0.9f), shape)
                    selected -> Modifier.background(Color(0x12FFFFFF))
                    else -> Modifier
                }
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus?.invoke()
            }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content(focused) }
}

/** Gradient tile with initials; the real logo is drawn on top when the playlist has one. */
@Composable
fun ChannelTile(channel: Channel, width: Dp = 46.dp, height: Dp = 32.dp, fontSize: TextUnit = 11.sp) {
    val h = remember(channel.name) { (channel.name.fold(0) { acc, c -> (acc * 31 + c.code) % 360 }).toFloat() }
    Box(
        Modifier.size(width, height).clip(RoundedCornerShape(height * 0.28f))
            .background(Brush.linearGradient(listOf(Color.hsl(h, 0.75f, 0.52f), Color.hsl((h + 45f) % 360f, 0.70f, 0.32f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials(channel.name), fontSize = fontSize, fontWeight = FontWeight.Bold, color = Color.White)
        if (channel.logo != null) {
            AsyncImage(
                model = channel.logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().background(Color(0xFF151A24)).padding(3.dp),
            )
        }
    }
}

fun initials(name: String): String {
    val words = name.replace(Regex("[^A-Za-z0-9 ]"), " ").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return "TV"
    return if (words.size == 1) words[0].take(2).uppercase() else (words[0].take(1) + words[1].take(1)).uppercase()
}

@Composable
fun Pill(text: String, bg: Color = RecRed, fg: Color = Color.White, pulse: Boolean = true) {
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (pulse) PulseDot(fg, 5.dp)
        Text(text, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = fg)
    }
}

@Composable
fun PulseDot(color: Color, size: Dp) {
    val t = rememberInfiniteTransition(label = "pulse")
    val a = t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "p")
    Box(Modifier.size(size).graphicsLayer { alpha = a.value }.clip(CircleShape).background(color))
}

@Composable
fun KeyHint(key: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0x14FFFFFF)).border(1.dp, GlassLine, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) { Text(key, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFDBE3EE)) }
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 11.sp, color = Muted)
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.height(3.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x1AFFFFFF))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(3.dp).background(BrandGradient))
    }
}

/** Rotating gradient ring. */
@Composable
fun Spinner(size: Dp = 38.dp) {
    val t = rememberInfiniteTransition(label = "spin")
    val r = t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "r")
    Canvas(Modifier.size(size).graphicsLayer { rotationZ = r.value }) {
        drawArc(
            Brush.sweepGradient(listOf(Color.Transparent, Teal, Violet)),
            startAngle = 0f, sweepAngle = 300f, useCenter = false,
            style = Stroke(width = this.size.width * 0.1f),
        )
    }
}

// ------------------------------------------------------------------ time

@Composable
fun rememberNow(periodMs: Long = 15_000): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        delay(periodMs - System.currentTimeMillis() % periodMs)
        value = System.currentTimeMillis()
    }
}

private val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
private val dateFmt = SimpleDateFormat("EEE d MMM", Locale.getDefault())
fun fmtTime(t: Long): String = timeFmt.format(Date(t))
fun fmtDate(t: Long): String = dateFmt.format(Date(t))
fun fmtDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val x = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, x) else "%02d:%02d".format(m, x)
}
fun fmtSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.0f MB".format(bytes / (1L shl 20).toDouble())
    else -> "${bytes / 1024} KB"
}

// ------------------------------------------------------------------ dialogs

data class SheetOption(val label: String, val detail: String = "", val danger: Boolean = false, val onClick: () -> Unit)

/** Side sheet of options (used for the player menu, recordings, guide actions). */
@Composable
fun OptionsSheet(title: String, subtitle: String, options: List<SheetOption>, note: String? = null, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            Column(
                Modifier.width(340.dp).fillMaxHeight().background(SheetFill).padding(horizontal = 22.dp, vertical = 26.dp)
            ) {
                Text(title.uppercase(), fontSize = 12.sp, letterSpacing = 2.sp, color = Muted, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(16.dp))
                val first = remember { FocusRequester() }
                options.forEachIndexed { i, o ->
                    FocusRow(
                        onClick = { o.onClick() },
                        height = 46.dp,
                        zoom = 1.02f,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                    ) {
                        if (o.danger) Box(Modifier.size(9.dp).clip(CircleShape).background(RecRed))
                        else Box(Modifier.size(9.dp).clip(CircleShape).background(Teal.copy(alpha = 0.6f)))
                        Spacer(Modifier.width(12.dp))
                        Text(o.label, fontSize = 14.sp, color = Ink, modifier = Modifier.weight(1f), maxLines = 1)
                        if (o.detail.isNotEmpty()) Text(o.detail, fontSize = 12.sp, color = Muted, maxLines = 1)
                    }
                }
                if (note != null) {
                    Spacer(Modifier.height(14.dp))
                    Text(note, fontSize = 11.5.sp, color = Dim, lineHeight = 16.sp)
                }
                LaunchedEffect(Unit) { focusSoon(first) }
            }
        }
    }
}


/** Requests focus once the target is laid out (retries for a few frames). */
suspend fun focusSoon(fr: FocusRequester) {
    repeat(8) {
        withFrameNanos { }
        if (runCatching { fr.requestFocus() }.isSuccess) return
    }
}

// ------------------------------------------------------------------ text field for a TV remote

/**
 * Looks like a field; press OK to edit (opens the on-screen keyboard), Done to finish.
 * Avoids the keyboard popping up every time focus passes over a field.
 */
@Composable
fun TvField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var editing by remember { mutableStateOf(false) }
    var edited by remember { mutableStateOf(false) }
    val rowFocus = remember { FocusRequester() }
    if (!editing) {
        FocusRow(
            onClick = { editing = true },
            height = 50.dp,
            zoom = 1.015f,
            modifier = modifier.fillMaxWidth().focusRequester(rowFocus).border(1.dp, GlassLine, RoundedCornerShape(12.dp)),
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 10.5.sp, color = Muted)
                val shown = when {
                    value.isEmpty() -> placeholder
                    password -> "•".repeat(value.length.coerceAtMost(16))
                    else -> value
                }
                Text(shown, fontSize = 14.sp, color = if (value.isEmpty()) Dim else Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("OK to edit", fontSize = 10.sp, color = Dim)
        }
        LaunchedEffect(Unit) { if (edited) focusSoon(rowFocus) }
    } else {
        val fr = remember { FocusRequester() }
        var tfv by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
        var hadFocus by remember { mutableStateOf(false) }
        BasicTextField(
            value = tfv,
            onValueChange = { tfv = it; onValueChange(it.text) },
            singleLine = true,
            textStyle = TextStyle(color = Ink, fontSize = 14.sp),
            cursorBrush = SolidColor(Teal),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { edited = true; editing = false }),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = modifier.fillMaxWidth().focusRequester(fr).onFocusChanged {
                if (it.isFocused) hadFocus = true
                else if (hadFocus) { edited = true; editing = false }
            },
            decorationBox = { inner ->
                Column(
                    Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x40000000))
                        .border(2.dp, Teal, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(label, fontSize = 10.5.sp, color = Teal)
                    Box(Modifier.fillMaxWidth()) { inner() }
                }
            },
        )
        LaunchedEffect(Unit) { focusSoon(fr) }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, tween(160), label = "b")
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .then(if (primary) Modifier.background(BrandGradient) else Modifier.border(1.dp, Color(0x24FFFFFF), shape))
            .then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (primary) Bg else Color(0xFFDFE7F1))
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text.uppercase(), fontSize = 10.sp, letterSpacing = 2.sp, color = Dim, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
fun Toast(text: String?, modifier: Modifier = Modifier) {
    if (text == null) return
    Box(modifier.padding(top = 18.dp).widthIn(max = 640.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xE60C0E14))
        .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(12.dp)).padding(horizontal = 18.dp, vertical = 10.dp)) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
    }
}
