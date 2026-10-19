package io.github.viggnah.accomplish.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.viggnah.accomplish.data.Capsule
import io.github.viggnah.accomplish.data.QuestState
import io.github.viggnah.accomplish.data.Reward
import io.github.viggnah.accomplish.data.RewardType
import io.github.viggnah.accomplish.logic.Logic

private fun iconFor(t: RewardType) = when (t) {
    RewardType.QUEST -> Icons.quest
    RewardType.LESSON -> Icons.lesson
    RewardType.SEAL_PROMPT, RewardType.SEALED_MESSAGE, RewardType.WHY_MESSAGE -> Icons.note
    RewardType.MEMORY -> Icons.history
}

/** Full-screen moment: the capsule cracks open, then what was inside slides up. */
@Composable
fun BoxScope.CapsuleOverlay(state: AppState) {
    val p = pal()
    val id = state.opening
    var last by remember { mutableStateOf<String?>(null) }
    if (id != null) last = id
    AnimatedVisibility(id != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
        val cid = last ?: return@AnimatedVisibility
        val c = state.data.capsules.firstOrNull { it.id == cid } ?: return@AnimatedVisibility
        val r = c.reward ?: return@AnimatedVisibility
        // Only play the crack when it was opened just now; reopening from the collection shows the card straight away.
        val crack = remember(cid) { Animatable(if (state.justOpened == cid) 0f else 1f).also { state.justOpened = null } }
        val card = remember(cid) { Animatable(if (crack.value >= 1f) 1f else 0f) }
        LaunchedEffect(cid) {
            if (crack.value < 1f) {
                crack.animateTo(1f, tween(650))
                card.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 300f))
            }
        }
        Box(
            Modifier.fillMaxSize().background(p.scrim).clickable(remember { MutableInteractionSource() }, null) { if (card.value > 0.9f) state.opening = null }
                .safeDrawingPadding().imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            if (crack.value < 1f) {
                CapsuleShape(Modifier.size(width = 90.dp, height = 120.dp).graphicsLayer {
                    alpha = 1f - ((crack.value - 0.7f) / 0.3f).coerceIn(0f, 1f)
                    scaleX = 1f + crack.value * 0.3f; scaleY = scaleX
                }, split = crack.value)
            }
            Column(
                Modifier.padding(20.dp).widthIn(max = 420.dp).fillMaxWidth()
                    .graphicsLayer { alpha = card.value; translationY = (1f - card.value) * 80.dp.toPx(); scaleX = 0.9f + 0.1f * card.value; scaleY = scaleX }
                    .tile(p, PanelShape, p.surface, elevation = 24.dp)
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                RewardBody(state, c, r)
            }
        }
    }
}

