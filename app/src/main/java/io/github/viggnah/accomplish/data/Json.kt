package io.github.viggnah.accomplish.data

import org.json.JSONArray
import org.json.JSONObject

/** Hand-written mapping to org.json (built into Android) so the app needs no serialization library. */
object Json {
    const val VERSION = 1

    fun encode(d: AppData): String = JSONObject().apply {
        put("version", VERSION)
        put("tasks", JSONArray(d.tasks.map(::task)))
        put("capsules", JSONArray(d.capsules.map(::capsule)))
        put("notes", JSONArray(d.notes.map(::note)))
        put("seen", JSONArray(d.seen.sorted()))
        put("appNotes", JSONArray(d.appNotes.map { n -> JSONObject().apply { put("id", n.id); put("at", n.at); put("text", n.text); put("done", n.done) } }))
    }.toString(1)

    fun decode(s: String): AppData {
        val o = JSONObject(s)
        return AppData(
            tasks = o.optJSONArray("tasks").objects().map(::task),
            capsules = o.optJSONArray("capsules").objects().map(::capsule),
            notes = o.optJSONArray("notes").objects().map(::note),
            seen = o.optJSONArray("seen").strings().toSet(),
            appNotes = o.optJSONArray("appNotes").objects().map { AppNote(it.getString("id"), it.getLong("at"), it.getString("text"), it.optBoolean("done")) },
        )
    }

    private fun task(t: Task) = JSONObject().apply {
        put("id", t.id); put("kind", t.kind.name); put("title", t.title); put("status", t.status.name)
        put("horizon", t.horizon.name); putOpt("deadline", t.deadline); put("steps", JSONArray(t.steps))
        put("color", t.color); put("size", t.size.name); putOpt("why", t.why); put("slot", t.slot)
        put("touched", t.touched); putOpt("doneAt", t.doneAt); putOpt("remindAt", t.remindAt)
        put("log", JSONArray(t.log.map(::event)))
    }

    private fun task(o: JSONObject) = Task(
        id = o.getString("id"),
        kind = Kind.valueOf(o.getString("kind")),
        title = o.getString("title"),
        status = Status.valueOf(o.getString("status")),
        horizon = Horizon.valueOf(o.getString("horizon")),
        deadline = o.optLongOrNull("deadline"),
        // Older files had a single "next" step.
        steps = o.optJSONArray("steps")?.strings() ?: listOfNotNull(o.optStringOrNull("next")),
        color = o.optInt("color", 0),
        size = runCatching { Size.valueOf(o.getString("size")) }.getOrDefault(Size.M),
        why = o.optStringOrNull("why"),
        slot = o.optInt("slot", 0),
        touched = o.getLong("touched"),
        doneAt = o.optLongOrNull("doneAt"),
        remindAt = o.optLongOrNull("remindAt"),
        log = o.optJSONArray("log").objects().mapNotNull(::event),
    )

    private fun event(e: Event) = JSONObject().apply {
        put("day", e.day); put("type", e.type.name); putOpt("text", e.text); putOpt("num", e.num)
    }

    private fun event(o: JSONObject): Event? {
        val type = runCatching { EventType.valueOf(o.getString("type")) }.getOrNull() ?: return null
        return Event(o.getLong("day"), type, o.optStringOrNull("text"), o.optLongOrNull("num"))
    }

    private fun capsule(c: Capsule) = JSONObject().apply {
        put("id", c.id); put("earned", c.earned); put("source", c.source.name)
        putOpt("taskId", c.taskId); putOpt("taskTitle", c.taskTitle); putOpt("noteId", c.noteId)
        c.reward?.let { r ->
            put("reward", JSONObject().apply {
                put("day", r.day); put("type", r.type.name); putOpt("refId", r.refId); putOpt("label", r.label); put("title", r.title)
                put("body", r.body); putOpt("extra", r.extra); put("social", r.social); putOpt("questState", r.questState?.name)
            })
        }
    }

    private fun capsule(o: JSONObject) = Capsule(
        id = o.getString("id"),
        earned = o.getLong("earned"),
        source = CapsuleSource.valueOf(o.getString("source")),
        taskId = o.optStringOrNull("taskId"),
        taskTitle = o.optStringOrNull("taskTitle"),
        noteId = o.optStringOrNull("noteId"),
        reward = o.optJSONObject("reward")?.let { r ->
            Reward(
                day = r.getLong("day"),
                type = RewardType.valueOf(r.getString("type")),
                refId = r.optStringOrNull("refId"),
                label = r.optStringOrNull("label"),
                title = r.getString("title"),
                body = r.getString("body"),
                extra = r.optStringOrNull("extra"),
                social = r.optBoolean("social", false),
                questState = r.optStringOrNull("questState")?.let { QuestState.valueOf(it) },
            )
        },
    )

    private fun note(n: SealedNote) = JSONObject().apply {
        put("id", n.id); put("written", n.written); put("opensOn", n.opensOn); put("text", n.text); put("delivered", n.delivered)
    }

    private fun note(o: JSONObject) = SealedNote(
        o.getString("id"), o.getLong("written"), o.getLong("opensOn"), o.getString("text"), o.optBoolean("delivered", false),
    )

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { getString(it) }
    private fun JSONObject.optStringOrNull(k: String): String? = if (isNull(k)) null else optString(k)
    private fun JSONObject.optLongOrNull(k: String): Long? = if (isNull(k)) null else optLong(k)
}
