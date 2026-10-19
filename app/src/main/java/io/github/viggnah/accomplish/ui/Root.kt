package io.github.viggnah.accomplish.ui

import android.app.Activity
import android.graphics.Color as AColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.viggnah.accomplish.data.Kind
import io.github.viggnah.accomplish.data.ThemeMode
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random


@Composable
fun Root(state: AppState) {
    val sysDark = isSystemInDarkTheme()
    val dark = when (state.settings.theme) { ThemeMode.SYSTEM -> sysDark; ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    val p = if (dark) DarkPalette else LightPalette
    val ctx = LocalContext.current
    LaunchedEffect(dark) {
        val style = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT) else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT)
        (ctx as? ComponentActivity)?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
    CompositionLocalProvider(LocalPalette provides p) {
        Box(Modifier.fillMaxSize().background(p.bg)) {
            when (val ph = state.phase) {
                Phase.Ready -> MainScaffold(state)
                else -> SetupScreen(state, ph)
            }
        }
    }
}

@Composable
private fun BoxScope.MainScaffold(state: AppState) {
    val p = pal()
    BackHandler(enabled = state.opening != null || state.sheet != null || state.stack.size > 1) { state.back() }
    AnimatedContent(
        targetState = state.screen,
        transitionSpec = {
            val forward = targetState != Screen.HOME
            (slideInHorizontally(spring(dampingRatio = 0.8f, stiffness = 400f)) { if (forward) it / 6 else -it / 6 } + fadeIn(tween(220)))
                .togetherWith(fadeOut(tween(150)))
        },
        label = "screen",
        modifier = Modifier.fillMaxSize(),
    ) { s ->
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            when (s) {
                Screen.HOME -> HomeScreen(state)
                Screen.SMALL -> SmallScreen(state)
                Screen.WAITING -> WaitingScreen(state)
                Screen.COMING -> ComingScreen(state)
                Screen.HISTORY -> HistoryScreen(state)
                Screen.COLLECTION -> CollectionScreen(state)
                Screen.NOTES -> AppNotesScreen(state)
                Screen.SETTINGS -> SettingsScreen(state)
            }
        }
    }

    val fabScreens = setOf(Screen.HOME, Screen.SMALL, Screen.WAITING)
    if (state.screen in fabScreens) {
        val kind = when (state.screen) { Screen.SMALL -> Kind.SMALL; Screen.WAITING -> Kind.BIG; else -> state.settings.captureDefault }
        Fab(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
            onClick = { state.sheet = Sheet.Capture(kind) },
            onLongClick = { state.sheet = Sheet.Capture(if (kind == Kind.BIG) Kind.SMALL else Kind.BIG) },
        )
    }
    if (state.screen == Screen.HOME && state.settings.capsules && state.unopened.isNotEmpty()) {
        CapsuleBubble(state.unopened.size, Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(20.dp)) {
            state.openCapsule(state.unopened.first().id)
        }
    }
    MenuPopover(state)
    Confetti(state.celebrate)
    SheetHost(state)
    // Above sheets, so an undo for something removed inside a sheet is visible.
    ToastHost(state, fabVisible = state.screen in fabScreens && state.sheet == null)
    CapsuleOverlay(state)
}

// ---------- header shared by every screen ----------

@Composable
fun Header(
    state: AppState,
    title: String,
    sub: String? = null,
    eyebrow: String? = null,
    onEyebrow: (() -> Unit)? = null,
    onSub: (() -> Unit)? = null,
    home: Boolean = false,
    aboveTitle: (@Composable () -> Unit)? = null,
) {
    val p = pal()
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (home) NavPill(state.settings.small, Icons.pebbles) { state.go(Screen.SMALL) }
            else NavPill(state.settings.big, Icons.back) { state.back() }
            Spacer(Modifier.weight(1f))
            if (home) RoundButton(Icons.menu, "Menu") { state.menuOpen = true }
        }
        Spacer(Modifier.height(16.dp))
        aboveTitle?.invoke()
        if (eyebrow != null) {
            Row(
                if (onEyebrow != null) Modifier.squish(scaleDown = 0.97f, onClick = onEyebrow) else Modifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt(eyebrow, Type.label, p.inkSoft)
                if (onEyebrow != null) { Spacer(Modifier.width(4.dp)); Ico("M9 6l6 6-6 6", p.inkSoft, 14.dp, stroke = 2.8f) }
            }
        }
        Txt(title, Type.display(36.sp), p.ink, Modifier.padding(top = 2.dp))
        if (sub != null) {
            Row(
                if (onSub != null) Modifier.squish(scaleDown = 0.97f, onClick = onSub) else Modifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt(sub, Type.body(14.sp, FontWeight.Bold), p.inkSoft, Modifier.padding(top = 3.dp))
                if (onSub != null) { Spacer(Modifier.width(3.dp)); Ico("M9 6l6 6-6 6", p.inkSoft, 14.dp, stroke = 2.8f, modifier = Modifier.padding(top = 3.dp)) }
            }
        }
    }
}

