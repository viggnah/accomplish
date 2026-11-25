package io.github.viggnah.accomplish.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.viggnah.accomplish.data.Kind
import io.github.viggnah.accomplish.data.Settings
import io.github.viggnah.accomplish.data.SocialRate
import io.github.viggnah.accomplish.data.Storage
import io.github.viggnah.accomplish.data.ThemeMode
import io.github.viggnah.accomplish.logic.Logic
import java.io.File

@Composable
private fun SetRow(label: String, hint: String? = null, control: @Composable () -> Unit) {
    val p = pal()
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt(label, Type.body(16.sp, FontWeight.Bold), p.ink)
            if (hint != null) Txt(hint, Type.body(12.sp), p.inkSoft)
        }
        Spacer(Modifier.width(12.dp))
        control()
    }
}

@Composable
private fun NumRow(state: AppState, key: String, label: String, hint: String?, value: Int, offLabel: String? = null, set: Settings.(Int) -> Settings) {
    val (lo, hi, step) = Settings.bounds.getValue(key)
    SetRow(label, hint) {
        Stepper(if (value == 0 && offLabel != null) offLabel else "$value",
            onMinus = { state.updateSettings(fix(state.settings.set((value - step).coerceIn(lo, hi)))) },
            onPlus = { state.updateSettings(fix(state.settings.set((value + step).coerceIn(lo, hi)))) })
    }
}

/** Keep paired numbers sensible: full frost after frost starts, asking after fading starts. */
private fun fix(s: Settings) = s.copy(
    frostFull = maxOf(s.frostFull, s.frostStart + 1),
    askAfter = maxOf(s.askAfter, s.fadeStart + 1),
)

@Composable
internal fun ToggleRow(state: AppState, label: String, hint: String?, value: Boolean, set: Settings.(Boolean) -> Settings) {
    SetRow(label, hint) { Toggle(value) { state.updateSettings(state.settings.set(it)) } }
}

enum class SettingsPage { NAMES, BIG, SMALL, NOTIFICATIONS, CAPSULES, ADDING, LOOKS, STORAGE, ABOUT }

/** One row of the Settings index: a name and what it's set to now. */
@Composable
private fun IndexRow(title: String, summary: String, onClick: () -> Unit) {
    val p = pal()
    Row(
        Modifier.fillMaxWidth().squish(scaleDown = 0.98f, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Txt(title, Type.body(16.sp, FontWeight.Bold), p.ink)
            Txt(summary, Type.body(12.sp), p.inkSoft, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Ico("M9 6l6 6-6 6", p.inkSoft, 16.dp, stroke = 2.6f)
    }
}

/** A row whose stepper runs through [labels] (for example Off, 1 … 30). */
@Composable
private fun ChoiceRow(label: String, hint: String?, labels: List<String>, index: Int, onPick: (Int) -> Unit) {
    SetRow(label, hint) {
        Stepper(labels[index.coerceIn(0, labels.lastIndex)],
            onMinus = { onPick((index - 1).coerceAtLeast(0)) },
            onPlus = { onPick((index + 1).coerceAtMost(labels.lastIndex)) })
    }
}

private fun hour(h: Int) = "%02d:00".format(h)

@Composable
fun SettingsScreen(state: AppState) {
    val s = state.settings
    val page = state.settingsPage
    val version = appVersion()
    val titles = mapOf(
        SettingsPage.NAMES to "Names", SettingsPage.BIG to s.big, SettingsPage.SMALL to s.small,
        SettingsPage.NOTIFICATIONS to "Notifications", SettingsPage.CAPSULES to "Capsules", SettingsPage.ADDING to "Undo and adding",
        SettingsPage.LOOKS to "Looks", SettingsPage.STORAGE to "Storage", SettingsPage.ABOUT to "About",
    )
    PageColumn {
        if (page == null) {
            Header(state, "Settings")
            ScreenBody {
                val p = pal()
                val summaries = mapOf(
                    SettingsPage.NAMES to listOf(s.big, s.small, s.waiting, s.history).joinToString(", "),
                    SettingsPage.BIG to "${Logic.plural(s.slots, "spot")}, " + (if (s.frost) "frost after ${Logic.plural(s.frostStart, "day")}" else "no frost"),
                    SettingsPage.SMALL to (if (s.heat) "Warm up ${Logic.plural(s.heatWindow, "day")} before" else "No warming") + ", fade after ${Logic.plural(s.fadeStart, "day")}",
                    SettingsPage.NOTIFICATIONS to (if (s.notify) "On, quiet ${hour(s.quietStart)} to ${hour(s.quietEnd)}" else "Off"),
                    SettingsPage.CAPSULES to (if (s.capsules) "On" else "Off"),
                    SettingsPage.ADDING to (if (s.undoSecs > 0) "Undo for ${s.undoSecs} seconds" else "No undo") + ", + adds ${if (s.captureDefault == Kind.BIG) s.big else s.small}",
                    SettingsPage.LOOKS to "${s.theme.name.lowercase().replaceFirstChar { it.uppercase() }} theme, ${s.cardStyle.label} cards",
                    SettingsPage.STORAGE to File(s.dataDir ?: "-").path,
                    SettingsPage.ABOUT to "Accomplish $version",
                )
                Spacer(Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth().inset(p, PanelShape).padding(vertical = 4.dp)) {
                    SettingsPage.entries.forEachIndexed { i, sp ->
                        if (i > 0) Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(p.line))
                        IndexRow(titles.getValue(sp), summaries.getValue(sp)) { state.settingsPage = sp }
                    }
                }
            }
        } else {
            Header(state, titles.getValue(page), backLabel = "Settings")
            ScreenBody { SettingsPageBody(state, page, version) }
        }
    }
}

@Composable
private fun appVersion(): String {
    val ctx = LocalContext.current
    return remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "" }
}

