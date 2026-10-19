package io.github.viggnah.accomplish.data

/** Days are stored as epoch days (LocalDate.toEpochDay) everywhere. */

enum class Kind { BIG, SMALL }

enum class Status { ACTIVE, WAITING, DONE, LET_GO }

enum class Horizon(val label: String, val short: String) {
    TODAY("Today", "Today"), WEEK("This week", "Week"), MONTH("This month", "Month"), SOMEDAY("Later", "Later")
}

/** Rough size of a big task: how many steps it takes to drain its meter. */
enum class Size(val steps: Int, val label: String) {
    S(3, "Small"), M(6, "Medium"), L(10, "Large")
}

enum class EventType {
    CREATED, CREATED_WAITING, RENAMED, NEXT_SET, NEXT_CLEARED, WHEN_CHANGED, DEADLINE_SET, DEADLINE_REMOVED,
    PLACED, MOVED, SENT_TO_WAITING, SWAPPED_OUT, STEP_DONE, THAWED, KEPT, DONE, LET_GO, RESTORED, PROMOTED,
    RESIZED, WHY_SET, REMINDER_SET, REMINDER_REMOVED
}

/** One thing that happened to a task. [text] and [num] carry the detail (old title, slot, deadline day, …). */
data class Event(val day: Long, val type: EventType, val text: String? = null, val num: Long? = null)

data class Task(
    val id: String,
    val kind: Kind,
    val title: String,
    val status: Status,
    val horizon: Horizon,
    val deadline: Long? = null,
    /** Upcoming steps in order; the first is the one shown on the card. */
    val steps: List<String> = emptyList(),
    val color: Int = 0,
    val size: Size = Size.M,
    val why: String? = null,
    /** Order among active big tasks; lower is higher priority. */
    val slot: Int = 0,
    val touched: Long,
    val doneAt: Long? = null,
    /** A one-off reminder, epoch milliseconds. */
    val remindAt: Long? = null,
    val log: List<Event> = emptyList(),
) {
    val stepsDone: Int get() = log.count { it.type == EventType.STEP_DONE }
    val next: String? get() = steps.firstOrNull()
    val isOpen: Boolean get() = status == Status.ACTIVE || status == Status.WAITING
}

enum class CapsuleSource { BIG_DONE, BIG_STEP, SMALL_DONE, SEALED_NOTE }

enum class RewardType { WHY_MESSAGE, SEALED_MESSAGE, MEMORY, SEAL_PROMPT, QUEST, LESSON }

enum class QuestState { OFFERED, ACCEPTED, DONE, SKIPPED }

/** What a capsule turned out to contain, fixed the moment it is opened. */
data class Reward(
    val day: Long,
    val type: RewardType,
    val refId: String? = null,
    /** Small label above the title, e.g. a lesson's topic. */
    val label: String? = null,
    val title: String,
    val body: String,
    val extra: String? = null,
    val social: Boolean = false,
    val questState: QuestState? = null,
)

data class Capsule(
    val id: String,
    val earned: Long,
    val source: CapsuleSource,
    val taskId: String? = null,
    val taskTitle: String? = null,
    val noteId: String? = null,
    val reward: Reward? = null,
) {
    val opened: Boolean get() = reward != null
}

/** A message to future you, sealed until [opensOn]. */
data class SealedNote(val id: String, val written: Long, val opensOn: Long, val text: String, val delivered: Boolean = false)

/** A note about the app itself: an idea, a bug, anything. [at] is epoch milliseconds. */
data class AppNote(val id: String, val at: Long, val text: String, val done: Boolean = false)

data class AppData(
    val tasks: List<Task> = emptyList(),
    val capsules: List<Capsule> = emptyList(),
    val notes: List<SealedNote> = emptyList(),
    /** Content ids already shown, so quests and lessons don't repeat until the bank runs out. */
    val seen: Set<String> = emptySet(),
    val appNotes: List<AppNote> = emptyList(),
)
