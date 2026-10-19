package io.github.viggnah.accomplish

import android.app.Application
import io.github.viggnah.accomplish.ui.AppState

class App : Application() {
    /** One state for the whole process, so it survives rotation and activity recreation. */
    @Volatile var stateCreated = false; private set
    val state by lazy { stateCreated = true; AppState(this) }
}
