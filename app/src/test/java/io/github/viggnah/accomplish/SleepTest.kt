package io.github.viggnah.accomplish

import io.github.viggnah.accomplish.data.AppData
import io.github.viggnah.accomplish.data.Json
import io.github.viggnah.accomplish.data.Settings
import io.github.viggnah.accomplish.logic.Reminders
import io.github.viggnah.accomplish.logic.Sleep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class SleepTest {
    private val zone = ZoneId.of("Asia/Colombo")
    private fun ms(day: Int, h: Int, m: Int = 0) = ZonedDateTime.of(2026, 11, day, h, m, 0, 0, zone).toInstant().toEpochMilli()
    private fun night(day: Int) = ZonedDateTime.of(2026, 11, day, 12, 0, 0, 0, zone).toLocalDate().toEpochDay()
    private val quiet = Settings(weeklyReview = false, randomPrompt = false, frostNudge = false)

    @Test fun afterMidnightCountsForTheEveningBefore() {
        assertEquals(night(26), Sleep.nightOf(ms(26, 23, 40), zone))
        assertEquals(night(26), Sleep.nightOf(ms(27, 0, 40), zone))
        assertEquals(night(27), Sleep.nightOf(ms(27, 13, 0), zone))
    }

    @Test fun onTimeWorksAcrossMidnight() {
        assertTrue(Sleep.onTime(ms(26, 23, 50), 0, zone))
        assertTrue(Sleep.onTime(ms(27, 0, 0), 0, zone))
        assertFalse(Sleep.onTime(ms(27, 0, 1), 0, zone))
        assertFalse(Sleep.onTime(ms(26, 23, 10), 23 * 60, zone))
    }

    @Test fun oneBedtimePerNightAndItSurvivesSaving() {
        var d = Sleep.record(AppData(), ms(26, 23, 40), zone)
        d = Sleep.record(d, ms(27, 0, 20), zone)
        d = Sleep.record(d, ms(27, 23, 0), zone)
        assertEquals(listOf(ms(27, 0, 20), ms(27, 23, 0)), d.bedtimes)
        assertEquals(d.bedtimes, Json.decode(Json.encode(d)).bedtimes)
        assertEquals(1, Sleep.onTimeCount(d, listOf(night(26), night(27)), 0, zone))
        assertEquals(ms(27, 0, 20), Sleep.at(night(26), Sleep.minutesAfterNoon(ms(27, 0, 20), zone), zone))
    }

    @Test fun windDownComesHalfAnHourBeforeUnlessAlreadyInBed() {
        val now = ZonedDateTime.of(2026, 11, 26, 20, 0, 0, 0, zone)
        val plan = Reminders.plan(AppData(), quiet, now, emptySet())
        val bed = plan.first { it.type == "bedtime" }
        assertEquals(ms(26, 23, 30), bed.at)
        assertEquals(Reminders.REMINDERS, bed.channel)
        val inBed = Sleep.record(AppData(), ms(26, 22, 0), zone)
        assertTrue(Reminders.plan(inBed, quiet, now, emptySet()).none { it.key == Reminders.bedKey(night(26)) })
        assertTrue(Reminders.plan(AppData(), quiet.copy(bedReminder = false), now, emptySet()).none { it.type == "bedtime" })
    }
}
