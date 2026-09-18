package com.example.myapplication

import android.app.Application
import com.example.myapplication.data.SettingsRepository
import com.example.myapplication.notify.Notifications

class ClipboardApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppGraph.settings = SettingsRepository(applicationContext)
        Notifications.createChannels(this)
    }
}

/**
 * 进程内共享的依赖。
 * Service 与 UI 运行在同一进程，因此可以直接用 StateFlow 通信，无需 IPC。
 */
object AppGraph {
    lateinit var settings: SettingsRepository
}
