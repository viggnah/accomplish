package io.github.viggnah.accomplish.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A flat, rounded surface. In dark mode it gets a faint outline so it stands off the background. */
fun Modifier.tile(p: Palette, shape: Shape, brush: Brush, elevation: Dp = 0.dp, depth: Float = 1f): Modifier =
    this.background(brush, shape).then(if (p.edge.alpha > 0f) Modifier.border(1.dp, p.edge, shape) else Modifier)

fun Modifier.tile(p: Palette, shape: Shape, color: Color, elevation: Dp = 0.dp, depth: Float = 1f) =
    tile(p, shape, SolidColor(color), elevation, depth)

/** A flat well: inputs, tracks, icon circles. */
fun Modifier.inset(p: Palette, shape: Shape, color: Color = p.bg2): Modifier = this.background(color, shape)

/** Tap with a springy squish instead of a ripple. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.squish(
    enabled: Boolean = true,
    scaleDown: Float = 0.95f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) scaleDown else 1f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "squish")
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(interactionSource = src, indication = null, enabled = enabled, onLongClick = onLongClick, onClick = onClick)
}

@Composable
fun Txt(
    text: String,
    style: TextStyle,
    color: Color = pal().ink,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = align ?: style.textAlign),
        maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
    )
}

/** Stroke icons drawn from 24×24 SVG path data, so no icon library or image files are needed. */
object Icons {
    const val plus = "M12 5v14M5 12h14"
    const val close = "M6 6l12 12M18 6L6 18"
    const val back = "M15 5l-7 7 7 7"
    const val menu = "M4 7h16M4 12h16M4 17h10"
    const val check = "M5 12.5l4.5 4.5L19 7.5"
    const val snow = "M12 3v18M4.2 7.5l15.6 9M4.2 16.5l15.6-9"
    const val horizon = "M3 16h18M7 20h10M7 16a5 5 0 0 1 10 0"
    const val clock = "M12 3.5a8.5 8.5 0 1 0 0 17 8.5 8.5 0 0 0 0-17zM12 7.5V12l3 2"
    const val pebbles = "M3.4 15.5a3.6 3 0 1 0 7.2 0 3.6 3 0 1 0-7.2 0M13.5 16a3 2.6 0 1 0 6 0 3 2.6 0 1 0-6 0M8.8 7.5a3.2 2.8 0 1 0 6.4 0 3.2 2.8 0 1 0-6.4 0"
    const val calendar = "M7 5h10a3.5 3.5 0 0 1 3.5 3.5v8A3.5 3.5 0 0 1 17 20H7a3.5 3.5 0 0 1-3.5-3.5v-8A3.5 3.5 0 0 1 7 5zM3.5 10h17M8 3v4M16 3v4"
    const val history = "M4 12a8 8 0 1 0 2.4-5.7M4 4.5v3.8h3.8M12 8v4.2l2.8 1.8"
    const val gear = "M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6zM19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"
    const val capsule = "M8 4h8a4 4 0 0 1 4 4v8a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4V8a4 4 0 0 1 4-4zM4 12h16"
    const val share = "M12 15V4M8 8l4-4 4 4M5 13v5a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-5"
    const val lesson = "M4 6.5C6.5 5 9.5 5 12 6.5 14.5 5 17.5 5 20 6.5V19c-2.5-1.5-5.5-1.5-8 0-2.5-1.5-5.5-1.5-8 0zM12 6.5V19"
    const val quest = "M5 21V4M5 4h11l-2 4 2 4H5"
    const val note = "M5 5h14v14H5zM5 5l7 6 7-6"
    const val folder = "M3.5 7a2 2 0 0 1 2-2h4l2 2.5h7a2 2 0 0 1 2 2V17a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2z"
    const val bell = "M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15zM10 20.5a2 2 0 0 0 4 0"
    const val hourglass = "M7 3h10M7 21h10M8 3c0 5 8 6 8 9s-8 4-8 9M16 3c0 5-8 6-8 9s8 4 8 9"
}

