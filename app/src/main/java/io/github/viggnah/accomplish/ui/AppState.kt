package io.github.viggnah.accomplish.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.viggnah.accomplish.data.AppData
import io.github.viggnah.accomplish.data.EventType
import io.github.viggnah.accomplish.data.Horizon
import io.github.viggnah.accomplish.data.Kind
import io.github.viggnah.accomplish.data.LoadResult
import io.github.viggnah.accomplish.data.QuestState
import io.github.viggnah.accomplish.data.Settings
import io.github.viggnah.accomplish.data.SettingsStore
import io.github.viggnah.accomplish.data.Size
import io.github.viggnah.accomplish.data.Status
import io.github.viggnah.accomplish.data.Storage
import io.github.viggnah.accomplish.data.Task
import io.github.viggnah.accomplish.data.Volume
import io.github.viggnah.accomplish.logic.Capsules
import io.github.viggnah.accomplish.logic.ContentBank
import io.github.viggnah.accomplish.logic.Logic
import io.github.viggnah.accomplish.logic.Logic.logged
import java.io.File
import java.time.LocalDate
import kotlin.random.Random

enum class Screen { HOME, SMALL, WAITING, COMING, HISTORY, COLLECTION, NOTES, SETTINGS }

sealed interface Sheet {
    /** [spot] set means "create straight into this spot on Home" (from tapping an empty spot). */
    data class Capture(val kind: Kind, val spot: Int? = null) : Sheet
    data class Edit(val id: String) : Sheet
}

sealed interface Phase {
    data object Loading : Phase
    data object NeedsAccess : Phase
    data object NeedsLocation : Phase
    data class Missing(val dir: String) : Phase
    data class Failed(val message: String) : Phase
    data object Ready : Phase
}

/** What the edit sheet holds until it closes. [place] is a spot index, or null for the waiting room. */
data class Draft(
    val title: String, val steps: List<String>, val horizon: Horizon, val deadline: Long?,
    val color: Int, val size: Size, val place: Int?, val why: String,
    val remindAt: Long? = null,
)

data class Toast(val id: Long, val msg: String, val undoable: Boolean, val until: Long)

class AppState(private val ctx: Context) {
    private val settingsStore = SettingsStore(ctx)
    val storage = Storage(ctx)
    private val rnd = Random.Default

    var settings by mutableStateOf(settingsStore.load()); private set
    var data by mutableStateOf(AppData()); private set
    var today by mutableLongStateOf(LocalDate.now().toEpochDay()); private set
    var phase by mutableStateOf<Phase>(Phase.Loading); private set

    var stack by mutableStateOf(listOf(Screen.HOME)); private set
    val screen get() = stack.last()
    var sheet by mutableStateOf<Sheet?>(null)
    var menuOpen by mutableStateOf(false)
    var toast by mutableStateOf<Toast?>(null); private set
    var opening by mutableStateOf<String?>(null)
    var askNext by mutableStateOf<String?>(null)
    var placeFor by mutableStateOf<String?>(null)
    /** Bumped when something worth celebrating happens on a card (finishing a big task). */
    var celebrate by mutableLongStateOf(0L); private set

    val bank: ContentBank by lazy {
        runCatching { ContentBank.parse(ctx.assets.open("capsules.json").bufferedReader().use { it.readText() }) }.getOrDefault(ContentBank.EMPTY)
    }

    /** Either a data snapshot to go back to, or [restore] for edits still inside an open sheet. */
    private data class UndoEntry(val before: AppData, val msg: String, val until: Long, val restore: (() -> Unit)? = null)
    private val undoStack = ArrayDeque<UndoEntry>()

    // ---------- lifecycle and storage ----------

    fun start() {
        today = LocalDate.now().toEpochDay()
        settings.dataDir?.let { File(it) }?.takeIf { it.name == Storage.OLD_FOLDER }?.parentFile?.let { root ->
            if (Storage.renameOldFolder(root)) updateSettings(settings.copy(dataDir = File(root, Storage.FOLDER).path))
        }
        phase = when {
            !storage.hasAllFilesAccess() -> Phase.NeedsAccess
            settings.dataDir == null -> Phase.NeedsLocation
            else -> { load(); return }
        }
    }

    /** The data file's timestamp when the app last let go of it; a different one means a snooze (or anything else) changed it. */
    private var leftAt = 0L
    private fun dataFile() = settings.dataDir?.let { File(it, Storage.FILE) }

