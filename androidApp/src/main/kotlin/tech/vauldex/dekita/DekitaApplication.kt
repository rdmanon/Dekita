package tech.vauldex.dekita

import android.app.Application
import kotlinx.coroutines.MainScope
import tech.vauldex.dekita.di.AppContainer
import tech.vauldex.dekita.notifications.OrderNotifier

/** Starts the order notifications for the whole process, so they post while no screen is open. */
class DekitaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        OrderNotifier(this).start(AppContainer.instance.orderNotifications, MainScope())
    }
}
