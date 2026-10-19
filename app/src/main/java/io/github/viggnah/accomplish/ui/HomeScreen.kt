package io.github.viggnah.accomplish.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.viggnah.accomplish.data.Kind
import io.github.viggnah.accomplish.data.Task
import io.github.viggnah.accomplish.logic.Logic
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(state: AppState) {
    val p = pal()
    val s = state.settings
    val a = state.active
    val today = state.today
    val w = state.waiting.size
    PageColumn {
        Header(state, s.big, home = true, aboveTitle = { WeekStrip(state) })
        ScreenBody {
            if (a.size > s.slots) {
                Txt("You have ${a.size} ${s.big} on Home but room for ${s.slots}. Open one and send it back to the ${s.waiting}.",
                    Type.body(14.sp), p.ink, Modifier.fillMaxWidth().tile(p, PanelShape, p.pebble, 4.dp).padding(16.dp))
                Spacer(Modifier.height(14.dp))
            }
            val drag = rememberCardDrag(state, a)
            for (i in 0 until maxOf(s.slots, a.size)) {
                val t = a.getOrNull(i)
                if (t != null) BigCard(state, t, i, drag)
                else EmptySpot(i, w, onWaiting = { state.go(Screen.WAITING) }) { state.sheet = Sheet.Capture(Kind.BIG, spot = i) }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun SpotNumber(n: Int, empty: Boolean = false) {
    val p = pal()
    Box(
        Modifier.size(30.dp).then(if (empty) Modifier.inset(p, CircleShape) else Modifier.background(p.hi, CircleShape)),
        contentAlignment = Alignment.Center,
    ) { Txt("$n", Type.display(16.sp), if (empty) p.inkSoft else p.cardInk) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BigCard(state: AppState, t: Task, i: Int, drag: CardDrag) {
    val p = pal()
    val s = state.settings
    val today = state.today
    val frostTarget = Logic.frost(t, s, today)
    val frost by animateFloatAsState(frostTarget, tween(900), label = "frost")
    val shape = PanelShape
    Column(
        Modifier
            .fillMaxWidth()
            .then(drag.modifier(t.id, i, shape))
            .tile(p, shape, p.cardBrush(t.color), elevation = 14.dp)
            .drawBehind { frost(frost, p, 24.dp.toPx()) }
            // A long-press that turned into a drag isn't a tap.
            .squish(scaleDown = 0.975f) { if (drag.draggingId == null) state.sheet = Sheet.Edit(t.id) }
            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = 500f))
            .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SpotNumber(i + 1)
            Spacer(Modifier.width(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                t.deadline?.let { CardChip(Icons.clock, Logic.due(it, today), urgent = Logic.urgent(it, today)) } ?: CardChip(Icons.horizon, t.horizon.label.lowercase())
                t.remindAt?.takeIf { it > System.currentTimeMillis() }?.let { CardChip(Icons.bell, Logic.fmtWhen(it, today)) }
            }
        }
        Txt(t.title, Type.card(24.sp), p.cardInk, Modifier.padding(top = 10.dp, bottom = 14.dp))
        when {
            state.askNext == t.id -> NextAsk(state, t)
            t.next != null -> StepButton(state, t)
            else -> Box(
                Modifier.fillMaxWidth().drawBehind {
                    drawRoundRect(p.cardInk.copy(alpha = 0.3f), cornerRadius = CornerRadius(14.dp.toPx()),
                        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
                }.squish { state.askNext = t.id }.padding(12.dp),
                contentAlignment = Alignment.Center,
            ) { Txt("+ Add a next step", Type.body(14.sp, FontWeight.Bold), p.cardInk.copy(alpha = 0.8f)) }
        }
        DrainMeter(t)
        if (frostTarget > 0f) {
            Row(
                Modifier.padding(top = 12.dp).tile(p, PillShape, p.frostSpark, elevation = 3.dp)
                    .squish { state.thaw(t.id) }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ico(Icons.snow, p.cardInk, 14.dp, stroke = 2.4f)
                Spacer(Modifier.width(6.dp))
                Txt("${Logic.plural(Logic.untouched(t, today), "day")} untouched", Type.body(12.sp, FontWeight.Bold), p.cardInk)
                Spacer(Modifier.width(12.dp))
                Txt("Thaw", Type.body(12.sp, FontWeight.ExtraBold).copy(textDecoration = TextDecoration.Underline), p.cardInk)
            }
        }
    }
}

@Composable
private fun StepButton(state: AppState, t: Task) {
    val p = pal()
    var ticking by remember(t.id, t.next) { mutableStateOf(false) }
    val tick by animateFloatAsState(if (ticking) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "tick")
    LaunchedEffect(ticking) { if (ticking) { delay(380); state.stepDone(t.id) } }
    Row(
        Modifier.fillMaxWidth().inset(p, BtnShape, p.chip).squish(enabled = !ticking) { ticking = true }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(26.dp).graphicsLayer { scaleX = 1f + tick * 0.18f; scaleY = 1f + tick * 0.18f }.background(p.hi, CircleShape), contentAlignment = Alignment.Center) {
            if (tick > 0.05f) Ico(Icons.check, p.cardInk, 16.dp, stroke = 3f, modifier = Modifier.graphicsLayer { scaleX = tick; scaleY = tick })
        }
        Spacer(Modifier.width(12.dp))
        Txt(t.next.orEmpty(), Type.body(16.sp, FontWeight.Bold).copy(textDecoration = if (ticking) TextDecoration.LineThrough else null),
            p.cardInk.copy(alpha = if (ticking) 0.55f else 1f), Modifier.weight(1f))
        val more = t.steps.size - 1
        if (more > 0) Txt("+$more", Type.body(14.sp, FontWeight.ExtraBold), p.cardInk.copy(alpha = 0.6f), Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun NextAsk(state: AppState, t: Task) {
    val p = pal()
    var text by remember(t.id) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // Drop focus first so the Enter that confirmed this can't land on whatever gets focus next.
    val save = { focusManager.clearFocus(force = true); state.setNext(t.id, text) }
    Column {
        Field(text, { text = it }, "What's next?", Modifier.focusRequester(focus), onDone = save, color = p.hi, textColor = p.cardInk)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClayButton("Save", kind = BtnKind.PRIMARY, onClick = save)
            ClayButton("Skip", kind = BtnKind.SOFT) { focusManager.clearFocus(force = true); state.askNext = null }
        }
    }
}

/** Chunks that empty as steps get done. The total comes from the task's rough size. */
@Composable
private fun DrainMeter(t: Task) {
    val p = pal()
    val (left, total) = Logic.drain(t)
    val shown by animateFloatAsState(left.toFloat(), spring(dampingRatio = 0.6f, stiffness = 200f), label = "drain")
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).heightIn(min = 10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (k in 0 until total) {
                val fill = (shown - k).coerceIn(0f, 1f)
                Box(Modifier.weight(1f).height(8.dp).background(p.chip, PillShape)) {
                    Box(Modifier.fillMaxWidth(fill).height(8.dp).background(p.cardInk.copy(alpha = 0.32f), PillShape))
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Txt(if (left == 0) "Drained. Finish it?" else "$left of $total left", Type.body(12.sp, FontWeight.ExtraBold), p.cardInk.copy(alpha = 0.6f))
    }
}

@Composable
private fun EmptySpot(i: Int, waiting: Int, onWaiting: () -> Unit, onClick: () -> Unit) {
    val p = pal()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 100.dp)
            .drawBehind {
                drawRoundRect(p.line, cornerRadius = CornerRadius(24.dp.toPx()),
                    style = Stroke(2.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
            }
            .squish(scaleDown = 0.97f, onClick = onClick).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpotNumber(i + 1, empty = true)
        Spacer(Modifier.width(14.dp))
        Txt("Empty spot", Type.body(16.sp, FontWeight.Bold), p.inkSoft, Modifier.weight(1f))
        if (waiting > 0) ClayButton("$waiting waiting", icon = Icons.hourglass, onClick = onWaiting)
    }
}

/** The next seven days at a glance: today highlighted, a mark on days with deadlines. Tap for Coming up. */
@Composable
private fun WeekStrip(state: AppState) {
    val p = pal()
    val today = state.today
    val live = state.data.tasks.filter { it.deadline != null && it.isOpen }
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).squish(scaleDown = 0.98f) { state.go(Screen.COMING) },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (i in 0 until 7) {
            val d = today + i
            val date = Logic.date(d)
            val on = i == 0
            val items = live.filter { it.deadline == d }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Txt(date.dayOfWeek.name.take(1), Type.body(12.sp, FontWeight.Bold), p.inkSoft)
                Box(
                    Modifier.padding(top = 2.dp).size(34.dp).then(if (on) Modifier.border(2.dp, p.ink, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) { Txt("${date.dayOfMonth}", Type.display(16.sp), p.ink) }
                Row(Modifier.height(8.dp).padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    items.take(3).forEach { t ->
                        val c = if (t.kind == Kind.BIG) p.dotBig else p.dotSmall
                        if (t.kind == Kind.BIG) Box(Modifier.size(6.dp).background(c, CircleShape))
                        else Box(Modifier.size(6.dp).border(1.5.dp, c, CircleShape))
                    }
                }
            }
        }
    }
}

/**
 * Long-press a card, then drag it to another spot. The long-press wait means a quick tap still opens the card
 * and a swipe still scrolls the page, so nothing moves by accident.
 */
class CardDrag(
    private val state: AppState,
    private val order: List<String>,
    private val gapPx: Float,
) {
    var draggingId by mutableStateOf<String?>(null); private set
    private var dy by mutableFloatStateOf(0f)
    private val heights = mutableStateMapOf<String, Int>()

    private fun from() = order.indexOf(draggingId)

    /** Which spot the dragged card would land in right now. */
    private fun target(): Int {
        val f = from(); if (f < 0) return -1
        var t = f
        var acc = 0f
        if (dy > 0) for (j in f + 1 until order.size) { val h = (heights[order[j]] ?: 0) + gapPx; acc += h; if (dy > acc - h / 2) t = j }
        else for (j in f - 1 downTo 0) { val h = (heights[order[j]] ?: 0) + gapPx; acc += h; if (-dy > acc - h / 2) t = j }
        return t
    }

    /** How far a card that isn't being dragged should slide to make room. */
    private fun shift(i: Int): Float {
        val f = from(); if (f < 0) return 0f
        val t = target(); val h = (heights[draggingId] ?: 0) + gapPx
        return when { t > f && i in f + 1..t -> -h; t < f && i in t until f -> h; else -> 0f }
    }

    fun modifier(id: String, i: Int, shape: androidx.compose.ui.graphics.Shape): Modifier = Modifier.composed {
        val dragging = draggingId == id
        val slide by animateFloatAsState(if (dragging) 0f else shift(i), spring(dampingRatio = 0.8f, stiffness = 500f), label = "slide")
        val lift by animateFloatAsState(if (dragging) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "lift")
        val p = pal()
        Modifier
            .zIndex(if (dragging) 1f else 0f)
            .onGloballyPositioned { heights[id] = it.size.height }
            .graphicsLayer { translationY = if (dragging) dy else slide; scaleX = 1f + 0.03f * lift; scaleY = scaleX }
            .then(if (lift > 0.01f) Modifier.shadow((16 * lift).dp, shape, ambientColor = p.drop, spotColor = p.drop) else Modifier)
            .pointerInput(id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { draggingId = id; dy = 0f },
                    onDrag = { change, amount ->
                        change.consume(); dy += amount.y
                    },
                    onDragEnd = { finish(true) },
                    onDragCancel = { finish(false) },
                )
            }
    }

    private fun finish(drop: Boolean) {
        val id = draggingId ?: return
        val f = from(); val t = target()
        draggingId = null; dy = 0f
        if (drop && t >= 0 && t != f) state.reorder(id, t)
    }
}

@Composable
fun rememberCardDrag(state: AppState, active: List<Task>): CardDrag {
    val gap = with(androidx.compose.ui.platform.LocalDensity.current) { 18.dp.toPx() }
    val order = active.map { it.id }
    return remember(order, gap) { CardDrag(state, order, gap) }
}