/** A whole screen that scrolls as one piece, header included. */
@Composable
fun PageColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), content = content)
}

/** The body under a header, with room at the bottom for the floating buttons. */
@Composable
fun ScreenBody(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp), content = content)
}

// ---------- floating pieces ----------

@Composable
private fun Fab(modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    val p = pal()
    val shape = PanelShape
    Box(
        modifier.size(60.dp).shadow(10.dp, shape, ambientColor = p.drop, spotColor = p.drop).background(p.accent, shape)
            .squish(scaleDown = 0.88f, onLongClick = onLongClick, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Ico(Icons.plus, p.onAccent, 26.dp, stroke = 2.8f) }
}

@Composable
private fun CapsuleBubble(count: Int, modifier: Modifier, onClick: () -> Unit) {
    val p = pal()
    val wobble by rememberInfiniteTransition(label = "wobble").animateFloat(
        -7f, 7f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "w",
    )
    Box(modifier.graphicsLayer { rotationZ = wobble }.squish(scaleDown = 0.85f, onClick = onClick)) {
        CapsuleShape(Modifier.size(width = 46.dp, height = 62.dp))
        if (count > 1) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-6).dp).size(24.dp).tile(p, CircleShape, p.ink, elevation = 4.dp),
                contentAlignment = Alignment.Center,
            ) { Txt("$count", Type.display(12.sp), p.bg) }
        }
    }
}

/** The capsule itself: two clay halves with a seam. [split] pulls them apart (0 closed, 1 open). */
@Composable
fun CapsuleShape(modifier: Modifier, split: Float = 0f) {
    val p = pal()
    val (a, b) = p.card(4)
    val (c, d) = p.card(0)
    Canvas(modifier) {
        val w = size.width; val h = size.height; val r = w / 2
        val gap = split * h * 0.35f
        rotate(-split * 25f, pivot = Offset(w / 2, h / 2)) {
            drawRoundRect(a, Offset(0f, -gap), androidx.compose.ui.geometry.Size(w, h / 2 + r * 0.2f), CornerRadius(r, r))
        }
        rotate(split * 25f, pivot = Offset(w / 2, h / 2)) {
            drawRoundRect(c, Offset(0f, h / 2 - r * 0.2f + gap), androidx.compose.ui.geometry.Size(w, h / 2 + r * 0.2f), CornerRadius(r, r))
        }
        if (split < 0.05f) drawLine(p.cardInk.copy(alpha = 0.25f), Offset(w * 0.1f, h / 2), Offset(w * 0.9f, h / 2), 2.dp.toPx(), StrokeCap.Round)
    }
}

// ---------- toast: a small pill; undo shows a countdown ring, swipe or tap × to dismiss ----------

