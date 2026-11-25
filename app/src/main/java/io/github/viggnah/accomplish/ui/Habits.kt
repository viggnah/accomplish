package io.github.viggnah.accomplish.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.viggnah.accomplish.logic.Logic
import io.github.viggnah.accomplish.logic.Sleep
import java.time.ZoneId

/** Habits. For now just sleep: when you went to bed, night by night, against a target. */
@Composable
fun HabitsScreen(state: AppState) {
    val p = pal()
    val s = state.settings
    val ctx = LocalContext.current
    val zone = ZoneId.systemDefault()
    val is24 = android.text.format.DateFormat.is24HourFormat(ctx)
    val tonight = Sleep.nightOf(System.currentTimeMillis(), zone)
    val nights = (6 downTo 0).map { tonight - it }
    val target = s.bedTarget
    fun pickTime(startMinutes: Int, onPick: (Int) -> Unit) =
        TimePickerDialog(ctx, { _, h, m -> onPick(h * 60 + m) }, startMinutes / 60, startMinutes % 60, is24).show()

    PageColumn {
        Header(state, "Habits")
        ScreenBody {
            Column(Modifier.fillMaxWidth().tile(p, PanelShape, p.pebble, 4.dp).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ico(Icons.moon, p.ink, 20.dp)
                    Spacer(Modifier.width(10.dp))
                    Txt("Bedtime", Type.display(18.sp), p.ink, Modifier.weight(1f))
                    ClayButton("By ${Sleep.clock(target)}") { pickTime(target) { state.updateSettings(state.settings.copy(bedTarget = it)) } }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth()) {
                    nights.forEach { night ->
                        val at = Sleep.bedtime(state.data, night, zone)
                        val onTime = at != null && Sleep.onTime(at, target, zone)
                        Column(
                            Modifier.weight(1f).squish(scaleDown = 0.95f) {
                                val start = at?.let { Sleep.minutesAfterNoon(it, zone) + 12 * 60 } ?: target
                                pickTime(Math.floorMod(start, 24 * 60)) { m -> state.setBedtime(night, Math.floorMod(m - 12 * 60, 24 * 60)) }
                            },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Txt(Logic.date(night).dayOfWeek.name.take(1), Type.body(12.sp, FontWeight.Bold), p.inkSoft)
                            Box(
                                Modifier.padding(vertical = 4.dp).size(26.dp).then(
                                    when {
                                        at == null -> Modifier.border(2.dp, p.line, CircleShape)
                                        onTime -> Modifier.background(p.accent, CircleShape)
                                        else -> Modifier.background(p.heat, CircleShape)
                                    },
                                ),
                            )
                            Txt(at?.let { Sleep.clock(it, zone) } ?: "", Type.body(12.sp, FontWeight.Bold), p.ink, maxLines = 1)
                        }
                    }
                }
                if (nights.any { Sleep.bedtime(state.data, it, zone) != null }) {
                    val n = Sleep.onTimeCount(state.data, nights, target, zone)
                    Txt("$n of 7 nights by ${Sleep.clock(target)}", Type.body(14.sp, FontWeight.Bold), p.inkSoft, Modifier.padding(top = 12.dp))
                }
                Spacer(Modifier.height(14.dp))
                ClayButton("Going to bed now", icon = Icons.moon) { state.recordBedtime() }
                Spacer(Modifier.height(6.dp))
                ToggleRow(state, "Wind-down reminder at ${Sleep.clock(Math.floorMod(target - 30, 24 * 60))}", null, s.bedReminder) { copy(bedReminder = it) }
            }
        }
    }
}
