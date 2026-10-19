package io.github.viggnah.accomplish.logic

import io.github.viggnah.accomplish.data.AppData
import io.github.viggnah.accomplish.data.Kind
import io.github.viggnah.accomplish.data.Settings
import io.github.viggnah.accomplish.data.Status
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.random.Random

/** One notification we may send. [key] makes it send-once; lower [priority] wins when the daily limit bites. */
data class Nudge(
    val key: String, val at: Long, val priority: Int, val title: String, val text: String, val channel: String,
    /** What kind of nudge, for its icon: reminder, deadline, frost, review, prompt. */
    val type: String = "",
    /** The task's card colour index, when there is one. */
    val color: Int? = null,
    /** An extra line, usually the next step. */
    val detail: String? = null,
    /** Where a tap takes you: "home", "small", "waiting", or "new" for the add-a-task sheet. */
    val screen: String = "home",
)

/** Works out which notifications are coming up. Pure, so it can be tested; the Android side only posts and schedules. */
object Reminders {
    const val REMINDERS = "reminders"
    const val DEADLINES = "deadlines"
    const val NUDGES = "nudges"
    /** A missed notification (phone off, say) still goes out if it's no older than this. */
    const val CATCH_UP_MS = 12L * 60 * 60 * 1000

    fun plan(d: AppData, s: Settings, now: ZonedDateTime, sent: Set<String>): List<Nudge> {
        if (!s.notify) return emptyList()
        val zone = now.zone
        val today = now.toLocalDate()
        val out = mutableListOf<Nudge>()
        fun at(date: LocalDate, hour: Int, minute: Int = 0) = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

        if (s.remindTwoDays || s.remindOneDay) {
            for (t in d.tasks) {
                val dl = t.deadline ?: continue
                if (!t.isOpen || (t.kind == Kind.SMALL && !s.remindSmall)) continue
                val due = LocalDate.ofEpochDay(dl)
                val color = if (t.kind == Kind.BIG) t.color else null
                val next = t.next?.let { "Next: $it" }
                if (s.remindTwoDays) out += Nudge("d2:${t.id}:$dl", at(due.minusDays(2), s.remindHour), 0,
                    t.title, "Due in 2 days, ${Logic.fmtLong(dl)}", DEADLINES, "deadline", color, next, screenFor(t))
                if (s.remindOneDay) out += Nudge("d1:${t.id}:$dl", at(due.minusDays(1), s.remindHour), 0,
                    t.title, "Due tomorrow, ${Logic.fmtLong(dl)}", DEADLINES, "deadline", color, next, screenFor(t))
            }
        }

        if (s.frostNudge && s.frost) {
            for (t in d.tasks.filter { it.kind == Kind.BIG && it.status == Status.ACTIVE }) {
                val startDay = maxOf(t.touched + s.frostStart + 1, today.toEpochDay())
                val days = startDay - t.touched
                out += Nudge("frost:${t.id}:${t.touched}", at(LocalDate.ofEpochDay(startDay), s.remindHour), 1,
                    t.title, "Untouched for $days days", NUDGES, "frost", t.color,
                    t.next?.let { "Next: $it" } ?: "Give it a next step")
            }
        }

        for (i in 0..7) {
            val day = today.plusDays(i.toLong())
            val r = Random(day.toEpochDay() * 31 + 7)
            if (s.weeklyReview && day.dayOfWeek.value == s.reviewDay) {
                out += Nudge("review:${day.toEpochDay()}", at(day, 18 + r.nextInt(3), r.nextInt(60)), 2,
                    "Time for a quick review", "Look over your ${s.big} and add anything new", NUDGES, "review")
            }
            if (s.randomPrompt && r.nextInt(6) == 0) {
                out += Nudge("prompt:${day.toEpochDay()}", at(day, 11 + r.nextInt(8), r.nextInt(60)), 3,
                    "Anything new on your mind?", "Add it now, so it's out of your head", NUDGES, "prompt", screen = "new")
            }
        }

        val earliest = now.toInstant().toEpochMilli() - CATCH_UP_MS
        val planned = out.filter { it.key !in sent && it.at >= earliest }.map { it.copy(at = outOfQuiet(it.at, s, zone)) }
        // Reminders you set yourself go out at the time you chose, quiet hours or not.
        val explicit = d.tasks.filter { it.isOpen && it.remindAt != null }.map { t ->
            Nudge("rem:${t.id}:${t.remindAt}", t.remindAt!!, -1, t.title, t.next?.let { "Next: $it" } ?: "Reminder", REMINDERS, "reminder",
                if (t.kind == Kind.BIG) t.color else null, screen = screenFor(t))
        }.filter { it.key !in sent && it.at >= earliest }
        return (planned + explicit).sortedWith(compareBy({ it.at }, { it.priority }))
    }

    private fun screenFor(t: io.github.viggnah.accomplish.data.Task) = when {
        t.kind == Kind.SMALL -> "small"
        t.status == Status.WAITING -> "waiting"
        else -> "home"
    }

    /** Moves a time that falls in quiet hours to when quiet hours end. */
    fun outOfQuiet(at: Long, s: Settings, zone: java.time.ZoneId): Long {
        val t = java.time.Instant.ofEpochMilli(at).atZone(zone)
        val h = t.hour
        val quiet = if (s.quietStart > s.quietEnd) h >= s.quietStart || h < s.quietEnd else h >= s.quietStart && h < s.quietEnd
        if (!quiet) return at
        val endDate = if (h >= s.quietStart && s.quietStart > s.quietEnd) t.toLocalDate().plusDays(1) else t.toLocalDate()
        return endDate.atTime(s.quietEnd, 0).atZone(zone).toInstant().toEpochMilli()
    }

    /** Alarms fire at or just after their time; this only absorbs clock jitter, so nothing shows early. */
    const val GRACE = 5_000L

    /** What to post right now. Your own reminders always go; the rest honour the daily limit. */
    fun dueNow(plan: List<Nudge>, nowMs: Long, sentToday: Int, maxPerDay: Int): List<Nudge> {
        val due = plan.filter { it.at <= nowMs + GRACE }
        val mine = due.filter { it.channel == REMINDERS }
        val rest = due.filter { it.channel != REMINDERS }.sortedBy { it.priority }.take((maxPerDay - sentToday).coerceAtLeast(0))
        return mine + rest
    }
}
