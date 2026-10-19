package io.github.viggnah.accomplish.ui

import android.content.Intent
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val stamp = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.UK)
private fun fmtAt(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(stamp)

/** Ideas and feedback about the app itself, kept in data.json next to everything else. */
@Composable
fun AppNotesScreen(state: AppState) {
    val p = pal()
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    var text by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    val notes = state.data.appNotes.sortedWith(compareBy({ it.done }, { -it.at }))
    val add = { if (text.isNotBlank()) { state.addAppNote(text); text = ""; focus.clearFocus(force = true) } }
    PageColumn {
        Header(state, "App notes")
        ScreenBody {
            Field(text, { text = it }, "An idea, a bug, anything", singleLine = false, minLines = 2)
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                ClayButton("Add note", kind = BtnKind.PRIMARY, enabled = text.isNotBlank(), onClick = add)
                Spacer(Modifier.weight(1f))
                if (notes.isNotEmpty()) ClayButton("Share all", kind = BtnKind.GHOST, icon = Icons.share) {
                    val body = notes.joinToString("\n") { "${if (it.done) "[x]" else "[ ]"} ${fmtAt(it.at)}  ${it.text}" }
                    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Accomplish app notes\n\n$body") }
                    ctx.startActivity(Intent.createChooser(send, null))
                }
            }
            Spacer(Modifier.height(18.dp))
            notes.forEach { n ->
                Row(Modifier.fillMaxWidth().bleed(12.dp).padding(vertical = 4.dp)) {
                    TapBox({ state.toggleAppNote(n.id) }) {
                        Box(
                            Modifier.size(24.dp).then(if (n.done) Modifier.background(p.ink, CircleShape) else Modifier.border(2.dp, p.inkSoft, CircleShape)),
                            contentAlignment = Alignment.Center,
                        ) { if (n.done) Ico(Icons.check, p.bg, 14.dp, stroke = 3f) }
                    }
                    if (editing == n.id) {
                        Column(Modifier.weight(1f)) {
                            Field(editText, { editText = it }, "", singleLine = false)
                            Row(Modifier.padding(top = 8.dp)) {
                                ClayButton("Save", kind = BtnKind.PRIMARY) { state.editAppNote(n.id, editText); editing = null; focus.clearFocus(force = true) }
                                Spacer(Modifier.width(8.dp))
                                ClayButton("Cancel", kind = BtnKind.GHOST) { editing = null; focus.clearFocus(force = true) }
                            }
                        }
                    } else {
                        Column(Modifier.weight(1f).padding(top = 12.dp, bottom = 6.dp).squish(scaleDown = 0.98f) { editing = n.id; editText = n.text }) {
                            Txt(n.text, Type.body(16.sp).copy(textDecoration = if (n.done) TextDecoration.LineThrough else null), if (n.done) p.inkSoft else p.ink)
                            Txt(fmtAt(n.at), Type.body(12.sp, FontWeight.Bold), p.inkSoft)
                        }
                    }
                    TapBox({ state.deleteAppNote(n.id) }) {
                        Ico("M5 7h14M10 7V5h4v2M7 7l1 12h8l1-12", p.inkSoft, 18.dp, stroke = 2f)
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
            }
        }
    }
}
