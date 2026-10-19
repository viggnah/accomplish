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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
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
private fun Group(title: String, note: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val p = pal()
    Spacer(Modifier.height(16.dp))
    Column(Modifier.fillMaxWidth().inset(p, PanelShape).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
        Txt(title, Type.display(16.sp), p.inkSoft)
        if (note != null) Txt(note, Type.body(14.sp), p.inkSoft, Modifier.padding(top = 4.dp, bottom = 6.dp))
        content()
    }
}

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
private fun ToggleRow(state: AppState, label: String, hint: String?, value: Boolean, set: Settings.(Boolean) -> Settings) {
    SetRow(label, hint) { Toggle(value) { state.updateSettings(state.settings.set(it)) } }
}

@Composable
fun SettingsScreen(state: AppState) {
    val s = state.settings
    val p = pal()
    var people by remember { mutableStateOf(s.people.joinToString("\n")) }
    PageColumn {
        Header(state, "Settings")
        ScreenBody {
            Group("Names") {
                listOf(
                    Triple("Big, important tasks", s.nameBig, "IMP") to { v: String -> s.copy(nameBig = v) },
                    Triple("Everyday small tasks", s.nameSmall, "Small stuff") to { v: String -> s.copy(nameSmall = v) },
                    Triple("Where big tasks wait", s.nameWaiting, "Waiting room") to { v: String -> s.copy(nameWaiting = v) },
                    Triple("Where finished tasks go", s.nameHistory, "History") to { v: String -> s.copy(nameHistory = v) },
                ).forEach { (t, set) ->
                    Txt(t.first, Type.body(14.sp, FontWeight.Bold), p.inkSoft, Modifier.padding(top = 10.dp, bottom = 6.dp))
                    Field(t.second, { state.updateSettings(set(it)) }, t.third, color = p.surface)
                }
                Spacer(Modifier.height(8.dp))
            }
            Group(s.big) {
                NumRow(state, "slots", "Spots on Home", null, s.slots) { copy(slots = it) }
                ToggleRow(state, "Frost untouched ones", null, s.frost) { copy(frost = it) }
                NumRow(state, "frostStart", "Frost starts after", "days untouched", s.frostStart) { copy(frostStart = it) }
                NumRow(state, "frostFull", "Fully frosted after", "days untouched", s.frostFull) { copy(frostFull = it) }
            }
            Group(s.small) {
                ToggleRow(state, "Warm up near a deadline", null, s.heat) { copy(heat = it) }
                NumRow(state, "heatWindow", "Start warming up", "days before the deadline", s.heatWindow) { copy(heatWindow = it) }
                NumRow(state, "fadeStart", "Fade untouched ones after", "days, if there's no deadline", s.fadeStart) { copy(fadeStart = it) }
                NumRow(state, "askAfter", "Ask “Still want this?” after", "days untouched", s.askAfter) { copy(askAfter = it) }
            }
            Group("Notifications") {
                val ctx = LocalContext.current
                ToggleRow(state, "Notifications", null, s.notify) {
                    if (it && ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        (ctx as? android.app.Activity)?.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
                    }
                    copy(notify = it)
                }
                if (s.notify) {
                    ToggleRow(state, "Deadline in 2 days", null, s.remindTwoDays) { copy(remindTwoDays = it) }
                    ToggleRow(state, "Deadline tomorrow", null, s.remindOneDay) { copy(remindOneDay = it) }
                    ToggleRow(state, "Include ${s.small}", "for deadline reminders", s.remindSmall) { copy(remindSmall = it) }
                    ToggleRow(state, "When an ${s.big} starts to frost", null, s.frostNudge) { copy(frostNudge = it) }
                    ToggleRow(state, "Weekly review", null, s.weeklyReview) { copy(weeklyReview = it) }
                    if (s.weeklyReview) {
                        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                        SetRow("Review on", "evening, at a random time") {
                            Stepper(days[s.reviewDay - 1],
                                onMinus = { state.updateSettings(s.copy(reviewDay = if (s.reviewDay == 1) 7 else s.reviewDay - 1)) },
                                onPlus = { state.updateSettings(s.copy(reviewDay = if (s.reviewDay == 7) 1 else s.reviewDay + 1)) })
                        }
                    }
                    ToggleRow(state, "Now and then: anything new?", null, s.randomPrompt) { copy(randomPrompt = it) }
                    NumRow(state, "remindHour", "Time for deadlines and nudges", "hour of the day", s.remindHour) { copy(remindHour = it) }
                    NumRow(state, "quietStart", "Quiet from", "hour", s.quietStart) { copy(quietStart = it) }
                    NumRow(state, "quietEnd", "Quiet until", "hour", s.quietEnd) { copy(quietEnd = it) }
                    NumRow(state, "maxPerDay", "Nudges per day, at most", "your own reminders always come", s.maxPerDay) { copy(maxPerDay = it) }
                }
            }
            Group("Capsules") {
                ToggleRow(state, "Capsules", null, s.capsules) { copy(capsules = it) }
                NumRow(state, "stepDropPercent", "Chance of one for a step", "%, first step on each task per day", s.stepDropPercent) { copy(stepDropPercent = it) }
                NumRow(state, "smallDropPercent", "Chance of one for ${s.small}", "%, at most once a day", s.smallDropPercent) { copy(smallDropPercent = it) }
                Txt("Side quests with other people", Type.body(16.sp, FontWeight.Bold), p.ink, Modifier.padding(top = 10.dp, bottom = 8.dp))
                Segmented(SocialRate.entries.map { it to it.label }, s.socialRate) { state.updateSettings(s.copy(socialRate = it)) }
                Txt("People in your life", Type.body(16.sp, FontWeight.Bold), p.ink, Modifier.padding(top = 14.dp))
                Txt("One per line", Type.body(12.sp), p.inkSoft, Modifier.padding(bottom = 8.dp))
                Field(people, { people = it; state.updateSettings(state.settings.copy(people = it.split('\n').map(String::trim).filter(String::isNotEmpty))) },
                    "Amma\nA close friend's name", singleLine = false, minLines = 3, color = p.surface)
                Spacer(Modifier.height(8.dp))
            }
            Group("Undo and adding") {
                NumRow(state, "undoSecs", "Undo window", "seconds to take back any change", s.undoSecs, offLabel = "Off") { copy(undoSecs = it) }
                Txt("The + button adds", Type.body(16.sp, FontWeight.Bold), p.ink, Modifier.padding(top = 10.dp, bottom = 8.dp))
                Segmented(listOf(Kind.SMALL to s.small, Kind.BIG to s.big), s.captureDefault) { state.updateSettings(s.copy(captureDefault = it)) }
                Spacer(Modifier.height(8.dp))
            }
            Group("Looks") {
                Spacer(Modifier.height(8.dp))
                Segmented(listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"), s.theme) { state.updateSettings(s.copy(theme = it)) }
            }
            Group("Storage") {
                Txt(File(s.dataDir ?: "-", Storage.FILE).path, Type.body(14.sp, FontWeight.Bold), p.ink, Modifier.padding(vertical = 6.dp))
                NumRow(state, "backups", "Daily backups kept", "in the backups folder next to it", s.backups) { copy(backups = it) }
                ClayButton("Move to another location", Modifier.padding(vertical = 8.dp)) { state.chooseLocationAgain() }
                Spacer(Modifier.height(8.dp))
            }
            About(state)
        }
    }
}

/** The app's own card: icon, name, version, and what you've got done with it so far. */
@Composable
private fun About(state: AppState) {
    val p = pal()
    val ctx = LocalContext.current
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "" }
    val d = state.data
    val since = d.tasks.mapNotNull { it.log.firstOrNull()?.day }.minOrNull()
    val big = d.tasks.count { it.kind == Kind.BIG && it.status == io.github.viggnah.accomplish.data.Status.DONE }
    val small = d.tasks.count { it.kind == Kind.SMALL && it.status == io.github.viggnah.accomplish.data.Status.DONE }
    val opened = d.capsules.count { it.opened }
    Spacer(Modifier.height(28.dp))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Like the launcher: the middle 72 of the icon's 108 units, on its own tile.
        Box(Modifier.size(96.dp).clip(PanelShape).background(p.surface).then(if (p.dark) Modifier else Modifier.border(1.dp, p.line, PanelShape)),
            contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(io.github.viggnah.accomplish.R.drawable.ic_launcher_foreground), null,
                Modifier.requiredSize(144.dp))
        }
        Spacer(Modifier.height(14.dp))
        Txt("Accomplish", Type.display(24.sp), p.ink)
        Txt("Version $version", Type.body(14.sp), p.inkSoft)
        if (since != null && big + small > 0) {
            Spacer(Modifier.height(18.dp))
            Txt("Since ${Logic.fmtLong(since)}", Type.label, p.inkSoft)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("$big", "${state.settings.big} done")
                Stat("$small", "${state.settings.small} done")
                Stat("$opened", if (opened == 1) "Capsule opened" else "Capsules opened")
            }
        }
        Spacer(Modifier.height(18.dp))
        Txt("Fonts: Fredoka and Nunito, under the SIL Open Font License", Type.body(12.sp), p.inkSoft, align = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Stat(n: String, label: String) {
    val p = pal()
    Column(Modifier.weight(1f).inset(p, BtnShape).padding(vertical = 12.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Txt(n, Type.display(24.sp), p.ink)
        Txt(label, Type.body(12.sp, FontWeight.Bold), p.inkSoft, align = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
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