    fun onResume() {
        val now = LocalDate.now().toEpochDay()
        if (now != today) today = now
        when (phase) {
            Phase.Ready -> if (leftAt != 0L && dataFile()?.lastModified() != leftAt) load() else deliverNotes()
            Phase.Loading -> {}
            else -> start()
        }
    }

    fun onPause() {
        storage.flush()
        leftAt = dataFile()?.lastModified() ?: 0L
    }

    private fun load() {
        val dir = File(settings.dataDir ?: return)
        when (val r = storage.load(dir)) {
            is LoadResult.Ok -> {
                data = r.data
                settingsStore.exportTo(dir)
                phase = Phase.Ready
                deliverNotes()
                r.note?.let { showToast(it, false, 6000) }
            }
            is LoadResult.Missing -> phase = Phase.Missing(dir.path)
            is LoadResult.Failed -> phase = Phase.Failed(r.message)
        }
    }

    fun volumes(): List<Volume> = storage.volumes()

    /** Switch storage location. Moving from an existing location brings your data along unless the new place already has some. */
    fun useVolume(v: Volume) {
        Storage.renameOldFolder(v.root)
        val dir = File(v.root, Storage.FOLDER)
        // A fresh install finds the settings you had before, kept next to the data.
        if (!settingsStore.hasAny() && settingsStore.importFrom(dir)) settings = settingsStore.load()
        val old = settings.dataDir
        if (old != null && old != dir.path && !File(dir, Storage.FILE).exists() && data != AppData()) {
            runCatching { storage.writeNow(dir, data, settings.backups) }
        }
        updateSettings(settings.copy(dataDir = dir.path))
        load()
    }

    fun chooseLocationAgain() { phase = Phase.NeedsLocation }

    private fun deliverNotes() {
        val nd = Capsules.deliverNotes(data, today, rnd)
        if (nd !== data) { data = nd; persist() }
    }

    private var lastReminders: List<Long?> = emptyList()

    private fun persist() {
        val dir = settings.dataDir ?: return
        storage.save(File(dir), data, settings.backups)
        // A reminder set or changed should be armed now, not when the app is next left.
        val reminders = data.tasks.map { if (it.isOpen) it.remindAt else null }
        if (reminders != lastReminders) { lastReminders = reminders; io.github.viggnah.accomplish.notify.Notifier.runAsync(ctx, data) }
    }

    fun updateSettings(s: Settings) {
        settings = s
        settingsStore.save(s)
        s.dataDir?.let { settingsStore.exportTo(File(it)) }
    }

    // ---------- navigation ----------

    fun openFromNotification(where: String) {
        menuOpen = false
        when (where) {
            "small" -> { closeSheet(); go(Screen.HOME); go(Screen.SMALL) }
            "waiting" -> { closeSheet(); go(Screen.HOME); go(Screen.WAITING) }
            "new" -> { if (sheet !is Sheet.Capture) { closeSheet(); go(Screen.HOME); sheet = Sheet.Capture(Kind.SMALL) } }
            else -> { closeSheet(); go(Screen.HOME) }
        }
    }

    fun go(s: Screen) {
        askNext = null; placeFor = null
        stack = if (s == Screen.HOME) listOf(Screen.HOME) else stack.filter { it != s } + s
    }

    /** Set by the open sheet: what to do when it is closed by swipe, scrim, × or back (task sheets save). */
    var sheetCloser: (() -> Unit)? = null

    fun closeSheet() {
        val c = sheetCloser
        sheetCloser = null
        sheet = null
        forgetSheetUndos()
        c?.invoke()
    }

    /** Close without running the sheet's closer, for actions that already did their own saving. */
    fun dropSheet() { sheetCloser = null; sheet = null; forgetSheetUndos() }

    /** In-sheet undos point at a draft that no longer exists once the sheet is gone. */
    private fun forgetSheetUndos() { undoStack.removeAll { it.restore != null } }

    /** Returns false when there's nothing left to go back to. */
    fun back(): Boolean {
        if (opening != null) { opening = null; return true }
        if (menuOpen) { menuOpen = false; return true }
        if (sheet != null) { closeSheet(); return true }
        if (stack.size > 1) { stack = stack.dropLast(1); placeFor = null; return true }
        return false
    }

    // ---------- undo ----------

