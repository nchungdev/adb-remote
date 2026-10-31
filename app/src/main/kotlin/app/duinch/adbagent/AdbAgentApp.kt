package app.duinch.adbagent

import android.app.Application
import app.duinch.adbagent.core.di.AppContainer

class AdbAgentApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
