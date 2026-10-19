package io.github.viggnah.accomplish.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.app.Notification
import io.github.viggnah.accomplish.MainActivity
import io.github.viggnah.accomplish.R
import androidx.compose.ui.graphics.toArgb
import io.github.viggnah.accomplish.ui.LightPalette
import io.github.viggnah.accomplish.data.AppData
import io.github.viggnah.accomplish.data.LoadResult
import io.github.viggnah.accomplish.data.SettingsStore
import io.github.viggnah.accomplish.data.Storage
import io.github.viggnah.accomplish.logic.Nudge
import io.github.viggnah.accomplish.logic.Reminders
import android.os.Handler
import android.os.Looper
import io.github.viggnah.accomplish.App
import io.github.viggnah.accomplish.data.Event
import io.github.viggnah.accomplish.data.EventType
import io.github.viggnah.accomplish.logic.Logic
import io.github.viggnah.accomplish.ui.Phase
import java.io.File
import java.time.ZoneId
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Posts reminders and keeps exactly one inexact alarm set for the next one.
 * Called when the app pauses, after boot, and each time the alarm fires.
 */
object Notifier {
    private const val ACTION = "io.github.viggnah.accomplish.REMIND"
    const val SNOOZE = "io.github.viggnah.accomplish.SNOOZE"
    const val EXTRA_SCREEN = "screen"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    /** Keys already sent, stored as "key|epochDay" so old ones can be dropped. */
    private fun sent(ctx: Context): Set<String> = prefs(ctx).getStringSet("sent", emptySet())!!.map { it.substringBeforeLast('|') }.toSet()

    private fun markSent(ctx: Context, keys: List<String>, counted: Int) {
        val today = LocalDate.now().toEpochDay()
        val p = prefs(ctx)
        val kept = p.getStringSet("sent", emptySet())!!.filter { (it.substringAfterLast('|').toLongOrNull() ?: 0) > today - 60 }
        val countDay = p.getLong("countDay", -1)
        val count = if (countDay == today) p.getInt("count", 0) else 0
        p.edit().putStringSet("sent", (kept + keys.map { "$it|$today" }).toSet())
            .putLong("countDay", today).putInt("count", count + counted).apply()
    }

    private fun sentToday(ctx: Context): Int {
        val p = prefs(ctx)
        return if (p.getLong("countDay", -1) == LocalDate.now().toEpochDay()) p.getInt("count", 0) else 0
    }

    private fun plan(ctx: Context, given: AppData?): List<Nudge> {
        val s = SettingsStore(ctx).load()
        val data = given ?: run {
            val dir = s.dataDir ?: return emptyList()
            (Storage(ctx).load(File(dir)) as? LoadResult.Ok)?.data ?: return emptyList()
        }
        return Reminders.plan(data, s, ZonedDateTime.now(), sent(ctx))
    }

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(Reminders.REMINDERS, "Your reminders", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(Reminders.DEADLINES, "Deadlines", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(Reminders.NUDGES, "Nudges", NotificationManager.IMPORTANCE_LOW))
    }

    /** The app's violet, which Android uses to tint the app icon in the notification. */
    private val BRAND = LightPalette.accent.toArgb()