@Composable
fun Ico(d: String, color: Color, size: Dp = 20.dp, stroke: Float = 2.2f, modifier: Modifier = Modifier, filled: Boolean = false) {
    val path = remember(d) { PathParser().parsePathString(d).toPath() }
    Canvas(modifier.size(size)) {
        scale(this.size.width / 24f, pivot = Offset.Zero) {
            drawPath(path, color, style = if (filled) Fill else Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

enum class BtnKind { PRIMARY, PLAIN, SOFT, GHOST }

/** Three corner sizes: panels (cards, sheets, popovers), controls (buttons, fields, rows) and pills (chips, toggles). */
val PanelShape = RoundedCornerShape(24.dp)
val BtnShape = RoundedCornerShape(14.dp)
val PillShape = RoundedCornerShape(50)

/** Lets a row reach [x] past its parent's padding on each side, so tap areas at the edges can grow while what's drawn stays put. */
fun Modifier.bleed(x: Dp): Modifier = this.layout { m, c ->
    val px = x.roundToPx()
    val placed = m.measure(c.copy(minWidth = c.minWidth + 2 * px, maxWidth = if (c.hasBoundedWidth) c.maxWidth + 2 * px else c.maxWidth))
    layout(placed.width - 2 * px, placed.height) { placed.place(-px, 0) }
}

/** At least 48dp to tap, however small the thing drawn inside. */
@Composable
fun TapBox(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) =
    Box(modifier.size(48.dp).squish(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center, content = content)

@Composable
fun ClayButton(
    text: String,
    modifier: Modifier = Modifier,
    kind: BtnKind = BtnKind.PLAIN,
    big: Boolean = false,
    icon: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val p = pal()
    val bg = when (kind) { BtnKind.PRIMARY -> p.accent; BtnKind.PLAIN -> p.bg2; BtnKind.SOFT -> p.chip; BtnKind.GHOST -> Color.Transparent }
    val fg = when (kind) { BtnKind.PRIMARY -> p.onAccent; BtnKind.PLAIN -> p.ink; BtnKind.SOFT -> p.cardInk; BtnKind.GHOST -> p.inkSoft }
    Row(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .heightIn(min = 44.dp)
            .background(bg, BtnShape)
            .squish(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (big) 20.dp else 14.dp, vertical = if (big) 15.dp else 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Ico(icon, fg, if (big) 20.dp else 16.dp, stroke = 2.6f); Spacer(Modifier.width(8.dp)) }
        Txt(text, Type.display(if (big) 16.5.sp else 14.sp), fg, maxLines = 1)
    }
}

@Composable
fun NavPill(text: String, icon: String, modifier: Modifier = Modifier, badge: String? = null, onClick: () -> Unit) {
    val p = pal()
    Row(
        modifier.heightIn(min = 44.dp).background(p.surface, BtnShape).border(1.dp, p.line, BtnShape).squish(onClick = onClick)
            .padding(start = 10.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(icon, p.inkSoft, 18.dp)
        Spacer(Modifier.width(7.dp))
        Txt(text, Type.body(14.sp, FontWeight.ExtraBold), p.inkSoft, maxLines = 1)
        if (badge != null) { Spacer(Modifier.width(6.dp)); Txt(badge, Type.display(14.sp), p.ink) }
    }
}

@Composable
fun RoundButton(icon: String, label: String, modifier: Modifier = Modifier, size: Dp = 44.dp, onClick: () -> Unit) {
    val p = pal()
    Box(
        modifier.size(size).background(p.surface, BtnShape).border(1.dp, p.line, BtnShape).squish(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Ico(icon, p.inkSoft, size * 0.46f, stroke = 2.2f) }
}

@Composable
fun OptChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = pal()
    val bg by animateColorAsState(if (selected) p.ink else p.bg2, label = "chip")
    Box(modifier.heightIn(min = 44.dp).background(bg, BtnShape).squish(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center) {
        Txt(text, Type.body(14.sp, FontWeight.Bold), if (selected) p.bg else p.ink, maxLines = 1)
    }
}

@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, modifier: Modifier = Modifier, weights: List<Float>? = null, onSelect: (T) -> Unit) {
    val p = pal()
    Row(modifier.fillMaxWidth().background(p.bg2, BtnShape).padding(3.dp)) {
        options.forEachIndexed { i, (v, label) ->
            val on = v == selected
            Box(
                Modifier.weight(weights?.get(i) ?: 1f)
                    .then(if (on) Modifier.background(p.ink, RoundedCornerShape(11.dp)) else Modifier)
                    .squish(onClick = { onSelect(v) })
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) { Txt(label, Type.display(14.sp), if (on) p.bg else p.inkSoft, maxLines = 1) }
        }
    }
}

@Composable
fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = pal()
    val x by animateDpAsState(if (checked) 22.dp else 0.dp, spring(dampingRatio = 0.5f, stiffness = 500f), label = "toggle")
    val track by animateColorAsState(if (checked) p.ink else p.line, label = "track")
    Box(Modifier.squish(onClick = { onChange(!checked) }).padding(vertical = 9.dp)) {
        Box(Modifier.size(width = 52.dp, height = 30.dp).background(track, PillShape).padding(3.dp)) {
            Box(Modifier.offset(x = x).size(24.dp).background(if (checked) p.bg else Color.White, CircleShape))
        }
    }
}

@Composable
fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val p = pal()
    Row(verticalAlignment = Alignment.CenterVertically) {
        TapBox(onMinus) { Box(Modifier.size(38.dp).background(p.bg2, BtnShape), contentAlignment = Alignment.Center) { Ico("M6 12h12", p.ink, 16.dp, stroke = 2.6f) } }
        Txt(value, Type.display(18.sp), p.ink, Modifier.width(36.dp), align = TextAlign.Center)
        TapBox(onPlus) { Box(Modifier.size(38.dp).background(p.bg2, BtnShape), contentAlignment = Alignment.Center) { Ico(Icons.plus, p.ink, 16.dp, stroke = 2.6f) } }
    }
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    onDone: (() -> Unit)? = null,
    color: Color? = null,
    textColor: Color? = null,
) {
    val p = pal()
    val style = Type.body(16.sp).copy(color = textColor ?: p.ink, letterSpacing = 0.25.sp)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        minLines = minLines,
        textStyle = style,
        cursorBrush = SolidColor(p.accent),
        keyboardOptions = KeyboardOptions(imeAction = if (onDone != null) androidx.compose.ui.text.input.ImeAction.Done else androidx.compose.ui.text.input.ImeAction.Default,
            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().background(color ?: p.bg2, BtnShape).padding(horizontal = 16.dp, vertical = 14.dp)) {
                if (value.isEmpty()) Txt(placeholder, style, if (textColor != null) textColor.copy(alpha = 0.55f) else p.placeholder)
                inner()
            }
        },
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Txt(text, Type.label, pal().inkSoft, modifier.padding(top = 18.dp, bottom = 8.dp))
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier, align: TextAlign? = null) {
    Txt(text, Type.body(14.sp), pal().inkSoft, modifier.padding(top = 8.dp), align = align)
}

/** A little "chip" on a card: icon + text on a translucent white pill, or solid red when a deadline is close. */
@Composable
fun CardChip(icon: String, text: String, urgent: Boolean = false) {
    val p = pal()
    val fg = if (urgent) Color.White else p.cardInk
    Row(
        Modifier.background(if (urgent) p.urgent else p.chip, PillShape).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(icon, fg, 14.dp, stroke = 2.4f)
        Spacer(Modifier.width(5.dp))
        Txt(text, Type.body(12.sp, FontWeight.Bold), fg, maxLines = 1)
    }
}

/** An empty screen says so plainly and, when there's something to do about it, offers that. */
@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    androidx.compose.foundation.layout.Column(modifier) {
        Txt(text, Type.body(16.sp), pal().inkSoft)
        if (action != null) {
            Spacer(Modifier.size(12.dp))
            ClayButton(action, icon = Icons.plus, onClick = onAction)
        }
    }
}

