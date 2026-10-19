package io.github.viggnah.accomplish.logic

import io.github.viggnah.accomplish.data.AppData
import io.github.viggnah.accomplish.data.Capsule
import io.github.viggnah.accomplish.data.CapsuleSource
import io.github.viggnah.accomplish.data.EventType
import io.github.viggnah.accomplish.data.Kind
import io.github.viggnah.accomplish.data.QuestState
import io.github.viggnah.accomplish.data.Reward
import io.github.viggnah.accomplish.data.RewardType
import io.github.viggnah.accomplish.data.SealedNote
import io.github.viggnah.accomplish.data.Settings
import io.github.viggnah.accomplish.data.Status
import io.github.viggnah.accomplish.data.Task
import org.json.JSONObject
import kotlin.random.Random

data class Quest(val id: String, val text: String, val personText: String?, val social: Boolean)
data class Lesson(val id: String, val topic: String, val title: String, val body: String, val tryIt: String)

class ContentBank(val quests: List<Quest>, val lessons: List<Lesson>) {
    companion object {
        val EMPTY = ContentBank(emptyList(), emptyList())

        fun parse(json: String): ContentBank {
            val o = JSONObject(json)
            val qs = o.optJSONArray("quests")
            val ls = o.optJSONArray("lessons")
            val quests = (0 until (qs?.length() ?: 0)).map { i ->
                val q = qs!!.getJSONObject(i)
                Quest(q.getString("id"), q.getString("text"), q.optString("personText").ifEmpty { null }, q.optBoolean("social"))
            }
            val lessons = (0 until (ls?.length() ?: 0)).map { i ->
                val l = ls!!.getJSONObject(i)
                Lesson(l.getString("id"), l.getString("topic"), l.getString("title"), l.getString("body"), l.getString("tryIt"))
            }
            return ContentBank(quests, lessons)
        }
    }
}

/**
 * Capsules are earned mostly by moving big tasks forward:
 * finishing one always drops a capsule, the first step on each big task per day often does,
 * and small stuff only occasionally (at most once a day).
 */
object Capsules {
    private fun newId(rnd: Random) = "c" + rnd.nextLong(1L shl 40).toString(36)

    private fun add(d: AppData, source: CapsuleSource, t: Task?, today: Long, rnd: Random, noteId: String? = null) =
        d.copy(capsules = d.capsules + Capsule(newId(rnd), today, source, t?.id, t?.title, noteId))

    /** Call with the task as it was *before* the step was logged. */
    fun onStep(d: AppData, before: Task, s: Settings, today: Long, rnd: Random): AppData {
        if (!s.capsules) return d
        val alreadyToday = before.log.any { it.type == EventType.STEP_DONE && it.day == today }
        if (alreadyToday || rnd.nextInt(100) >= s.stepDropPercent) return d
        return add(d, CapsuleSource.BIG_STEP, before, today, rnd)
    }

    fun onBigDone(d: AppData, t: Task, s: Settings, today: Long, rnd: Random): AppData =
        if (!s.capsules) d else add(d, CapsuleSource.BIG_DONE, t, today, rnd)

    fun onSmallDone(d: AppData, t: Task, s: Settings, today: Long, rnd: Random): AppData {
        if (!s.capsules) return d
        val alreadyToday = d.capsules.any { it.source == CapsuleSource.SMALL_DONE && it.earned == today }
        if (alreadyToday || rnd.nextInt(100) >= s.smallDropPercent) return d
        return add(d, CapsuleSource.SMALL_DONE, t, today, rnd)
    }

    /** Sealed notes whose day has come turn into capsules. Not undoable; runs on launch and resume. */
    fun deliverNotes(d: AppData, today: Long, rnd: Random): AppData {
        val due = d.notes.filter { !it.delivered && it.opensOn <= today }
        if (due.isEmpty()) return d
        var nd = d.copy(notes = d.notes.map { if (it in due) it.copy(delivered = true) else it })
        for (n in due) nd = add(nd, CapsuleSource.SEALED_NOTE, null, today, rnd, noteId = n.id)
        return nd
    }

    fun unopened(d: AppData) = d.capsules.filter { !it.opened }.sortedBy { it.earned }