    /**
     * Every change goes through here. Undo is offered when [undoable]: anything that removes or changes
     * something you can't simply put back where you are. Only true in-place toggles and additions you can
     * delete right there skip it; they get at most a short confirmation, or nothing when [msg] is null.
     */
    fun act(msg: String?, undoable: Boolean, f: (AppData) -> AppData) {
        val before = data
        data = f(data)
        persist()
        pruneUndo()
        val secs = settings.undoSecs
        if (undoable && secs > 0 && msg != null) {
            val until = System.currentTimeMillis() + secs * 1000L
            undoStack.addLast(UndoEntry(before, msg, until))
            showToast(msg, true, until = until)
        } else if (msg != null) showToast(msg, false)
    }

    /** Undo for a removal inside an open sheet (a step, a deadline): puts the draft back, keeps the sheet open. */
    fun offerUndo(msg: String, restore: () -> Unit) {
        val secs = settings.undoSecs
        if (secs <= 0) return
        pruneUndo()
        val until = System.currentTimeMillis() + secs * 1000L
        undoStack.addLast(UndoEntry(data, msg, until, restore))
        showToast(msg, true, until = until)
    }

    fun undo() {
        pruneUndo()
        val e = undoStack.removeLastOrNull() ?: return
        if (e.restore != null) { e.restore.invoke(); showToast("Undone", false); return }
        dropSheet(); opening = null; askNext = null; placeFor = null
        data = e.before
        persist()
        val prev = undoStack.lastOrNull()
        if (prev != null) showToast("Undone. Undo “${prev.msg}” too?", true, until = prev.until)
        else showToast("Undone", false)
    }

    fun dismissToast() { undoStack.clear(); toast = null }

    fun toastExpired(id: Long) { if (toast?.id == id) toast = null }

    private fun pruneUndo() {
        val now = System.currentTimeMillis()
        while (undoStack.isNotEmpty() && undoStack.first().until <= now) undoStack.removeFirst()
    }

    private fun showToast(msg: String, undoable: Boolean, ms: Long = 1800, until: Long = System.currentTimeMillis() + ms) {
        toast = Toast(System.nanoTime(), msg, undoable, until)
    }

    // ---------- reads ----------

    fun task(id: String) = data.tasks.firstOrNull { it.id == id }
    val active get() = Logic.active(data)
    val waiting get() = Logic.waiting(data)
    val smalls get() = Logic.smalls(data)
    val unopened get() = Capsules.unopened(data)

    fun freeColor(): Int {
        val used = (active + waiting).map { it.color }.toSet()
        return (0 until 5).firstOrNull { it !in used } ?: rnd.nextInt(5)
    }

    fun draftOf(t: Task) = Draft(t.title, t.steps, t.horizon, t.deadline, t.color, t.size,
        if (t.status == Status.ACTIVE) active.indexOf(t) else null, t.why.orEmpty(), t.remindAt)

    private fun clean(steps: List<String>) = steps.map { it.trim() }.filter { it.isNotEmpty() }

    // ---------- task actions ----------

    fun capture(title: String, kind: Kind, horizon: Horizon, deadline: Long?, size: Size, spot: Int? = null, steps: List<String> = emptyList(), remindAt: Long? = null) {
        val planned = if (kind == Kind.BIG) clean(steps) else emptyList()
        val t0 = Task(
            id = "t" + rnd.nextLong(1L shl 40).toString(36), kind = kind, title = title.trim(),
            status = if (kind == Kind.BIG) Status.WAITING else Status.ACTIVE, horizon = horizon, deadline = deadline,
            color = if (kind == Kind.BIG) freeColor() else 0, size = size, touched = today, steps = planned, remindAt = remindAt,
        )
        var t = t0.logged(if (kind == Kind.BIG) EventType.CREATED_WAITING else EventType.CREATED, today)
        if (deadline != null) t = t.logged(EventType.DEADLINE_SET, today, num = deadline)
        planned.forEach { t = t.logged(EventType.NEXT_SET, today, text = it) }
        if (remindAt != null) t = t.logged(EventType.REMINDER_SET, today, num = remindAt)
        val intoSpot = spot != null && kind == Kind.BIG
        val msg = when {
            intoSpot -> "Added to spot ${minOf(spot!!, active.size) + 1}"
            kind == Kind.BIG -> "Added to the ${settings.waiting}"
            else -> "Added to ${settings.small}"
        }
        act(msg, undoable = true) {
            val nd = it.copy(tasks = it.tasks + t)
            if (intoSpot) Logic.place(nd, t.id, spot!!, settings, today) else nd
        }
    }