@Composable
private fun BoxScope.ToastHost(state: AppState, fabVisible: Boolean) {
    val p = pal()
    val t = state.toast
    var last by remember { mutableStateOf<Toast?>(null) }
    if (t != null) last = t
    LaunchedEffect(t?.id) {
        if (t != null) { delay((t.until - System.currentTimeMillis()).coerceAtLeast(0)); state.toastExpired(t.id) }
    }
    AnimatedVisibility(
        t != null,
        enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = 500f)) { it / 2 } + fadeIn(),
        exit = fadeOut(tween(160)),
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = if (fabVisible) 100.dp else 28.dp),
    ) {
        val shown = last ?: return@AnimatedVisibility
        var dx by remember(shown.id) { mutableFloatStateOf(0f) }
        val progress = remember(shown.id) { Animatable(1f) }
        LaunchedEffect(shown.id) {
            val left = (shown.until - System.currentTimeMillis()).coerceAtLeast(0).toInt()
            val total = (state.settings.undoSecs * 1000).coerceAtLeast(left).coerceAtLeast(1)
            progress.snapTo(left.toFloat() / total)
            progress.animateTo(0f, tween(left, easing = LinearEasing))
        }
        val shape = PillShape
        val bg = if (p.dark) p.pebble else p.surface
        Row(
            Modifier
                .offset { IntOffset(dx.roundToInt(), 0) }
                .graphicsLayer { alpha = 1f - (abs(dx) / 500f).coerceIn(0f, 0.9f) }
                .shadow(12.dp, shape, ambientColor = p.drop, spotColor = p.drop)
                .background(bg, shape)
                .border(1.dp, p.line, shape)
                .draggable(
                    rememberDraggableState { dx += it },
                    Orientation.Horizontal,
                    onDragStopped = { if (abs(dx) > 120f) state.dismissToast() else dx = 0f },
                )
                .padding(start = 18.dp, end = if (shown.undoable) 6.dp else 18.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt(shown.msg, Type.body(14.sp, FontWeight.Bold), p.ink, Modifier.padding(vertical = 6.dp), maxLines = 2)
            if (shown.undoable) {
                Spacer(Modifier.width(12.dp))
                Row(
                    Modifier.background(p.bg2, shape).squish { state.undo() }.padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawArc(p.ink, -90f, 360f * progress.value, false, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                        }
                        Ico("M9 14L4 9l5-5M4 9h10.5a5.5 5.5 0 0 1 0 11H11", p.ink, 13.dp, stroke = 2.6f)
                    }
                    Spacer(Modifier.width(6.dp))
                    Txt("Undo", Type.display(14.sp), p.ink)
                }
                Box(Modifier.size(44.dp).squish { state.dismissToast() }, contentAlignment = Alignment.Center) {
                    Ico(Icons.close, p.inkSoft, 14.dp, stroke = 2.6f)
                }
            }
        }
    }
}

// ---------- bottom sheet ----------

@Composable
private fun BoxScope.SheetHost(state: AppState) {
    val p = pal()
    val sheet = state.sheet
    var last by remember { mutableStateOf<Sheet?>(null) }
    if (sheet != null) last = sheet
    val close = { state.closeSheet() }

    AnimatedVisibility(sheet != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
        Box(Modifier.fillMaxSize().background(p.scrim).clickable(remember { MutableInteractionSource() }, null) { close() })
    }
    AnimatedVisibility(
        sheet != null,
        enter = slideInVertically(spring(dampingRatio = 0.78f, stiffness = 380f)) { it },
        exit = slideOutVertically(tween(220)) { it },
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        val current = last ?: return@AnimatedVisibility
        var drag by remember(current) { mutableFloatStateOf(0f) }
        Column(
            Modifier
                .offset { IntOffset(0, drag.roundToInt().coerceAtLeast(0)) }
                .fillMaxWidth()
                .heightIn(max = 760.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(p.surface)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Box(
                Modifier.fillMaxWidth().draggable(
                    rememberDraggableState { drag = (drag + it).coerceAtLeast(0f) },
                    Orientation.Vertical,
                    onDragStopped = { v -> if (drag > 220f || v > 1800f) close() else drag = 0f },
                ).padding(top = 10.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(width = 44.dp, height = 5.dp).background(p.line, PillShape)) }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
                when (current) {
                    is Sheet.Capture -> CaptureSheet(state, current.kind, current.spot)
                    is Sheet.Edit -> EditSheet(state, current.id)
                }
            }
        }
    }
}

@Composable
fun SheetTitle(title: String, onClose: () -> Unit, swatch: Brush? = null, confirm: String? = null) {
    val p = pal()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (swatch != null) { Box(Modifier.size(18.dp).clip(CircleShape).background(swatch)); Spacer(Modifier.width(10.dp)) }
        Txt(title, Type.display(24.sp), p.ink, Modifier.weight(1f))
        // When closing saves something new, the button names it instead of showing a ×.
        if (confirm != null) ClayButton(confirm, kind = BtnKind.PRIMARY, icon = Icons.check, onClick = onClose)
        else RoundButton(Icons.close, "Close", onClick = onClose)
    }
}

