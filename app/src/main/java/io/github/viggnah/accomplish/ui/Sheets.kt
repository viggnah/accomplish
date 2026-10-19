package io.github.viggnah.accomplish.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.viggnah.accomplish.data.Horizon
import io.github.viggnah.accomplish.data.Kind
import io.github.viggnah.accomplish.data.Size
import io.github.viggnah.accomplish.data.Status
import io.github.viggnah.accomplish.data.Task
import io.github.viggnah.accomplish.logic.Logic
import java.time.LocalDate

/** "When?" has three kinds of answer: a date, this month, or later. Picking one replaces the others. */
private enum class When { DATE, MONTH, LATER }

@Composable
private fun WhenRow(state: AppState, horizon: Horizon, deadline: Long?, today: Long, onHorizon: (Horizon) -> Unit, onDeadline: (Long?) -> Unit) {
    val ctx = LocalContext.current
    val pick = {
        val d = LocalDate.ofEpochDay(deadline ?: today)
        DatePickerDialog(ctx, { _, y, m, day -> onDeadline(LocalDate.of(y, m + 1, day).toEpochDay()) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
    }
    val selected = when {
        deadline != null -> When.DATE
        horizon == Horizon.MONTH -> When.MONTH
        horizon == Horizon.SOMEDAY -> When.LATER
        else -> null // older tasks marked Today or This week without a date
    }
    Segmented(
        listOf<Pair<When?, String>>(When.DATE to (deadline?.let { Logic.fmtShort(it) } ?: "Date"), When.MONTH to "This month", When.LATER to "Later"),
        selected,
    ) { w ->
        when (w) {
            When.DATE -> pick()
            When.MONTH, When.LATER -> {
                onHorizon(if (w == When.MONTH) Horizon.MONTH else Horizon.SOMEDAY)
                if (deadline != null) { onDeadline(null); state.offerUndo("Date removed") { onDeadline(deadline) } }
            }
            null -> {}
        }
    }
}

/** A one-off reminder: pick a day, then a time. Shows when it's set, with × to remove (undoable). */
@Composable
private fun RemindRow(state: AppState, remindAt: Long?, onChange: (Long?) -> Unit) {
    val p = pal()
    val ctx = LocalContext.current
    val pick = {
        val zone = java.time.ZoneId.systemDefault()
        val start = remindAt?.let { java.time.Instant.ofEpochMilli(it).atZone(zone) } ?: java.time.ZonedDateTime.now(zone).plusHours(1).withMinute(0)
        DatePickerDialog(ctx, { _, y, m, day ->
            android.app.TimePickerDialog(ctx, { _, h, min ->
                onChange(LocalDate.of(y, m + 1, day).atTime(h, min).atZone(zone).toInstant().toEpochMilli())
            }, start.hour, start.minute, android.text.format.DateFormat.is24HourFormat(ctx)).show()
        }, start.year, start.monthValue - 1, start.dayOfMonth).show()
    }
    if (remindAt == null) {
        ClayButton("Add a reminder", icon = Icons.bell, onClick = pick)
    } else {
        Row(Modifier.background(p.bg2, BtnShape).border(1.5.dp, p.ink, BtnShape).squish(onClick = pick).padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Ico(Icons.bell, p.ink, 16.dp, stroke = 2.4f)
            Spacer(Modifier.width(8.dp))
            Txt(Logic.fmtWhen(remindAt, state.today), Type.display(14.sp), p.ink, maxLines = 1)
            Box(Modifier.size(44.dp).squish { onChange(null); state.offerUndo("Reminder removed") { onChange(remindAt) } }, contentAlignment = Alignment.Center) {
                Ico(Icons.close, p.ink, 13.dp, stroke = 2.6f)
            }
        }
    }
}

/** Planned steps in order. The first one is what shows on the card; ticking it brings up the next. */
@Composable
private fun StepsEditor(state: AppState, steps: List<String>, onChange: (List<String>) -> Unit) {
    val p = pal()
    steps.forEachIndexed { i, step ->
        Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt("${i + 1}", Type.display(14.sp), p.inkSoft, Modifier.width(20.dp))
            Field(step, { v -> onChange(steps.toMutableList().also { it[i] = v }) }, if (i == 0) "The smallest thing that moves it" else "And then…",
                Modifier.weight(1f), singleLine = false)
            Spacer(Modifier.width(2.dp))
            TapBox({
                val before = steps
                onChange(steps.filterIndexed { j, _ -> j != i })
                if (step.isNotBlank()) state.offerUndo("Step removed") { onChange(before) }
            }) {
                Box(Modifier.size(38.dp).background(p.bg2, BtnShape), contentAlignment = Alignment.Center) { Ico(Icons.close, p.inkSoft, 14.dp, stroke = 2.6f) }
            }
        }
    }
    ClayButton(if (steps.isEmpty()) "Add a step" else "Add another", kind = BtnKind.PLAIN, icon = Icons.plus) { onChange(steps + "") }
}

@Composable
private fun SizePicker(size: Size, onPick: (Size) -> Unit) {
    Segmented(Size.entries.map { it to "${it.label} (${it.steps})" }, size, onSelect = onPick)
}

// ---------------- New task ----------------

@Composable
fun CaptureSheet(state: AppState, initialKind: Kind, spot: Int? = null) {
    val s = state.settings
    var title by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(initialKind) }
    var horizon by remember { mutableStateOf(Horizon.SOMEDAY) }
    var deadline by remember { mutableStateOf<Long?>(null) }
    var size by remember { mutableStateOf(Size.M) }
    var steps by remember { mutableStateOf(listOf<String>()) }
    var remindAt by remember { mutableStateOf<Long?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(250); runCatching { focus.requestFocus() } }
    // Saves when closed, like edits. Nothing typed means nothing saved.
    val latest by rememberUpdatedState({ if (title.isNotBlank()) state.capture(title, kind, horizon, deadline, size, spot, steps, remindAt) })
    SideEffect { if (state.sheet is Sheet.Capture) state.sheetCloser = latest }
    SheetTitle("New task", onClose = { state.closeSheet() }, confirm = if (title.isNotBlank()) "Add" else null)
    Spacer(Modifier.height(14.dp))
    Field(title, { title = it }, "What needs doing?", Modifier.focusRequester(focus), onDone = { state.closeSheet() })
    if (spot == null) {
        Spacer(Modifier.height(12.dp))
        Segmented(listOf(Kind.SMALL to s.small, Kind.BIG to s.big), kind) { kind = it }
    }
    SectionLabel("When")
    WhenRow(state, horizon, deadline, state.today, { horizon = it }, { deadline = it })
    Spacer(Modifier.height(10.dp))
    RemindRow(state, remindAt) { remindAt = it }
    if (kind == Kind.BIG) {
        SectionLabel("Next steps")
        StepsEditor(state, steps) { steps = it }
        SectionLabel("Rough size")
        SizePicker(size) { size = it }
    }
    val waiting = state.waiting.size
    if (spot != null && waiting > 0) {
        Spacer(Modifier.height(18.dp))
        ClayButton("Or place one from the ${s.waiting} ($waiting)", Modifier.fillMaxWidth(), kind = BtnKind.GHOST) { state.dropSheet(); state.go(Screen.WAITING) }
    }
}