    /** Tick off the step on the card; the next planned one moves up, or we ask what's next. */
    fun stepDone(id: String) {
        val before = task(id) ?: return
        val step = before.next ?: return
        if (before.steps.size <= 1) askNext = id
        act("Step done", undoable = true) { d ->
            val nd = Logic.update(d, id) { it.copy(steps = it.steps.drop(1), touched = today).logged(EventType.STEP_DONE, today, text = step) }
            Capsules.onStep(nd, before, settings, today, rnd)
        }
    }

    fun setNext(id: String, text: String) {
        askNext = null
        if (text.isBlank()) return
        act(null, undoable = false) { d -> Logic.update(d, id) { it.copy(steps = it.steps + text.trim(), touched = today).logged(EventType.NEXT_SET, today, text = text.trim()) } }
    }

    fun thaw(id: String) = act(null, undoable = false) { d -> Logic.update(d, id) { it.copy(touched = today).logged(EventType.THAWED, today) } }

    fun finish(id: String, draft: Draft? = null) {
        val t = task(id) ?: return
        if (t.kind == Kind.BIG) celebrate = System.nanoTime()
        act("Done", undoable = true) { d ->
            var nd = if (draft != null) applyDraft(d, id, draft) else d
            nd = Logic.update(nd, id) { it.copy(status = Status.DONE, doneAt = today).logged(EventType.DONE, today) }
            val done = nd.tasks.first { it.id == id }
            if (t.kind == Kind.BIG) Capsules.onBigDone(nd, done, settings, today, rnd) else Capsules.onSmallDone(nd, done, settings, today, rnd)
        }
        if (askNext == id) askNext = null
    }

    fun letGo(id: String, draft: Draft? = null) {
        task(id) ?: return
        act("Let go", undoable = true) { d ->
            val nd = if (draft != null) applyDraft(d, id, draft) else d
            Logic.update(nd, id) { it.copy(status = Status.LET_GO, doneAt = today).logged(EventType.LET_GO, today) }
        }
    }

    fun keep(id: String) = act(null, undoable = false) { d -> Logic.update(d, id) { it.copy(touched = today).logged(EventType.KEPT, today) } }

    fun place(id: String, spot: Int) {
        task(id) ?: return
        val full = active.size >= settings.slots
        placeFor = null
        act(if (full) "Swapped" else "Placed in spot ${minOf(spot, active.size) + 1}", undoable = true) { d ->
            Logic.place(d, id, spot, settings, today)
        }
    }

    /** Snoozed from a notification. */
    fun snooze(id: String, at: Long) = act(null, undoable = false) { d ->
        Logic.update(d, id) { it.copy(remindAt = at).logged(EventType.REMINDER_SET, today, num = at) }
    }

    /** Drag-and-drop on Home: move an IMP task to another spot. */
    fun reorder(id: String, spot: Int) {
        // Dragging it back is just as easy, so no undo.
        act(null, undoable = false) { d -> Logic.place(d, id, spot, settings, today) }
    }

    fun restore(id: String) {
        task(id) ?: return
        act("Restored", undoable = true) { d ->
            Logic.update(d, id) {
                it.copy(status = if (it.kind == Kind.BIG) Status.WAITING else Status.ACTIVE, doneAt = null, touched = today).logged(EventType.RESTORED, today)
            }
        }
    }

    fun promote(id: String, draft: Draft) {
        act("Moved to the ${settings.waiting}", undoable = true) { d ->
            Logic.update(applyDraft(d, id, draft), id) {
                it.copy(kind = Kind.BIG, status = Status.WAITING, color = freeColor(), size = Size.M).logged(EventType.PROMOTED, today)
            }
        }
    }

    /**
     * Closing a sheet just saves. Anything destructive inside it (removing a step, a date, a reminder) already
     * offered its own undo at the moment it happened, so only a move between spots gets one here.
     */
    fun saveDraft(id: String, draft: Draft) {
        val t = task(id) ?: return
        if (!t.isOpen || !changed(t, draft)) return
        val cur = if (t.status == Status.ACTIVE) active.indexOf(t) else null
        val moved = t.kind == Kind.BIG && draft.place != cur
        act(if (!moved) "Saved" else if (draft.place == null) "Moved to the ${settings.waiting}" else "Moved", undoable = moved) { d -> applyDraft(d, id, draft) }
    }

