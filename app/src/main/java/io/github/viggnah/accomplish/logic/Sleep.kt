package io.github.viggnah.accomplish.logic

import io.github.viggnah.accomplish.data.AppData
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Bedtimes, one per night. A night belongs to the evening it starts on, so going to bed at 00:40
 * on Tuesday counts for Monday night. Times are compared as minutes after that evening's noon,
 * so 23:30 and 00:30 sit next to each other instead of a day apart.
 */
object Sleep {
    /** The epoch day of the evening this moment's night started on. Before noon belongs to the night before. */
    fun nightOf(ms: Long, zone: ZoneId): Long {
        val t = Instant.ofEpochMilli(ms).atZone(zone)
        return (if (t.hour < 12) t.toLocalDate().minusDays(1) else t.toLocalDate()).toEpochDay()
    }

    fun minutesAfterNoon(ms: Long, zone: ZoneId): Int {
        val t = Instant.ofEpochMilli(ms).atZone(zone)
        return Math.floorMod(t.hour * 60 + t.minute - 12 * 60, 24 * 60)
    }

    /** "23:40". */
    fun clock(ms: Long, zone: ZoneId): String = Instant.ofEpochMilli(ms).atZone(zone).let { "%02d:%02d".format(it.hour, it.minute) }

    /** "00:00" for a time given as minutes after midnight. */
    fun clock(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    /** [target] is minutes after midnight (0 = 00:00, 1380 = 23:00). */
    fun targetAfterNoon(target: Int): Int = Math.floorMod(target - 12 * 60, 24 * 60)

    fun onTime(ms: Long, target: Int, zone: ZoneId) = minutesAfterNoon(ms, zone) <= targetAfterNoon(target)

    /** The moment [minutesAfterNoon] minutes after the noon of [night]. */
    fun at(night: Long, minutesAfterNoon: Int, zone: ZoneId): Long =
        LocalDate.ofEpochDay(night).atTime(12, 0).plusMinutes(minutesAfterNoon.toLong()).atZone(zone).toInstant().toEpochMilli()

    fun bedtime(d: AppData, night: Long, zone: ZoneId): Long? = d.bedtimes.lastOrNull { nightOf(it, zone) == night }

    /** Records going to bed at [ms]. A night has one bedtime, so this replaces any earlier one that night. */
    fun record(d: AppData, ms: Long, zone: ZoneId): AppData {
        val night = nightOf(ms, zone)
        return d.copy(bedtimes = (d.bedtimes.filter { nightOf(it, zone) != night } + ms).sorted())
    }

    /** How many of [nights] have a bedtime by the target. */
    fun onTimeCount(d: AppData, nights: List<Long>, target: Int, zone: ZoneId) =
        nights.count { n -> bedtime(d, n, zone)?.let { onTime(it, target, zone) } == true }
}