@Composable
private fun RewardBody(state: AppState, c: Capsule, r: Reward) {
    val p = pal()
    val ctx = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).tile(p, CircleShape, p.cardBrush(if (r.type == RewardType.LESSON) 3 else if (r.type == RewardType.QUEST) 2 else 4), 4.dp), contentAlignment = Alignment.Center) {
            Ico(iconFor(r.type), p.cardInk, 18.dp)
        }
        Spacer(Modifier.width(10.dp))
        Txt(r.label ?: "", Type.label, p.inkSoft, Modifier.weight(1f))
    }
    Spacer(Modifier.height(14.dp))
    if (r.type != RewardType.QUEST) Txt(r.title, Type.display(24.sp), p.ink, Modifier.padding(bottom = 8.dp))
    Txt(r.body, if (r.type == RewardType.QUEST) Type.display(24.sp) else Type.body(16.sp), p.ink)

    when (r.type) {
        RewardType.LESSON -> {
            if (r.extra != null) {
                Column(Modifier.padding(top = 16.dp).fillMaxWidth().inset(p, BtnShape).padding(14.dp)) {
                    Txt("Try it", Type.label, p.inkSoft)
                    Txt(r.extra, Type.body(16.sp, FontWeight.Bold), p.ink, Modifier.padding(top = 4.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            ClayButton("Got it", Modifier.fillMaxWidth(), kind = BtnKind.PRIMARY, big = true) { state.opening = null }
        }
        RewardType.QUEST -> {
            Spacer(Modifier.height(20.dp))
            when (r.questState) {
                QuestState.OFFERED, null -> {
                    ClayButton("Accept", Modifier.fillMaxWidth(), kind = BtnKind.PRIMARY, big = true) { state.setQuest(c.id, QuestState.ACCEPTED); state.opening = null }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        ClayButton("Share it", kind = BtnKind.GHOST, icon = Icons.share) { share(ctx, "A little side quest for you: ${r.body}") }
                        ClayButton("Skip", kind = BtnKind.GHOST) { state.setQuest(c.id, QuestState.SKIPPED); state.opening = null }
                    }
                }
                QuestState.ACCEPTED -> {
                    ClayButton("I did it", Modifier.fillMaxWidth(), kind = BtnKind.PRIMARY, big = true, icon = Icons.check) { state.setQuest(c.id, QuestState.DONE); state.opening = null }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        ClayButton("Share it", kind = BtnKind.GHOST, icon = Icons.share) { share(ctx, "A little side quest for you: ${r.body}") }
                        ClayButton("Skip", kind = BtnKind.GHOST) { state.setQuest(c.id, QuestState.SKIPPED); state.opening = null }
                    }
                }
                QuestState.DONE -> { Hint("Done ${Logic.ago(r.day, state.today)}"); Spacer(Modifier.height(10.dp)); ClayButton("Close", Modifier.fillMaxWidth(), big = true) { state.opening = null } }
                QuestState.SKIPPED -> { Hint("Skipped"); Spacer(Modifier.height(10.dp)); ClayButton("Close", Modifier.fillMaxWidth(), big = true) { state.opening = null } }
            }
        }
        RewardType.SEAL_PROMPT -> {
            if (r.extra == "sealed") {
                Hint("Sealed")
                Spacer(Modifier.height(16.dp))
                ClayButton("Close", Modifier.fillMaxWidth(), big = true) { state.opening = null }
            } else {
                var text by remember(c.id) { mutableStateOf("") }
                Spacer(Modifier.height(14.dp))
                Field(text, { text = it }, "Dear future me…", singleLine = false, minLines = 4)
                Spacer(Modifier.height(16.dp))
                ClayButton("Seal it", Modifier.fillMaxWidth(), kind = BtnKind.PRIMARY, big = true, enabled = text.isNotBlank()) { state.seal(c.id, text); state.opening = null }
                ClayButton("Not now", Modifier.fillMaxWidth(), kind = BtnKind.GHOST) { state.opening = null }
            }
        }
        else -> {
            r.extra?.let { Txt(it, Type.body(14.sp), p.inkSoft, Modifier.padding(top = 12.dp)) }
            Spacer(Modifier.height(20.dp))
            ClayButton("Close", Modifier.fillMaxWidth(), kind = BtnKind.PRIMARY, big = true) { state.opening = null }
        }
    }
    val more = state.unopened.size
    if (more > 0) {
        Spacer(Modifier.height(8.dp))
        Txt("Open the next one ($more)", Type.body(14.sp, FontWeight.ExtraBold), p.inkSoft,
            Modifier.fillMaxWidth().squish { state.openCapsule(state.unopened.first().id) }.padding(8.dp), align = TextAlign.Center)
    }
}

private fun share(ctx: android.content.Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    ctx.startActivity(Intent.createChooser(send, null))
}

// ---------------- Collection ----------------

@Composable
fun CollectionScreen(state: AppState) {
    val p = pal()
    val unopened = state.unopened
    val opened = state.data.capsules.filter { it.opened }.sortedByDescending { it.reward!!.day }
    val questsOn = opened.filter { it.reward!!.type == RewardType.QUEST && it.reward.questState == QuestState.ACCEPTED }
    val sealed = state.data.notes.count { !it.delivered }
    PageColumn {
        Header(state, "Capsules", sub = "${opened.size} opened" + if (sealed > 0) ", ${Logic.plural(sealed, "note")} sealed for later" else "")
        ScreenBody {
            if (!state.settings.capsules) Hint("Capsules are off. Turn them on in Settings.")
            if (unopened.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().tile(p, PanelShape, p.pebble, 8.dp).squish { state.openCapsule(unopened.first().id) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CapsuleShape(Modifier.size(width = 34.dp, height = 46.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Txt("${Logic.plural(unopened.size, "capsule")} waiting", Type.display(18.sp), p.ink)
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
            if (questsOn.isNotEmpty()) {
                SectionLabel("Side quests you accepted")
                questsOn.forEach { CapsuleRow(state, it) }
            }
            SectionLabel("Opened")
            if (opened.isEmpty()) Txt("Nothing opened yet", Type.body(14.sp), p.inkSoft)
            opened.filter { it !in questsOn }.forEach { CapsuleRow(state, it) }
        }
    }
}

@Composable
private fun CapsuleRow(state: AppState, c: Capsule) {
    val p = pal()
    val r = c.reward ?: return
    Row(Modifier.fillMaxWidth().squish(scaleDown = 0.98f) { state.opening = c.id }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).inset(p, CircleShape), contentAlignment = Alignment.Center) { Ico(iconFor(r.type), p.ink, 17.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Txt(if (r.type == RewardType.QUEST) r.body else r.title, Type.body(14.sp, FontWeight.Bold), p.ink, maxLines = 2)
            val status = when (r.questState) { QuestState.ACCEPTED -> "In progress"; QuestState.DONE -> "Done"; QuestState.SKIPPED -> "Skipped"; else -> "Opened" }
            Txt("$status ${Logic.ago(r.day, state.today)}", Type.body(12.sp), p.inkSoft, maxLines = 1)
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
}
