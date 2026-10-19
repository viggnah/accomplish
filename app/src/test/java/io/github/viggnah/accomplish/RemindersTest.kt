package io.github.viggnah.accomplish

import io.github.viggnah.accomplish.data.AppData
import io.github.viggnah.accomplish.data.Horizon
import io.github.viggnah.accomplish.data.Kind
import io.github.viggnah.accomplish.data.Settings
import io.github.viggnah.accomplish.data.Status
import io.github.viggnah.accomplish.data.Task
import io.github.viggnah.accomplish.logic.Reminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class RemindersTest {
    private val zone = ZoneId.of("Asia/Colombo")
    private val now = ZonedDateTime.of(2026, 10, 13, 8, 30, 0, 0, zone)
    private val today = now.toLocalDate().toEpochDay()
    private val quiet = Settings(weeklyReview = false, randomPrompt = false, frostNudge = false)

    private fun task(id: String, kind: Kind, deadline: Long?, status: Status = Status.ACTIVE, touched: Long = today) =
        Task(id, kind, "Task $id", status, Horizon.WEEK, deadline = deadline, touched = touched)

    private fun hour(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone)

    @Test fun deadlineGetsTwoDayAndOneDayReminders() {
        val plan = Reminders.plan(AppData(listOf(task("a", Kind.BIG, today + 5))), quiet, now, emptySet())
        assertEquals(listOf("d2:a:${today + 5}", "d1:a:${today + 5}"), plan.map { it.key })
        assertEquals(LocalDate.ofEpochDay(today + 3), hour(plan[0].at).toLocalDate())
        assertEquals(9, hour(plan[0].at).hour)
    }

    @Test fun titleIsTheTaskAndTapGoesWhereItLives() {
        val d = AppData(listOf(task("s", Kind.SMALL, today + 2), task("w", Kind.BIG, today + 2, Status.WAITING)))
        val plan = Reminders.plan(d, quiet, now, emptySet())
        val small = plan.first { it.key.startsWith("d2:s") }
        assertEquals("Task s", small.title)
        assertTrue(small.text.startsWith("Due in 2 days"))
        assertEquals("small", small.screen)
        assertEquals("waiting", plan.first { it.key.startsWith("d1:w") }.screen)
    }

    @Test fun smallStuffCanBeLeftOut() {
        val d = AppData(listOf(task("s", Kind.SMALL, today + 4)))
        assertEquals(2, Reminders.plan(d, quiet, now, emptySet()).size)
        assertEquals(0, Reminders.plan(d, quiet.copy(remindSmall = false), now, emptySet()).size)
    }

    @Test fun sentOnesAndFinishedTasksAreSkipped() {
        val d = AppData(listOf(task("a", Kind.BIG, today + 5), task("b", Kind.BIG, today + 5, Status.DONE)))
        val plan = Reminders.plan(d, quiet, now, setOf("d2:a:${today + 5}"))
        assertEquals(listOf("d1:a:${today + 5}"), plan.map { it.key })
    }

    @Test fun quietHoursPushToMorning() {
        val s = quiet.copy(remindHour = 7, quietEnd = 8)
        val plan = Reminders.plan(AppData(listOf(task("a", Kind.BIG, today + 3))), s, now, emptySet())
        assertTrue(plan.all { hour(it.at).hour == 8 })
    }

    @Test fun dailyLimitKeepsTheMostImportant() {
        val d = AppData(listOf(task("a", Kind.BIG, today + 1), task("b", Kind.BIG, today + 1), task("c", Kind.BIG, today + 1)))
        val plan = Reminders.plan(d, quiet.copy(remindHour = 8), now.withHour(9), emptySet())
        assertEquals(2, Reminders.dueNow(plan, now.withHour(9).toInstant().toEpochMilli(), 0, 2).size)
        assertEquals(1, Reminders.dueNow(plan, now.withHour(9).toInstant().toEpochMilli(), 1, 2).size)
    }

    @Test fun frostNudgeOncePerSpell() {
        val s = Settings(weeklyReview = false, randomPrompt = false, remindTwoDays = false, remindOneDay = false)
        val t = task("f", Kind.BIG, null, touched = today - 10)
        val plan = Reminders.plan(AppData(listOf(t)), s, now, emptySet())
        assertEquals(listOf("frost:f:${today - 10}"), plan.map { it.key })
        assertEquals(0, Reminders.plan(AppData(listOf(t)), s, now, setOf("frost:f:${today - 10}")).size)
    }

    @Test fun yourOwnReminderGoesOutOnTimeEvenInQuietHours() {
        val at = now.withHour(23).withMinute(15).toInstant().toEpochMilli()
        val t = task("r", Kind.SMALL, null).copy(remindAt = at)
        val plan = Reminders.plan(AppData(listOf(t)), quiet, now, emptySet())
        val r = plan.single { it.channel == Reminders.REMINDERS }
        assertEquals(at, r.at)
        // the daily limit doesn't hold it back
        assertEquals(listOf("rem:r:$at"), Reminders.dueNow(plan, at, sentToday = 5, maxPerDay = 2).map { it.key })
    }

    @Test fun offMeansNothing() {
        assertEquals(0, Reminders.plan(AppData(listOf(task("a", Kind.BIG, today + 2))), Settings(notify = false), now, emptySet()).size)
    }
}