    /** Tapping takes you to where the task lives (or to a new task, for "anything new?"). */
    private fun openIntent(ctx: Context, n: Nudge): PendingIntent =
        PendingIntent.getActivity(ctx, n.key.hashCode(),
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(EXTRA_SCREEN, n.screen),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** The system's own layout: the app icon in violet, the task as the title. Your reminders get snooze buttons. */
    private fun build(ctx: Context, n: Nudge): Notification {
        val b = Notification.Builder(ctx, n.channel)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(BRAND)
            .setContentTitle(n.title)
            .setContentText(n.text)
            .setStyle(Notification.BigTextStyle().bigText(listOfNotNull(n.text, n.detail).joinToString("\n")))
            .setCategory(if (n.type == "review" || n.type == "prompt") Notification.CATEGORY_RECOMMENDATION else Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(openIntent(ctx, n))
            .setAutoCancel(true)
        val taskId = n.key.split(':').getOrNull(1)
        if (n.channel == Reminders.REMINDERS && taskId != null) {
            listOf("hour" to "In 1 hour", "tomorrow" to "Tomorrow").forEachIndexed { i, (mode, label) ->
                val pi = PendingIntent.getBroadcast(ctx, n.key.hashCode() * 2 + i,
                    Intent(ctx, ReminderReceiver::class.java).setAction(SNOOZE)
                        .putExtra("task", taskId).putExtra("mode", mode).putExtra("notif", n.key.hashCode()),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                b.addAction(Notification.Action.Builder(null, label, pi).build())
            }
        }
        return b.build()
    }

    /** Snooze from the notification: move the task's reminder and take the notification away. */
    fun snooze(ctx: Context, taskId: String, mode: String, notifId: Int) {
        ctx.getSystemService(NotificationManager::class.java).cancel(notifId)
        val s = SettingsStore(ctx).load()
        val at = if (mode == "hour") System.currentTimeMillis() + 60 * 60 * 1000L
            else LocalDate.now().plusDays(1).atTime(s.remindHour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val app = ctx.applicationContext as App
        Handler(Looper.getMainLooper()).post {
            // With your data loaded in the app, change it there so nothing overwrites the snooze later.
            if (app.stateCreated && app.state.phase == Phase.Ready) { app.state.snooze(taskId, at); return@post }
            Thread {
                val dir = s.dataDir ?: return@Thread
                val storage = Storage(ctx)
                val data = (storage.load(File(dir)) as? LoadResult.Ok)?.data ?: return@Thread
                val today = LocalDate.now().toEpochDay()
                val updated = Logic.update(data, taskId) { it.copy(remindAt = at, log = it.log + Event(today, EventType.REMINDER_SET, num = at)) }
                runCatching { storage.writeNow(File(dir), updated, s.backups) }
                runCatching { run(ctx, updated) }
            }.start()
        }
    }

    /** Post whatever is due, then set the alarm for the next one. */
    fun run(ctx: Context, given: AppData? = null) {
        val s = SettingsStore(ctx).load()
        val all = plan(ctx, given)
        val now = System.currentTimeMillis()
        val canPost = ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (canPost) {
            val due = Reminders.dueNow(all, now, sentToday(ctx), s.maxPerDay)
            if (due.isNotEmpty()) {
                channels(ctx)
                val nm = ctx.getSystemService(NotificationManager::class.java)
                due.forEach { n -> nm.notify(n.key.hashCode(), build(ctx, n)) }
                markSent(ctx, due.map { it.key }, counted = due.count { it.channel != Reminders.REMINDERS })
            }
        }
        val next = all.filter { it.at > now + Reminders.GRACE }.minByOrNull { it.at }
        schedule(ctx, next?.at, exact = next?.channel == Reminders.REMINDERS)
    }

    private fun schedule(ctx: Context, at: Long?, exact: Boolean) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(ctx, 1, Intent(ctx, ReminderReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.cancel(pi)
        if (at == null) return
        // Your own reminders fire on the minute; nudges are inexact, kinder to the battery, within ~10 minutes.
        if (exact && am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setWindow(AlarmManager.RTC_WAKEUP, at, if (exact) 60_000L else 10 * 60 * 1000L, pi)
    }

    /** Off the main thread: it reads the data file. */
    fun runAsync(ctx: Context, data: AppData? = null) {
        val app = ctx.applicationContext
        Thread { runCatching { run(app, data) } }.start()
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Notifier.SNOOZE) {
            Notifier.snooze(context, intent.getStringExtra("task") ?: return, intent.getStringExtra("mode") ?: "hour", intent.getIntExtra("notif", 0))
            return
        }
        val pending = goAsync()
        Thread {
            try { runCatching { Notifier.run(context.applicationContext) } } finally { pending.finish() }
        }.start()
    }
}