@Composable
private fun SheetFoot(content: @Composable () -> Unit) {
    val p = pal()
    Spacer(Modifier.height(26.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
    Spacer(Modifier.height(18.dp))
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

// ---------------- Edit a task (saves when closed) ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditSheet(state: AppState, id: String) {
    val p = pal()
    val s = state.settings
    val t = state.task(id) ?: run { state.dropSheet(); return }
    // Decide once, so a task finished from this sheet doesn't flip to the finished view while sliding away.
    val wasOpen = remember(id) { t.isOpen }
    if (!wasOpen) { FinishedSheet(state, t); return }
    var draft by remember(id) { mutableStateOf(state.draftOf(t)) }
    var allEvents by remember(id) { mutableStateOf(false) }
    val latest by rememberUpdatedState(draft)
    // Only while this sheet is the open one; it keeps recomposing during its exit animation.
    SideEffect { if (state.sheet == Sheet.Edit(id)) state.sheetCloser = { state.saveDraft(id, latest) } }
    val big = t.kind == Kind.BIG

    SheetTitle(if (big) s.big else s.small, onClose = { state.closeSheet() }, swatch = if (big) p.cardBrush(draft.color) else null)
    Spacer(Modifier.height(14.dp))
    Field(draft.title, { draft = draft.copy(title = it) }, "What needs doing?", singleLine = false)
    if (big) {
        SectionLabel("Next steps")
        StepsEditor(state, draft.steps) { draft = draft.copy(steps = it) }
    }
    SectionLabel("When")
    WhenRow(state, draft.horizon, draft.deadline, state.today, { draft = draft.copy(horizon = it) }, { draft = draft.copy(deadline = it) })
    Spacer(Modifier.height(10.dp))
    RemindRow(state, draft.remindAt) { draft = draft.copy(remindAt = it) }
    if (big) {
        SectionLabel("Rough size")
        SizePicker(draft.size) { draft = draft.copy(size = it) }
        SectionLabel("Colour")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (c in 0 until 5) {
                Box(
                    Modifier.size(44.dp)
                        .then(if (draft.color == c) Modifier.background(p.ink, CircleShape).padding(3.dp).background(p.surface, CircleShape).padding(3.dp) else Modifier)
                        .tile(p, CircleShape, p.cardBrush(c), 4.dp).squish { draft = draft.copy(color = c) },
                )
            }
        }
        SectionLabel("Why it matters")
        Field(draft.why, { draft = draft.copy(why = it) }, "Why does this matter to you?", singleLine = false, minLines = 2)
    }
    if (!big) {
        Spacer(Modifier.height(18.dp))
        ClayButton("Promote to ${s.big}", Modifier.fillMaxWidth()) { state.dropSheet(); state.promote(id, latest) }
    }
    Timeline(state, t, allEvents) { allEvents = true }
    SheetFoot {
        ClayButton("Mark as done", Modifier.fillMaxWidth(), kind = BtnKind.PRIMARY, big = true, icon = Icons.check) { state.dropSheet(); state.finish(id, latest) }
        if (big && t.status == Status.ACTIVE) {
            ClayButton("Move to the ${s.waiting}", Modifier.fillMaxWidth(), kind = BtnKind.GHOST) { state.dropSheet(); state.saveDraft(id, latest.copy(place = null)) }
        }
        if (big && t.status == Status.WAITING) {
            ClayButton("Place on Home", Modifier.fillMaxWidth(), kind = BtnKind.GHOST) { state.dropSheet(); state.saveDraft(id, latest); state.go(Screen.WAITING); state.placeFor = id }
        }
        ClayButton("Let go", Modifier.fillMaxWidth(), kind = BtnKind.GHOST) { state.dropSheet(); state.letGo(id, latest) }
    }
}

/** Only speak up when the choice bumps another task off Home. */
private fun placeHint(state: AppState, t: Task, place: Int?): String? {
    val a = state.active
    if (t.status == Status.ACTIVE || place == null || a.size < state.settings.slots) return null
    return "“${a[minOf(place, a.size - 1)].title}” goes to the ${state.settings.waiting}."
}

@Composable
private fun FinishedSheet(state: AppState, t: Task) {
    val p = pal()
    val s = state.settings
    SideEffect { state.sheetCloser = null }
    SheetTitle(t.title, onClose = { state.closeSheet() }, swatch = if (t.kind == Kind.BIG) p.cardBrush(t.color) else null)
    Spacer(Modifier.height(12.dp))
    Box(Modifier.background(p.bg2, PillShape).padding(horizontal = 12.dp, vertical = 5.dp)) {
        Txt("${if (t.status == Status.DONE) "Done" else "Let go"} ${Logic.ago(t.doneAt ?: state.today, state.today)}",
            Type.body(14.sp, FontWeight.ExtraBold), p.ink)
    }
    t.why?.let { SectionLabel("Why it mattered"); Txt(it, Type.body(16.sp), p.ink) }
    Timeline(state, t, true) {}
    SheetFoot {
        ClayButton("Restore", Modifier.fillMaxWidth(), kind = BtnKind.PRIMARY, big = true) { state.dropSheet(); state.restore(t.id) }
    }
}

@Composable
private fun Timeline(state: AppState, t: Task, all: Boolean, onMore: () -> Unit) {
    val p = pal()
    val items = t.log.reversed()
    val shown = if (all) items else items.take(5)
    SectionLabel("Timeline")
    shown.forEachIndexed { i, e ->
        Row {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.padding(top = 5.dp).size(10.dp).background(p.inkSoft, CircleShape))
                if (i < shown.lastIndex) Box(Modifier.width(1.5.dp).height(34.dp).background(p.line))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.padding(bottom = 10.dp)) {
                Txt(Logic.describe(e, state.settings), Type.body(14.sp), p.ink)
                Txt(Logic.fmtLong(e.day), Type.body(12.sp, FontWeight.Bold), p.inkSoft)
            }
        }
    }
    if (items.size > shown.size) Txt("Show all ${items.size} events", Type.body(14.sp, FontWeight.ExtraBold), p.inkSoft, Modifier.padding(start = 22.dp).squish(onClick = onMore))
}