// ---------- menu: a small card that springs out of the ≡ button ----------

@Composable
private fun BoxScope.MenuPopover(state: AppState) {
    val p = pal()
    val open = state.menuOpen
    if (open) Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { state.menuOpen = false })
    AnimatedVisibility(
        open,
        enter = scaleIn(spring(dampingRatio = 0.7f, stiffness = 600f), initialScale = 0.6f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f)) + fadeIn(tween(120)),
        exit = scaleOut(tween(120), targetScale = 0.8f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f)) + fadeOut(tween(120)),
        modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 62.dp, end = 16.dp),
    ) {
        val shape = PanelShape
        val unopened = state.unopened.size
        val rows = listOf(
            Triple(Icons.hourglass, state.settings.waiting, null) to Screen.WAITING,
            Triple(Icons.capsule, "Capsules", if (unopened > 0) "$unopened" else null) to Screen.COLLECTION,
            Triple(Icons.history, state.settings.history, null) to Screen.HISTORY,
            Triple(Icons.note, "App notes", null) to Screen.NOTES,
            Triple(Icons.gear, "Settings", null) to Screen.SETTINGS,
        )
        Column(
            Modifier.width(232.dp).shadow(18.dp, shape, ambientColor = p.drop, spotColor = p.drop).background(p.surface, shape)
                .border(1.dp, p.line, shape).padding(6.dp),
        ) {
            rows.forEach { (r, screen) ->
                Row(
                    Modifier.fillMaxWidth().squish(scaleDown = 0.97f) { state.menuOpen = false; state.go(screen) }.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Ico(r.first, p.ink, 20.dp)
                    Spacer(Modifier.width(14.dp))
                    Txt(r.second, Type.body(16.sp, FontWeight.Bold), p.ink, Modifier.weight(1f), maxLines = 1)
                    if (r.third != null) Box(Modifier.background(p.ink, PillShape).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Txt(r.third!!, Type.body(12.sp, FontWeight.ExtraBold), p.bg)
                    }
                }
            }
        }
    }
}

/** Clay blobs bursting from the middle of the screen whenever [trigger] changes. */
@Composable
private fun Confetti(trigger: Long) {
    if (trigger == 0L) return
    val p = pal()
    val prog = remember(trigger) { Animatable(0f) }
    val bits = remember(trigger) { List(18) { Triple(it * (2 * Math.PI / 18).toFloat() + Random.nextFloat() * 0.4f, 120f + Random.nextFloat() * 140f, 8f + Random.nextFloat() * 12f) } }
    LaunchedEffect(trigger) { prog.animateTo(1f, tween(900)) }
    if (prog.value >= 1f) return
    Canvas(Modifier.fillMaxSize()) {
        val c = Offset(size.width / 2, size.height * 0.42f)
        val e = 1f - (1f - prog.value) * (1f - prog.value)
        bits.forEachIndexed { i, (ang, dist, r) ->
            val pos = c + Offset(cos(ang) * dist * e * density, sin(ang) * dist * e * density)
            val col = p.card(i)
            drawCircle(p.cardColor(i), r * density, pos, alpha = 1f - prog.value)
        }
    }
}

/** Frost on a card, flat like everything else: an icy tint that thickens with neglect, a crisp icy rim and a few sparkles. */
fun DrawScope.frost(f: Float, p: Palette, corner: Float) {
    if (f <= 0.001f) return
    val rim = 2.dp.toPx() + 3.dp.toPx() * f
    drawRoundRect(p.frostA, cornerRadius = CornerRadius(corner), alpha = 0.15f + 0.4f * f)
    drawRoundRect(p.frostSpark, Offset(rim / 2, rim / 2), androidx.compose.ui.geometry.Size(size.width - rim, size.height - rim),
        CornerRadius(corner - rim / 2), alpha = 0.5f + 0.5f * f, style = Stroke(rim))
    listOf(0.07f to 0.14f, 0.18f to 0.78f, 0.46f to 0.06f, 0.64f to 0.92f, 0.91f to 0.3f, 0.95f to 0.7f).forEach { (x, y) ->
        drawCircle(p.frostSpark, 2.dp.toPx(), Offset(size.width * x, size.height * y), alpha = (f * 2f).coerceAtMost(1f))
    }
}
