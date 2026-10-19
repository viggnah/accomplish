package io.github.viggnah.accomplish.data

import android.content.Context
import org.json.JSONObject
import java.io.File

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** How often a side quest involves other people. */
enum class SocialRate(val label: String, val chance: Double) {
    NEVER("Never", 0.0), SOMETIMES("Sometimes", 0.33), OFTEN("Often", 0.5)
}

data class Settings(
    val slots: Int = 3,
    val frost: Boolean = true,
    val frostStart: Int = 3,
    val frostFull: Int = 14,
    val heat: Boolean = true,
    val heatWindow: Int = 7,
    val fadeStart: Int = 5,
    val askAfter: Int = 14,
    val undoSecs: Int = 10,
    val captureDefault: Kind = Kind.SMALL,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val nameBig: String = "",
    val nameSmall: String = "",
    val nameWaiting: String = "",
    val nameHistory: String = "",
    val capsules: Boolean = true,
    val socialRate: SocialRate = SocialRate.SOMETIMES,
    val people: List<String> = emptyList(),
    val stepDropPercent: Int = 50,
    val smallDropPercent: Int = 10,
    val backups: Int = 7,
    // notifications
    val notify: Boolean = true,
    val remindTwoDays: Boolean = true,
    val remindOneDay: Boolean = true,
    val remindSmall: Boolean = true,
    val frostNudge: Boolean = true,
    val weeklyReview: Boolean = true,
    /** 1 = Monday … 7 = Sunday. */
    val reviewDay: Int = 7,
    val randomPrompt: Boolean = true,
    val remindHour: Int = 9,
    val quietStart: Int = 22,
    val quietEnd: Int = 8,
    val maxPerDay: Int = 2,
    /** Folder that holds data.json, e.g. /storage/1A2B-3C4D/Accomplish. Null until set up. */
    val dataDir: String? = null,
) {
    val big get() = nameBig.trim().ifEmpty { "IMP" }
    val small get() = nameSmall.trim().ifEmpty { "Small stuff" }
    val waiting get() = nameWaiting.trim().ifEmpty { "Waiting room" }
    val history get() = nameHistory.trim().ifEmpty { "History" }

    companion object {
        /** Range and step for every number setting, used by the settings screen and when loading. */
        val bounds = mapOf(
            "slots" to Triple(1, 5, 1), "frostStart" to Triple(1, 30, 1), "frostFull" to Triple(2, 60, 1),
            "heatWindow" to Triple(1, 30, 1), "fadeStart" to Triple(1, 30, 1), "askAfter" to Triple(2, 60, 1),
            "undoSecs" to Triple(0, 60, 5), "stepDropPercent" to Triple(0, 100, 10), "smallDropPercent" to Triple(0, 50, 5),
            "backups" to Triple(1, 30, 1), "reviewDay" to Triple(1, 7, 1), "remindHour" to Triple(5, 21, 1),
            "quietStart" to Triple(18, 23, 1), "quietEnd" to Triple(5, 11, 1), "maxPerDay" to Triple(1, 6, 1),
        )
    }
}

