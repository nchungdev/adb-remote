package app.duinch.adbagent

import android.app.Activity
import android.app.Application
import android.os.Bundle
import app.duinch.adbagent.core.di.AppContainer

class AdbAgentApp : Application() {

    lateinit var container: AppContainer
        private set

    @Volatile
    var isAppInForeground: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var startedActivities = 0

            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                val foreground = startedActivities > 0
                if (isAppInForeground != foreground) {
                    isAppInForeground = foreground
                    container.connectionAlertNotifier.onForegroundChanged(foreground)
                }
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                val foreground = startedActivities > 0
                if (isAppInForeground != foreground) {
                    isAppInForeground = foreground
                    container.connectionAlertNotifier.onForegroundChanged(foreground)
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        container.connectionAlertNotifier.start()
    }
}