    private fun changed(t: Task, d: Draft): Boolean {
        val place = if (t.status == Status.ACTIVE) active.indexOf(t) else null
        return (d.title.isNotBlank() && d.title.trim() != t.title) || d.horizon != t.horizon || d.deadline != t.deadline || d.remindAt != t.remindAt ||
            (t.kind == Kind.BIG && (clean(d.steps) != t.steps || d.color != t.color || d.size != t.size ||
                d.place != place || (d.why.trim().ifEmpty { null }) != t.why))
    }

    private fun applyDraft(d0: AppData, id: String, dr: Draft): AppData {
        var d = Logic.update(d0, id) { t0 ->
            var t = t0.copy(touched = today)
            val title = dr.title.trim()
            if (title.isNotEmpty() && title != t.title) t = t.logged(EventType.RENAMED, today, text = t.title).copy(title = title)
            if (dr.horizon != t.horizon) t = t.copy(horizon = dr.horizon).logged(EventType.WHEN_CHANGED, today, text = dr.horizon.name)
            if (dr.deadline != t.deadline) {
                t = t.copy(deadline = dr.deadline)
                t = if (dr.deadline == null) t.logged(EventType.DEADLINE_REMOVED, today) else t.logged(EventType.DEADLINE_SET, today, num = dr.deadline)
            }
            if (dr.remindAt != t.remindAt) {
                t = t.copy(remindAt = dr.remindAt)
                t = if (dr.remindAt == null) t.logged(EventType.REMINDER_REMOVED, today) else t.logged(EventType.REMINDER_SET, today, num = dr.remindAt)
            }
            if (t.kind == Kind.BIG) {
                val steps = clean(dr.steps)
                if (steps != t.steps) {
                    (t.steps - steps.toSet()).forEach { t = t.logged(EventType.NEXT_CLEARED, today, text = it) }
                    (steps - t.steps.toSet()).forEach { t = t.logged(EventType.NEXT_SET, today, text = it) }
                    t = t.copy(steps = steps)
                }
                if (dr.size != t.size) t = t.copy(size = dr.size).logged(EventType.RESIZED, today, text = dr.size.label)
                val why = dr.why.trim().ifEmpty { null }
                if (why != t.why) { t = t.copy(why = why); if (why != null) t = t.logged(EventType.WHY_SET, today) }
                t = t.copy(color = dr.color)
            }
            t
        }
        val t = d.tasks.first { it.id == id }
        if (t.kind == Kind.BIG && t.isOpen) {
            val cur = if (t.status == Status.ACTIVE) Logic.active(d).indexOf(t) else null
            if (dr.place != cur) d = Logic.place(d, id, dr.place, settings, today)
        }
        return d
    }

    // ---------- notes about the app ----------

    fun addAppNote(text: String) {
        if (text.isBlank()) return
        val n = io.github.viggnah.accomplish.data.AppNote("a" + rnd.nextLong(1L shl 40).toString(36), System.currentTimeMillis(), text.trim())
        act(null, undoable = false) { it.copy(appNotes = it.appNotes + n) }
    }

    fun toggleAppNote(id: String) = act(null, undoable = false) { d ->
        d.copy(appNotes = d.appNotes.map { if (it.id == id) it.copy(done = !it.done) else it })
    }

    fun editAppNote(id: String, text: String) {
        if (text.isBlank()) return
        act("Note edited", undoable = true) { d -> d.copy(appNotes = d.appNotes.map { if (it.id == id) it.copy(text = text.trim()) else it }) }
    }

    fun deleteAppNote(id: String) = act("Note deleted", undoable = true) { d -> d.copy(appNotes = d.appNotes.filter { it.id != id }) }

    // ---------- capsules ----------

    /** The capsule that was cracked open this moment (so the overlay plays the opening animation). */
    var justOpened: String? = null

    fun openCapsule(id: String) {
        data = Capsules.open(data, id, settings, bank, today, rnd)
        persist()
        justOpened = id
        opening = id
    }

    fun seal(capsuleId: String, text: String) {
        if (text.isBlank()) return
        data = Capsules.seal(data, capsuleId, text, today, rnd)
        persist()
        showToast("Sealed for later", false)
    }

    fun setQuest(capsuleId: String, state: QuestState) {
        data = Capsules.setQuest(data, capsuleId, state)
        persist()
        when (state) {
            QuestState.ACCEPTED -> showToast("Side quest accepted", false)
            QuestState.DONE -> showToast("Side quest done", false)
            else -> {}
        }
    }
}
