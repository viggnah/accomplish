package io.github.viggnah.accomplish.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.viggnah.accomplish.data.Horizon
import io.github.viggnah.accomplish.data.Kind
import io.github.viggnah.accomplish.data.Status
import io.github.viggnah.accomplish.data.Task
import io.github.viggnah.accomplish.logic.Logic
import kotlinx.coroutines.delay

// ---------------- Small stuff: plain grouped rows, today first ----------------

@Composable
fun SmallScreen(state: AppState) {
    val p = pal()
    val today = state.today
    val groups = state.smalls.groupBy { Logic.effHorizon(it, today) }.mapValues { (_, v) -> v.sortedBy { it.deadline ?: Long.MAX_VALUE } }
    PageColumn {
        Header(state, state.settings.small)
        ScreenBody {
            for (h in Horizon.entries) {
                val list = groups[h].orEmpty()
                if (list.isEmpty() && h != Horizon.TODAY) continue
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = if (h == Horizon.TODAY) 4.dp else 22.dp, bottom = 8.dp)) {
                    Txt(h.label, Type.label, if (h == Horizon.TODAY) p.ink else p.inkSoft, Modifier.weight(1f))
                    if (list.isNotEmpty()) Txt("${list.size}", Type.label, p.inkSoft)
                }
                val shape = PanelShape
                Column(
                    Modifier.fillMaxWidth().background(if (h == Horizon.TODAY) p.pool else p.surface, shape)
                        .then(if (p.dark) Modifier.border(1.dp, p.edge, shape) else Modifier),
                ) {
                    if (list.isEmpty()) EmptyNote("Nothing for today", Modifier.padding(16.dp),
                        action = if (state.smalls.isEmpty()) "Add ${state.settings.small.lowercase()}" else null) { state.sheet = Sheet.Capture(Kind.SMALL) }
                    list.forEachIndexed { i, t ->
                        if (i > 0) Box(Modifier.padding(start = 56.dp).fillMaxWidth().height(1.dp).background(p.line))
                        SmallRow(state, t)
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallRow(state: AppState, t: Task) {
    val p = pal()
    val s = state.settings
    val today = state.today
    val heat = Logic.heat(t, s, today)
    val fade = Logic.fade(t, s, today)
    val ask = Logic.asks(t, s, today)
    val u = Logic.untouched(t, today)
    var checked by remember(t.id) { mutableStateOf(false) }
    val pop by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "pop")
    LaunchedEffect(checked) { if (checked) { delay(380); state.finish(t.id) } }
    val reminder = t.remindAt?.takeIf { it > System.currentTimeMillis() }?.let { "reminder ${Logic.fmtWhen(it, today)}" }
    val meta = when {
        reminder != null && t.deadline != null -> Logic.due(t.deadline, today) + ", " + reminder
        reminder != null -> reminder
        t.deadline != null -> Logic.due(t.deadline, today)
        fade > 0f && !ask -> "Untouched for ${Logic.plural(u, "day")}"
        else -> null
    }
    val urgent = t.deadline?.let { Logic.urgent(it, today) } == true
    Column(Modifier.fillMaxWidth().graphicsLayer { alpha = if (ask) 1f else 1f - fade * 0.55f }.padding(start = 4.dp, end = 14.dp, top = 2.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TapBox({ checked = true }, enabled = !checked) {
                Box(
                    Modifier.size(28.dp).graphicsLayer { scaleX = 1f + pop * 0.12f; scaleY = 1f + pop * 0.12f }
                        .then(if (checked) Modifier.background(p.ink, CircleShape) else Modifier.border(2.dp, p.inkSoft, CircleShape)),
                    contentAlignment = Alignment.Center,
                ) { if (checked) Ico(Icons.check, p.bg, 15.dp, stroke = 3f) }
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f).squish(scaleDown = 0.98f) { state.sheet = Sheet.Edit(t.id) }) {
                Txt(t.title, Type.body(16.sp, FontWeight.Bold).copy(textDecoration = if (checked) TextDecoration.LineThrough else null), p.ink)
                if (meta != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    if (urgent) { Ico(Icons.clock, p.urgentInk, 13.dp, stroke = 2.6f); Spacer(Modifier.width(5.dp)) }
                    else if (heat > 0f) { Box(Modifier.size(8.dp).graphicsLayer { alpha = 0.4f + 0.6f * heat }.background(p.heat, CircleShape)); Spacer(Modifier.width(6.dp)) }
                    Txt(meta, Type.body(12.sp, FontWeight.Bold), if (urgent) p.urgentInk else if (heat > 0.5f) p.heat else p.inkSoft)
                }
            }
        }
        if (ask) {
            Row(Modifier.padding(start = 52.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt("Untouched ${Logic.plural(u, "day")}. Still want it?", Type.body(14.sp, FontWeight.Bold), p.ink, Modifier.weight(1f))
                ClayButton("Keep") { state.keep(t.id) }
                ClayButton("Let go", kind = BtnKind.GHOST) { state.letGo(t.id) }
            }
        }
    }
}

// ---------------- Waiting room ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WaitingScreen(state: AppState) {
    val p = pal()
    val s = state.settings
    val w = state.waiting
    val a = state.active
    val free = s.slots - a.size
    PageColumn {
        Header(state, s.waiting)
        ScreenBody {
            if (w.isEmpty()) EmptyNote("Nothing waiting", action = "New ${s.big} task") { state.sheet = Sheet.Capture(Kind.BIG) }
            w.forEach { t ->
                Column(
                    Modifier.fillMaxWidth()
                        .tile(p, PanelShape, lerp(p.bg, p.cardColor(t.color), p.waitingMix))
                        .squish(scaleDown = 0.98f) { state.sheet = Sheet.Edit(t.id) }
                        .padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Txt(t.title, Type.card(18.sp), p.cardInk)
                            FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                t.deadline?.let { CardChip(Icons.clock, Logic.due(it, state.today), urgent = Logic.urgent(it, state.today)) } ?: CardChip(Icons.horizon, t.horizon.label.lowercase())
                                CardChip(Icons.hourglass, t.size.label.lowercase())
                            }
                        }
                        if (state.placeFor != t.id) {
                            Spacer(Modifier.width(10.dp))
                            ClayButton("Place on Home", kind = BtnKind.PRIMARY) { state.placeFor = t.id }
                        }
                    }
                    if (state.placeFor == t.id) {
                        val full = a.size >= s.slots
                        Txt(if (full) "Home is full. Swap with:" else "Which spot?",
                            Type.body(14.sp, FontWeight.ExtraBold), p.cardInk, Modifier.padding(top = 12.dp, bottom = 8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (full) a.forEachIndexed { i, x -> ClayButton(x.title.take(28), kind = BtnKind.SOFT) { state.place(t.id, i) } }
                            else for (i in 0..a.size) ClayButton("Spot ${i + 1}", kind = BtnKind.SOFT) { state.place(t.id, i) }
                            ClayButton("Cancel", kind = BtnKind.GHOST) { state.placeFor = null }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

// ---------------- Coming up: deadline calendar ----------------

@Composable
fun ComingScreen(state: AppState) {
    val p = pal()
    val s = state.settings
    val today = state.today
    val live = state.data.tasks.filter { it.deadline != null && it.isOpen }
    val start = today - (Logic.date(today).dayOfWeek.value - 1)
    val weeks = 6
    var picked by remember { mutableLongStateOf(-1L) }
    PageColumn {
        Header(state, "Coming up")
        ScreenBody {
            Column(Modifier.fillMaxWidth().tile(p, PanelShape, p.surface, 8.dp).padding(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Mark(Kind.BIG); Spacer(Modifier.width(6.dp)); Txt(s.big, Type.body(12.sp, FontWeight.Bold), p.inkSoft) }
                    Row(verticalAlignment = Alignment.CenterVertically) { Mark(Kind.SMALL); Spacer(Modifier.width(6.dp)); Txt(s.small, Type.body(12.sp, FontWeight.Bold), p.inkSoft) }
                }
                Spacer(Modifier.height(10.dp))
                Row { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Txt(it, Type.body(12.sp, FontWeight.ExtraBold), p.inkSoft, Modifier.weight(1f), align = TextAlign.Center) } }
                Spacer(Modifier.height(4.dp))
                for (wk in 0 until weeks) {
                    Row(Modifier.padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (dw in 0 until 7) {
                            val d = start + wk * 7 + dw
                            val items = live.filter { it.deadline == d }
                            val date = Logic.date(d)
                            val isToday = d == today
                            val on = d == picked
                            Column(
                                Modifier.weight(1f).height(44.dp)
                                    .background(if (on) p.ink else p.bg2, BtnShape)
                                    .then(if (isToday) Modifier.border(2.dp, p.ink, BtnShape) else Modifier)
                                    .graphicsLayer { alpha = if (d < today) 0.4f else 1f }
                                    .then(if (items.isNotEmpty()) Modifier.squish { picked = if (picked == d) -1 else d } else Modifier),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Txt(if (date.dayOfMonth == 1) date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() } else "${date.dayOfMonth}",
                                    Type.body(12.sp, FontWeight.Bold), if (on) p.bg else p.ink)
                                Row(Modifier.height(10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    items.take(3).forEach { Mark(it.kind, if (on) p.bg else null) }
                                }
                            }
                        }
                    }
                }
                // Past-due ones lead the list (in red), since they're the most pressing.
                val shown = live.filter { val dl = it.deadline!!; if (picked >= 0) dl == picked else dl < start + weeks * 7 }.sortedBy { it.deadline }
                Spacer(Modifier.height(14.dp))
                if (shown.isEmpty()) Txt("No deadlines coming up", Type.body(14.sp), p.inkSoft)
                shown.forEach { t ->
                    Row(Modifier.fillMaxWidth().squish(scaleDown = 0.98f) { state.sheet = Sheet.Edit(t.id) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        val left = t.deadline!! - today
                        Txt(if (left <= 6) Logic.fmtWeekday(t.deadline).take(3) + " " + Logic.date(t.deadline).dayOfMonth else Logic.fmtShort(t.deadline),
                            Type.body(12.sp, FontWeight.Bold), p.inkSoft, Modifier.width(64.dp))
                        Mark(t.kind)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Txt(t.title, Type.body(14.sp, FontWeight.Bold), p.ink)
                            Txt(Logic.due(t.deadline, today) + if (t.status == Status.WAITING) ", in the ${s.waiting}" else "", Type.body(12.sp),
                                if (Logic.urgent(t.deadline, today)) p.urgentInk else p.inkSoft)
                        }
                    }
                }
            }
        }
    }
}

/** Filled dot for big tasks, hollow ring for small ones, so the kinds differ by shape as well as colour. */
@Composable
private fun Mark(kind: Kind, color: Color? = null) {
    val p = pal()
    if (kind == Kind.BIG) Box(Modifier.size(9.dp).background(color ?: p.dotBig, CircleShape))
    else Box(Modifier.size(9.dp).drawBehind { drawCircle(color ?: p.dotSmall, size.minDimension / 2 - 1.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())) })
}

// ---------------- History ----------------

@Composable
fun HistoryScreen(state: AppState) {
    val p = pal()
    val s = state.settings
    var filter by remember { mutableStateOf<Kind?>(null) }
    val items = Logic.finished(state.data).filter { filter == null || it.kind == filter }
    PageColumn {
        Header(state, s.history)
        ScreenBody {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OptChip("All", filter == null) { filter = null }
                OptChip(s.big, filter == Kind.BIG) { filter = Kind.BIG }
                OptChip(s.small, filter == Kind.SMALL) { filter = Kind.SMALL }
            }
            if (items.isEmpty()) EmptyNote(if (filter == null) "Nothing done or let go yet" else "None yet", Modifier.padding(top = 18.dp))
            for ((label, st) in listOf("Done" to Status.DONE, "Let go" to Status.LET_GO)) {
                val group = items.filter { it.status == st }
                if (group.isEmpty()) continue
                Txt(label, Type.display(16.sp), p.inkSoft, Modifier.padding(top = 20.dp, bottom = 4.dp))
                group.forEach { t ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).background(if (t.kind == Kind.BIG) p.cardBrush(t.color) else Brush.linearGradient(listOf(p.line, p.line)), CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f).squish(scaleDown = 0.98f) { state.sheet = Sheet.Edit(t.id) }) {
                            Txt(t.title, Type.body(16.sp, FontWeight.Bold), p.ink)
                            Txt(Logic.ago(t.doneAt ?: state.today, state.today).replaceFirstChar { it.uppercase() } +
                                (t.stepsDone.takeIf { it > 0 }?.let { ", ${Logic.plural(it, "step")}" } ?: ""), Type.body(12.sp), p.inkSoft)
                        }
                        ClayButton("Restore") { state.restore(t.id) }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
                }
            }
        }
    }
}
