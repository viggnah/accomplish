package io.github.viggnah.accomplish.logic

import io.github.viggnah.accomplish.data.AppData
import io.github.viggnah.accomplish.data.Event
import io.github.viggnah.accomplish.data.EventType
import io.github.viggnah.accomplish.data.Horizon
import io.github.viggnah.accomplish.data.Kind
import io.github.viggnah.accomplish.data.Settings
import io.github.viggnah.accomplish.data.Status
import io.github.viggnah.accomplish.data.Task
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Pure rules shared by the UI and the tests. Nothing here touches Android. */
object Logic {
    fun active(d: AppData) = d.tasks.filter { it.kind == Kind.BIG && it.status == Status.ACTIVE }.sortedBy { it.slot }
    fun waiting(d: AppData) = d.tasks.filter { it.kind == Kind.BIG && it.status == Status.WAITING }
    fun smalls(d: AppData) = d.tasks.filter { it.kind == Kind.SMALL && it.status == Status.ACTIVE }
    fun finished(d: AppData) = d.tasks.filter { it.status == Status.DONE || it.status == Status.LET_GO }.sortedByDescending { it.doneAt ?: 0 }

    fun untouched(t: Task, today: Long) = max(0L, today - t.touched).toInt()

    /** With a deadline, the deadline alone decides the horizon (and moves it closer as the date nears). */
    fun effHorizon(t: Task, today: Long): Horizon {
        val dl = t.deadline ?: return t.horizon
        val left = dl - today
        return Horizon.entries[when { left <= 1 -> 0; left <= 7 -> 1; left <= 31 -> 2; else -> 3 }]
    }

    /** 0..1, how frosted an untouched big task looks. */
    fun frost(t: Task, s: Settings, today: Long): Float {
        val u = untouched(t, today)
        if (!s.frost || u <= s.frostStart) return 0f
        return min(1f, (u - s.frostStart).toFloat() / max(1, s.frostFull - s.frostStart))
    }

    /** 0..1, how warm a small task with a deadline looks as the date nears. */
    fun heat(t: Task, s: Settings, today: Long): Float {
        val dl = t.deadline ?: return 0f
        if (!s.heat) return 0f
        val left = dl - today
        if (left > s.heatWindow) return 0f
        return (1f - left.toFloat() / (s.heatWindow + 1)).coerceIn(0f, 1f)
    }

    /** 0..1, how faded an ignored small task without a deadline looks. */
    fun fade(t: Task, s: Settings, today: Long): Float {
        val u = untouched(t, today)
        if (t.deadline != null || u <= s.fadeStart) return 0f
        return min(1f, (u - s.fadeStart).toFloat() / max(1, s.askAfter - s.fadeStart))
    }

    fun asks(t: Task, s: Settings, today: Long) = t.deadline == null && untouched(t, today) >= s.askAfter

    /** Steps left before the drain meter is empty, and the total it started with. */
    fun drain(t: Task): Pair<Int, Int> = max(0, t.size.steps - t.stepsDone) to t.size.steps