    fun open(d: AppData, id: String, s: Settings, bank: ContentBank, today: Long, rnd: Random): AppData {
        val c = d.capsules.firstOrNull { it.id == id } ?: return d
        if (c.opened) return d
        var seen = d.seen
        val task = c.taskId?.let { tid -> d.tasks.firstOrNull { it.id == tid } }

        val reward: Reward = when {
            c.source == CapsuleSource.SEALED_NOTE -> {
                val n = d.notes.firstOrNull { it.id == c.noteId }
                Reward(today, RewardType.SEALED_MESSAGE, refId = n?.id, label = "From past you",
                    title = "A note you sealed on ${n?.let { Logic.fmtLong(it.written) } ?: "an earlier day"}",
                    body = n?.text ?: "(The note couldn't be found.)")
            }
            c.source == CapsuleSource.BIG_DONE && task?.why != null -> {
                val wrote = task.log.lastOrNull { it.type == EventType.WHY_SET }?.day
                Reward(today, RewardType.WHY_MESSAGE, refId = task.id, label = "From past you",
                    title = "Why “${task.title}” mattered",
                    body = task.why,
                    extra = "You wrote this${wrote?.let { " on ${Logic.fmtLong(it)}" } ?: ""}, before you started. You finished it after ${Logic.plural(task.stepsDone, "step")}.")
            }
            else -> {
                val memories = d.tasks.filter { it.kind == Kind.BIG && it.status == Status.DONE && (it.doneAt ?: today) <= today - 60 }
                val pendingNotes = d.notes.count { !it.delivered }
                val options = buildList {
                    if (bank.quests.isNotEmpty()) add(RewardType.QUEST to 40)
                    if (bank.lessons.isNotEmpty()) add(RewardType.LESSON to 45)
                    if (memories.isNotEmpty()) add(RewardType.MEMORY to 7)
                    if (pendingNotes < 5) add(RewardType.SEAL_PROMPT to 8)
                }
                when (weighted(options, rnd)) {
                    RewardType.QUEST -> {
                        val wantSocial = rnd.nextDouble() < s.socialRate.chance
                        var pool = bank.quests.filter { it.social == wantSocial }.ifEmpty { bank.quests }
                        var fresh = pool.filter { it.id !in seen }
                        if (fresh.isEmpty()) { seen = seen - pool.map { it.id }.toSet(); fresh = pool }
                        val q = fresh.random(rnd)
                        seen = seen + q.id
                        val person = s.people.takeIf { it.isNotEmpty() && q.personText != null && rnd.nextBoolean() }?.random(rnd)
                        Reward(today, RewardType.QUEST, refId = q.id, label = if (q.social) "Side quest with someone" else "Side quest",
                            title = "Side quest", body = if (person != null) q.personText!!.replace("{person}", person) else q.text,
                            social = q.social, questState = QuestState.OFFERED)
                    }
                    RewardType.LESSON -> {
                        var fresh = bank.lessons.filter { it.id !in seen }
                        if (fresh.isEmpty()) { seen = seen - bank.lessons.map { it.id }.toSet(); fresh = bank.lessons }
                        val l = fresh.random(rnd)
                        seen = seen + l.id
                        Reward(today, RewardType.LESSON, refId = l.id, label = "One-minute lesson on ${l.topic}", title = l.title, body = l.body, extra = l.tryIt)
                    }
                    RewardType.MEMORY -> {
                        val m = memories.random(rnd)
                        Reward(today, RewardType.MEMORY, refId = m.id, label = "Remember this?",
                            title = m.title,
                            body = "You finished this on ${Logic.fmtLong(m.doneAt ?: today)}, ${Logic.ago(m.doneAt ?: today, today)}, after ${Logic.plural(m.stepsDone, "step")}.",
                            extra = m.why?.let { "Back then you wrote: “$it”" })
                    }
                    else -> Reward(today, RewardType.SEAL_PROMPT, label = "Seal a note", title = "Write to future you",
                        body = "Write a few lines to yourself. They stay sealed until a surprise day in the next few months.")
                }
            }
        }
        return d.copy(capsules = d.capsules.map { if (it.id == id) it.copy(reward = reward) else it }, seen = seen)
    }

    fun seal(d: AppData, capsuleId: String, text: String, today: Long, rnd: Random): AppData {
        val note = SealedNote("n" + rnd.nextLong(1L shl 40).toString(36), today, today + rnd.nextLong(30, 181), text.trim())
        return d.copy(
            notes = d.notes + note,
            capsules = d.capsules.map { c -> if (c.id == capsuleId) c.copy(reward = c.reward?.copy(extra = "sealed")) else c },
        )
    }

    fun setQuest(d: AppData, capsuleId: String, state: QuestState) =
        d.copy(capsules = d.capsules.map { c -> if (c.id == capsuleId) c.copy(reward = c.reward?.copy(questState = state)) else c })

    private fun <T> weighted(options: List<Pair<T, Int>>, rnd: Random): T? {
        val total = options.sumOf { it.second }
        if (total <= 0) return null
        var r = rnd.nextInt(total)
        for ((v, w) in options) { if (r < w) return v; r -= w }
        return options.last().first
    }
}
