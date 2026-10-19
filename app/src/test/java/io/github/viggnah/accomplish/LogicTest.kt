package io.github.viggnah.accomplish

import io.github.viggnah.accomplish.data.AppData
import io.github.viggnah.accomplish.data.CapsuleSource
import io.github.viggnah.accomplish.data.Event
import io.github.viggnah.accomplish.data.EventType
import io.github.viggnah.accomplish.data.Horizon
import io.github.viggnah.accomplish.data.Json
import io.github.viggnah.accomplish.data.Kind
import io.github.viggnah.accomplish.data.RewardType
import io.github.viggnah.accomplish.data.SealedNote
import io.github.viggnah.accomplish.data.Settings
import io.github.viggnah.accomplish.data.Size
import io.github.viggnah.accomplish.data.Status
import io.github.viggnah.accomplish.data.Task
import io.github.viggnah.accomplish.logic.Capsules
import io.github.viggnah.accomplish.logic.ContentBank
import io.github.viggnah.accomplish.logic.Logic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class LogicTest {
    private val today = 20_000L
    private val s = Settings()

    private fun big(id: String, status: Status = Status.ACTIVE, slot: Int = 0, touched: Long = today) =
        Task(id, Kind.BIG, "Task $id", status, Horizon.MONTH, slot = slot, touched = touched)

    private fun order(d: AppData) = Logic.active(d).map { it.id }

    @Test fun placingIntoFreeSpotShiftsOthers() {
        val d = AppData(listOf(big("a", slot = 0), big("b", slot = 1), big("w", Status.WAITING)))
        val out = Logic.place(d, "w", 0, s, today)
        assertEquals(listOf("w", "a", "b"), order(out))
        assertEquals(EventType.PLACED, out.tasks.first { it.id == "w" }.log.last().type)
    }

    @Test fun placingIntoFullHomeSwapsOut() {
        val d = AppData(listOf(big("a", slot = 0), big("b", slot = 1), big("c", slot = 2), big("w", Status.WAITING)))
        val out = Logic.place(d, "w", 1, s, today)
        assertEquals(listOf("a", "w", "c"), order(out))
        val b = out.tasks.first { it.id == "b" }
        assertEquals(Status.WAITING, b.status)
        assertEquals(EventType.SWAPPED_OUT, b.log.last().type)
    }

    @Test fun movingWithinHomeReorders() {
        val d = AppData(listOf(big("a", slot = 0), big("b", slot = 1), big("c", slot = 2)))
        assertEquals(listOf("c", "a", "b"), order(Logic.place(d, "c", 0, s, today)))
        assertEquals(listOf("a", "c"), order(Logic.place(d, "b", null, s, today)))
    }

    @Test fun deadlinePullsHorizonCloser() {
        val t = big("a").copy(horizon = Horizon.SOMEDAY, deadline = today + 5)
        assertEquals(Horizon.WEEK, Logic.effHorizon(t, today))
        assertEquals(Horizon.TODAY, Logic.effHorizon(t.copy(deadline = today + 1), today))
        // the deadline decides, even if the task was once marked for today
        assertEquals(Horizon.MONTH, Logic.effHorizon(t.copy(horizon = Horizon.TODAY, deadline = today + 20), today))
    }

    @Test fun frostBuildsWithNeglect() {
        assertEquals(0f, Logic.frost(big("a", touched = today - 3), s, today))
        assertEquals(1f, Logic.frost(big("a", touched = today - 30), s, today))
        assertTrue(Logic.frost(big("a", touched = today - 8), s, today) in 0.3f..0.6f)
    }

    @Test fun drainCountsSteps() {
        val t = big("a").copy(size = Size.S, log = List(2) { Event(today, EventType.STEP_DONE, "x") })
        assertEquals(1 to 3, Logic.drain(t))
        assertEquals(0 to 3, Logic.drain(t.copy(log = t.log + t.log)))
    }

    @Test fun onlyFirstStepPerDayCanDrop() {
        val always = s.copy(stepDropPercent = 100)
        val fresh = big("a")
        assertEquals(1, Capsules.onStep(AppData(listOf(fresh)), fresh, always, today, Random(1)).capsules.size)
        val stepped = fresh.copy(log = listOf(Event(today, EventType.STEP_DONE, "x")))
        assertEquals(0, Capsules.onStep(AppData(listOf(stepped)), stepped, always, today, Random(1)).capsules.size)
    }

    @Test fun smallDropsAtMostOncePerDay() {
        val always = s.copy(smallDropPercent = 50)
        var d = AppData()
        val t = Task("s", Kind.SMALL, "x", Status.DONE, Horizon.WEEK, touched = today)
        repeat(50) { d = Capsules.onSmallDone(d, t, always, today, Random(it)) }
        assertEquals(1, d.capsules.count { it.source == CapsuleSource.SMALL_DONE })
    }

    @Test fun finishingWithWhyReturnsTheMessage() {
        val t = big("a").copy(status = Status.DONE, why = "Freedom to travel", log = listOf(Event(today - 10, EventType.WHY_SET)))
        var d = Capsules.onBigDone(AppData(listOf(t)), t, s, today, Random(3))
        d = Capsules.open(d, d.capsules.single().id, s, ContentBank.EMPTY, today, Random(3))
        val r = d.capsules.single().reward!!
        assertEquals(RewardType.WHY_MESSAGE, r.type)
        assertEquals("Freedom to travel", r.body)
    }

    @Test fun sealedNotesArriveOnTheirDay() {
        val d = AppData(notes = listOf(SealedNote("n1", today - 40, today, "hi")))
        val out = Capsules.deliverNotes(d, today, Random(1))
        assertEquals(1, out.capsules.size)
        assertTrue(out.notes.single().delivered)
        assertEquals(out, Capsules.deliverNotes(out, today, Random(1)))
    }

    @Test fun jsonRoundTrips() {
        val t = big("a").copy(deadline = today + 3, remindAt = 1_800_000_000_000, steps = listOf("Call", "Book"), why = "Because", log = listOf(Event(today, EventType.DEADLINE_SET, num = today + 3)))
        val d = Capsules.deliverNotes(AppData(listOf(t), notes = listOf(SealedNote("n", today, today, "x")),
            appNotes = listOf(io.github.viggnah.accomplish.data.AppNote("a1", 1_700_000_000_000, "Make buttons flat", done = true))), today, Random(2))
        assertEquals(d, Json.decode(Json.encode(d)))
    }

    @Test fun olderSingleNextStepStillLoads() {
        val old = """{"version":1,"tasks":[{"id":"a","kind":"BIG","title":"T","status":"ACTIVE","horizon":"WEEK","next":"Call them","touched":1,"log":[]}]}"""
        assertEquals(listOf("Call them"), Json.decode(old).tasks.single().steps)
        assertEquals("Call them", Json.decode(old).tasks.single().next)
    }

    @Test fun bundledContentParsesAndNeverRepeatsEarly() {
        val bank = ContentBank.parse(File("src/main/assets/capsules.json").readText())
        assertEquals(150, bank.quests.size)
        assertEquals(250, bank.lessons.size)
        assertTrue(bank.quests.filter { it.personText != null }.all { "{person}" in it.personText!! })
        // Open many capsules: lessons and quests shouldn't repeat until their pool is used up.
        var d = AppData()
        val rnd = Random(7)
        repeat(120) {
            d = d.copy(capsules = d.capsules + io.github.viggnah.accomplish.data.Capsule("c$it", today, CapsuleSource.BIG_STEP))
            d = Capsules.open(d, "c$it", s, bank, today, rnd)
        }
        val ids = d.capsules.mapNotNull { c -> c.reward?.takeIf { it.type == RewardType.LESSON || it.type == RewardType.QUEST }?.refId }
        assertEquals(ids.size, ids.toSet().size)
    }
}