/** Settings live in app-private storage (tiny, and readable even when the SD card is out). */
class SettingsStore(context: Context) {
    private val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): Settings {
        val d = Settings()
        return Settings(
            slots = p.getInt("slots", d.slots),
            frost = p.getBoolean("frost", d.frost),
            frostStart = p.getInt("frostStart", d.frostStart),
            frostFull = p.getInt("frostFull", d.frostFull),
            heat = p.getBoolean("heat", d.heat),
            heatWindow = p.getInt("heatWindow", d.heatWindow),
            fadeStart = p.getInt("fadeStart", d.fadeStart),
            askAfter = p.getInt("askAfter", d.askAfter),
            undoSecs = p.getInt("undoSecs", d.undoSecs),
            captureDefault = enumOr(p.getString("captureDefault", null), d.captureDefault),
            theme = enumOr(p.getString("theme", null), d.theme),
            nameBig = p.getString("nameBig", d.nameBig)!!,
            nameSmall = p.getString("nameSmall", d.nameSmall)!!,
            nameWaiting = p.getString("nameWaiting", d.nameWaiting)!!,
            nameHistory = p.getString("nameHistory", d.nameHistory)!!,
            capsules = p.getBoolean("capsules", d.capsules),
            socialRate = enumOr(p.getString("socialRate", null), d.socialRate),
            people = p.getString("people", "")!!.split('\n').map { it.trim() }.filter { it.isNotEmpty() },
            stepDropPercent = p.getInt("stepDropPercent", d.stepDropPercent),
            smallDropPercent = p.getInt("smallDropPercent", d.smallDropPercent),
            backups = p.getInt("backups", d.backups),
            notify = p.getBoolean("notify", d.notify),
            remindTwoDays = p.getBoolean("remindTwoDays", d.remindTwoDays),
            remindOneDay = p.getBoolean("remindOneDay", d.remindOneDay),
            remindSmall = p.getBoolean("remindSmall", d.remindSmall),
            frostNudge = p.getBoolean("frostNudge", d.frostNudge),
            weeklyReview = p.getBoolean("weeklyReview", d.weeklyReview),
            reviewDay = p.getInt("reviewDay", d.reviewDay),
            randomPrompt = p.getBoolean("randomPrompt", d.randomPrompt),
            remindHour = p.getInt("remindHour", d.remindHour),
            quietStart = p.getInt("quietStart", d.quietStart),
            quietEnd = p.getInt("quietEnd", d.quietEnd),
            maxPerDay = p.getInt("maxPerDay", d.maxPerDay),
            dataDir = p.getString("dataDir", null),
        )
    }

    fun save(s: Settings) {
        p.edit()
            .putInt("slots", s.slots).putBoolean("frost", s.frost).putInt("frostStart", s.frostStart).putInt("frostFull", s.frostFull)
            .putBoolean("heat", s.heat).putInt("heatWindow", s.heatWindow).putInt("fadeStart", s.fadeStart).putInt("askAfter", s.askAfter)
            .putInt("undoSecs", s.undoSecs).putString("captureDefault", s.captureDefault.name).putString("theme", s.theme.name)
            .putString("nameBig", s.nameBig).putString("nameSmall", s.nameSmall)
            .putString("nameWaiting", s.nameWaiting).putString("nameHistory", s.nameHistory)
            .putBoolean("capsules", s.capsules).putString("socialRate", s.socialRate.name)
            .putString("people", s.people.joinToString("\n")).putInt("stepDropPercent", s.stepDropPercent)
            .putInt("smallDropPercent", s.smallDropPercent).putInt("backups", s.backups).putString("dataDir", s.dataDir)
            .putBoolean("notify", s.notify).putBoolean("remindTwoDays", s.remindTwoDays).putBoolean("remindOneDay", s.remindOneDay)
            .putBoolean("remindSmall", s.remindSmall).putBoolean("frostNudge", s.frostNudge).putBoolean("weeklyReview", s.weeklyReview)
            .putInt("reviewDay", s.reviewDay).putBoolean("randomPrompt", s.randomPrompt).putInt("remindHour", s.remindHour)
            .putInt("quietStart", s.quietStart).putInt("quietEnd", s.quietEnd).putInt("maxPerDay", s.maxPerDay)
            .apply()
    }

    /** True once this install has saved settings of its own. */
    fun hasAny() = p.contains("dataDir")

    /**
     * Keeps a copy of the settings next to your data, so a reinstall or a new install can pick them up.
     * Everything but the folder itself, which depends on where the copy is found.
     */
    fun exportTo(dir: File) {
        val o = JSONObject()
        p.all.forEach { (k, v) -> if (k != "dataDir" && v != null) o.put(k, v) }
        val text = o.toString(1)
        writer.execute {
            runCatching {
                if (!dir.isDirectory) return@runCatching
                val tmp = File(dir, "$FILE.tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(File(dir, FILE))) tmp.delete()
            }
        }
    }

    /** Takes settings from a copy in [dir], if there is one. */
    fun importFrom(dir: File): Boolean = runCatching {
        val f = File(dir, FILE)
        if (!f.isFile) return false
        val o = JSONObject(f.readText())
        val e = p.edit()
        o.keys().forEach { k ->
            when (val v = o.get(k)) {
                is Boolean -> e.putBoolean(k, v)
                is Int -> e.putInt(k, v)
                is Long -> e.putInt(k, v.toInt())
                is String -> e.putString(k, v)
            }
        }
        e.commit()
    }.getOrDefault(false)

    companion object {
        const val FILE = "settings.json"
        private val writer = java.util.concurrent.Executors.newSingleThreadExecutor()
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
        name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: fallback
}