    // ---- dates ----
    fun date(day: Long): LocalDate = LocalDate.ofEpochDay(day)
    private val short = DateTimeFormatter.ofPattern("d MMM", Locale.UK)
    private val long = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)
    private val weekday = DateTimeFormatter.ofPattern("EEEE", Locale.UK)
    fun fmtShort(day: Long): String = date(day).format(short)
    fun fmtLong(day: Long): String = date(day).format(long)
    fun fmtWeekday(day: Long): String = date(day).format(weekday)

    private val whenFmt = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.UK)
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)

    /** A reminder time: "today 17:00", "tomorrow 09:30" or "Thu 15 Oct, 17:00". */
    fun fmtWhen(ms: Long, today: Long? = null): String {
        val t = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault())
        val day = t.toLocalDate().toEpochDay()
        return when (today) {
            null -> t.format(whenFmt)
            day -> "today ${t.format(timeFmt)}"
            day - 1 -> "tomorrow ${t.format(timeFmt)}"
            else -> t.format(whenFmt)
        }
    }

    /** Due today, tomorrow, or already past. */
    fun urgent(deadline: Long, today: Long) = deadline - today <= 1

    fun due(deadline: Long, today: Long): String {
        val left = deadline - today
        return when {
            left < 0 -> "${plural(-left, "day")} past due"
            left == 0L -> "due today"
            left == 1L -> "due tomorrow"
            left <= 6 -> "due ${fmtWeekday(deadline)}"
            else -> "due ${fmtShort(deadline)}"
        }
    }

    fun ago(day: Long, today: Long): String = when (val n = today - day) {
        in Long.MIN_VALUE..0 -> "today"
        1L -> "yesterday"
        else -> "$n days ago"
    }

    fun plural(n: Long, word: String) = "$n $word${if (n == 1L) "" else "s"}"
    fun plural(n: Int, word: String) = plural(n.toLong(), word)

    // ---- editing ----
    fun Task.logged(type: EventType, today: Long, text: String? = null, num: Long? = null) =
        copy(log = log + Event(today, type, text, num))

    fun update(d: AppData, id: String, f: (Task) -> Task) = d.copy(tasks = d.tasks.map { if (it.id == id) f(it) else it })

    /** Renumber active big tasks 0..n-1 in the given order. */
    private fun withOrder(d: AppData, order: List<String>): AppData {
        val idx = order.withIndex().associate { it.value to it.index }
        return d.copy(tasks = d.tasks.map { t -> idx[t.id]?.let { t.copy(slot = it) } ?: t })
    }

    /**
     * Put a big task in spot [place] (0-based) on Home, or back in the waiting room when [place] is null.
     * Moving within Home shifts the others; coming home to a full Home swaps out whoever holds that spot.
     */
    fun place(d: AppData, id: String, place: Int?, s: Settings, today: Long): AppData {
        val t = d.tasks.first { it.id == id }
        val a = active(d)
        val cur = if (t.status == Status.ACTIVE) a.indexOf(t) else null
        if (place == cur) return d
        if (place == null) {
            val out = update(d, id) { it.copy(status = Status.WAITING).logged(EventType.SENT_TO_WAITING, today) }
            return withOrder(out, a.filter { it.id != id }.map { it.id })
        }
        if (cur != null) {
            val rest = a.filter { it.id != id }.map { it.id }.toMutableList()
            val at = min(place, rest.size)
            rest.add(at, id)
            return withOrder(update(d, id) { it.logged(EventType.MOVED, today, num = at.toLong()) }, rest)
        }
        if (a.size >= s.slots) {
            val at = min(place, a.size - 1)
            val outId = a[at].id
            var nd = update(d, outId) { it.copy(status = Status.WAITING).logged(EventType.SWAPPED_OUT, today, text = t.title) }
            nd = update(nd, id) { it.copy(status = Status.ACTIVE, touched = today).logged(EventType.PLACED, today, num = at.toLong()) }
            return withOrder(nd, a.map { if (it.id == outId) id else it.id })
        }
        val order = a.map { it.id }.toMutableList()
        val at = min(place, order.size)
        order.add(at, id)
        val nd = update(d, id) { it.copy(status = Status.ACTIVE, touched = today).logged(EventType.PLACED, today, num = at.toLong()) }
        return withOrder(nd, order)
    }

    fun describe(e: Event, s: Settings): String = when (e.type) {
        EventType.CREATED -> "Added"
        EventType.CREATED_WAITING -> "Added to the ${s.waiting}"
        EventType.RENAMED -> "Renamed from “${e.text}”"
        EventType.NEXT_SET -> "Next step set: ${e.text}"
        EventType.NEXT_CLEARED -> if (e.text != null) "Step removed: ${e.text}" else "Next step cleared"
        EventType.WHEN_CHANGED -> "When changed to ${e.text?.let { runCatching { Horizon.valueOf(it).label }.getOrNull() } ?: e.text}"
        EventType.DEADLINE_SET -> "Deadline set to ${e.num?.let { fmtShort(it) }}"
        EventType.DEADLINE_REMOVED -> "Deadline removed"
        EventType.PLACED -> "Placed on Home in spot ${(e.num ?: 0) + 1}"
        EventType.MOVED -> "Moved to spot ${(e.num ?: 0) + 1}"
        EventType.SENT_TO_WAITING -> "Moved to the ${s.waiting}"
        EventType.SWAPPED_OUT -> "Swapped out to the ${s.waiting} for “${e.text}”"
        EventType.STEP_DONE -> "Step done: ${e.text}"
        EventType.THAWED -> "Thawed"
        EventType.KEPT -> "Kept when asked “Still want this?”"
        EventType.DONE -> "Marked done"
        EventType.LET_GO -> "Let go"
        EventType.RESTORED -> "Restored"
        EventType.PROMOTED -> "Promoted to ${s.big}"
        EventType.RESIZED -> "Size changed to ${e.text}"
        EventType.WHY_SET -> "Added why it matters"
        EventType.REMINDER_SET -> "Reminder set for ${e.num?.let { fmtWhen(it) }}"
        EventType.REMINDER_REMOVED -> "Reminder removed"
    }
}