@Composable
private fun SettingsPageBody(state: AppState, page: SettingsPage, version: String) {
    val s = state.settings
    val p = pal()
    val ctx = LocalContext.current
    val upTo30 = listOf("Off") + (1..30).map { "$it" }
    Column(Modifier.fillMaxWidth().inset(p, PanelShape).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)) {
        when (page) {
            SettingsPage.NAMES -> {
                listOf(
                    Triple("Big, important tasks", s.nameBig, "IMP") to { v: String -> state.settings.copy(nameBig = v) },
                    Triple("Everyday small tasks", s.nameSmall, "Small stuff") to { v: String -> state.settings.copy(nameSmall = v) },
                    Triple("Where big tasks wait", s.nameWaiting, "Waiting room") to { v: String -> state.settings.copy(nameWaiting = v) },
                    Triple("Where finished tasks go", s.nameHistory, "History") to { v: String -> state.settings.copy(nameHistory = v) },
                ).forEach { (t, set) ->
                    Txt(t.first, Type.body(14.sp, FontWeight.Bold), p.inkSoft, Modifier.padding(top = 10.dp, bottom = 6.dp))
                    Field(t.second, { state.updateSettings(set(it)) }, t.third, color = p.surface)
                }
                Spacer(Modifier.height(8.dp))
            }
            SettingsPage.BIG -> {
                NumRow(state, "slots", "Spots on Home", null, s.slots) { copy(slots = it) }
                // One row for frost: Off, or the day it starts. Full frost keeps its distance from the start.
                ChoiceRow("Frost untouched ones after", "days", upTo30, if (s.frost) s.frostStart else 0) { i ->
                    val gap = maxOf(1, s.frostFull - s.frostStart)
                    state.updateSettings(if (i == 0) s.copy(frost = false) else s.copy(frost = true, frostStart = i, frostFull = i + gap))
                }
            }
            SettingsPage.SMALL -> {
                ChoiceRow("Warm up before a deadline", "days", upTo30, if (s.heat) s.heatWindow else 0) { i ->
                    state.updateSettings(if (i == 0) s.copy(heat = false) else s.copy(heat = true, heatWindow = i))
                }
                // Fading and "Still want this?" move together, keeping their distance.
                NumRow(state, "fadeStart", "Fade untouched ones after", "days, then ask “Still want this?” ${Logic.plural(s.askAfter - s.fadeStart, "day")} later", s.fadeStart) {
                    copy(fadeStart = it, askAfter = it + maxOf(1, s.askAfter - s.fadeStart))
                }
            }
            SettingsPage.NOTIFICATIONS -> {
                ToggleRow(state, "Notifications", null, s.notify) {
                    if (it && ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        (ctx as? android.app.Activity)?.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
                    }
                    copy(notify = it)
                }
                if (s.notify) {
                    Txt("Before a deadline", Type.body(16.sp, FontWeight.Bold), p.ink, Modifier.padding(top = 10.dp, bottom = 8.dp))
                    val dl = when { s.remindTwoDays -> 2; s.remindOneDay -> 1; else -> 0 }
                    Segmented(listOf(0 to "Off", 1 to "1 day", 2 to "1 and 2 days"), dl) {
                        state.updateSettings(s.copy(remindOneDay = it >= 1, remindTwoDays = it == 2))
                    }
                    if (dl > 0) ToggleRow(state, "Include ${s.small}", null, s.remindSmall) { copy(remindSmall = it) }
                    ToggleRow(state, "When an ${s.big} starts to frost", null, s.frostNudge) { copy(frostNudge = it) }
                    val days = listOf("Off", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                    ChoiceRow("Weekly review", "evening, at a random time", days, if (s.weeklyReview) s.reviewDay else 0) { i ->
                        state.updateSettings(if (i == 0) s.copy(weeklyReview = false) else s.copy(weeklyReview = true, reviewDay = i))
                    }
                    ToggleRow(state, "Now and then: anything new?", null, s.randomPrompt) { copy(randomPrompt = it) }
                    NumRow(state, "remindHour", "Time for deadlines and nudges", "hour of the day", s.remindHour) { copy(remindHour = it) }
                    SetRow("Quiet hours", "your own reminders still come") {
                        ClayButton("${hour(s.quietStart)} to ${hour(s.quietEnd)}") {
                            val (lo, hi) = Settings.bounds.getValue("quietStart").let { it.first to it.second }
                            val (lo2, hi2) = Settings.bounds.getValue("quietEnd").let { it.first to it.second }
                            android.app.TimePickerDialog(ctx, { _, h1, _ ->
                                android.app.TimePickerDialog(ctx, { _, h2, _ ->
                                    state.updateSettings(state.settings.copy(quietStart = h1.coerceIn(lo, hi), quietEnd = h2.coerceIn(lo2, hi2)))
                                }, s.quietEnd, 0, true).show()
                            }, s.quietStart, 0, true).show()
                        }
                    }
                    NumRow(state, "maxPerDay", "Nudges per day, at most", null, s.maxPerDay) { copy(maxPerDay = it) }
                }
            }
            SettingsPage.CAPSULES -> {
                ToggleRow(state, "Capsules", null, s.capsules) { copy(capsules = it) }
                if (s.capsules) {
                    NumRow(state, "stepDropPercent", "Chance of one for a step", "%, first step on each task per day", s.stepDropPercent) { copy(stepDropPercent = it) }
                    NumRow(state, "smallDropPercent", "Chance of one for ${s.small}", "%, at most once a day", s.smallDropPercent) { copy(smallDropPercent = it) }
                    Txt("Side quests with other people", Type.body(16.sp, FontWeight.Bold), p.ink, Modifier.padding(top = 10.dp, bottom = 8.dp))
                    Segmented(SocialRate.entries.map { it to it.label }, s.socialRate) { state.updateSettings(state.settings.copy(socialRate = it)) }
                    var people by remember { mutableStateOf(s.people.joinToString("\n")) }
                    Txt("People in your life", Type.body(16.sp, FontWeight.Bold), p.ink, Modifier.padding(top = 14.dp))
                    Txt("One per line", Type.body(12.sp), p.inkSoft, Modifier.padding(bottom = 8.dp))
                    Field(people, { people = it; state.updateSettings(state.settings.copy(people = it.split('\n').map(String::trim).filter(String::isNotEmpty))) },
                        "Amma\nA close friend's name", singleLine = false, minLines = 3, color = p.surface)
                    Spacer(Modifier.height(8.dp))
                }
            }
            SettingsPage.ADDING -> {
                NumRow(state, "undoSecs", "Undo window", "seconds to take back any change", s.undoSecs, offLabel = "Off") { copy(undoSecs = it) }
                Txt("The + button adds", Type.body(16.sp, FontWeight.Bold), p.ink, Modifier.padding(top = 10.dp, bottom = 8.dp))
                Segmented(listOf(Kind.SMALL to s.small, Kind.BIG to s.big), s.captureDefault) { state.updateSettings(state.settings.copy(captureDefault = it)) }
                Spacer(Modifier.height(8.dp))
            }
            SettingsPage.LOOKS -> {
                Txt("Theme", Type.body(16.sp, FontWeight.Bold), p.ink, Modifier.padding(top = 6.dp, bottom = 8.dp))
                Segmented(listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"), s.theme) { state.updateSettings(state.settings.copy(theme = it)) }
                Txt("Card colours", Type.body(16.sp, FontWeight.Bold), p.ink, Modifier.padding(top = 14.dp, bottom = 8.dp))
                Segmented(io.github.viggnah.accomplish.data.CardStyle.entries.map { it to it.label }, s.cardStyle) {
                    state.updateSettings(state.settings.copy(cardStyle = it))
                }
                Spacer(Modifier.height(8.dp))
            }
            SettingsPage.STORAGE -> {
                Txt(File(s.dataDir ?: "-", Storage.FILE).path, Type.body(14.sp, FontWeight.Bold), p.ink, Modifier.padding(vertical = 6.dp))
                NumRow(state, "backups", "Daily backups kept", "in the backups folder next to it", s.backups) { copy(backups = it) }
                ClayButton("Move to another location", Modifier.padding(vertical = 8.dp)) { state.chooseLocationAgain() }
                Spacer(Modifier.height(8.dp))
            }
            SettingsPage.ABOUT -> {
                Txt("Accomplish $version", Type.body(16.sp, FontWeight.Bold), p.ink, Modifier.padding(top = 6.dp))
                Txt("Free software under the GPL-3.0. Fonts: Fredoka and Nunito, under the SIL Open Font License.", Type.body(14.sp), p.inkSoft, Modifier.padding(top = 4.dp))
                ClayButton("Source code", Modifier.padding(vertical = 8.dp), icon = Icons.share) {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/viggnah/accomplish"))) }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

// ---------------- First run, missing card, and errors ----------------

@Composable
fun SetupScreen(state: AppState, phase: Phase) {
    val p = pal()
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(30.dp))
        Box(Modifier.size(72.dp).tile(p, CircleShape, p.cardBrush(4), 14.dp), contentAlignment = Alignment.Center) {
            Ico(Icons.folder, p.cardInk, 34.dp)
        }
        when (phase) {
            Phase.Loading -> {}
            Phase.NeedsAccess -> {
                Txt("Hello there", Type.display(36.sp), p.ink)
                Txt("Accomplish keeps your tasks in a folder on your phone that you can see, copy and back up. It stays put even if the app is reinstalled.",
                    Type.body(16.sp), p.ink)
                Txt("Android asks you to allow “All files access” for that. Nothing ever leaves your phone.", Type.body(16.sp), p.inkSoft)
                ClayButton("Allow file access", Modifier.fillMaxWidth(), kind = BtnKind.PRIMARY, big = true) {
                    val i = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                    runCatching { ctx.startActivity(i) }.onFailure { ctx.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                }
                Hint("Turn the switch on, then come back here.")
            }
            Phase.NeedsLocation -> {
                Txt("Where should it live?", Type.display(36.sp), p.ink)
                val vols = remember(refresh) { state.volumes() }
                vols.forEach { v ->
                    Row(
                        Modifier.fillMaxWidth().tile(p, PanelShape, p.pebble, 6.dp).squish(scaleDown = 0.97f) { state.useVolume(v) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Txt(if (v.removable) "SD card (${v.label})" else "Phone storage", Type.display(18.sp), p.ink)
                            Txt(File(v.root, Storage.FOLDER).path, Type.body(12.sp), p.inkSoft)
                        }
                    }
                }
                ClayButton("Check again", kind = BtnKind.GHOST) { refresh++ }
            }
            is Phase.Missing -> {
                Txt("Your tasks can't be reached", Type.display(36.sp), p.ink)
                Txt("They're in ${phase.dir}. If that's on an SD card, check that the card is in. Nothing has been changed.",
                    Type.body(16.sp), p.ink)
                ClayButton("Try again", Modifier.fillMaxWidth(), kind = BtnKind.PRIMARY, big = true) { state.start() }
                ClayButton("Use a different location", Modifier.fillMaxWidth(), kind = BtnKind.GHOST) { state.chooseLocationAgain() }
            }
            is Phase.Failed -> {
                Txt("Something went wrong", Type.display(36.sp), p.ink)
                Txt(phase.message, Type.body(16.sp), p.ink)
                ClayButton("Try again", Modifier.fillMaxWidth(), kind = BtnKind.PRIMARY, big = true) { state.start() }
                ClayButton("Use a different location", Modifier.fillMaxWidth(), kind = BtnKind.GHOST) { state.chooseLocationAgain() }
            }
            Phase.Ready -> {}
        }
    }
}
