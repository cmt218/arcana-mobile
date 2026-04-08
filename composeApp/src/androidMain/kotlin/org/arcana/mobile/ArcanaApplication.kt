package org.arcana.mobile

import android.app.Application
import org.arcana.mobile.di.appModule
import org.koin.core.context.startKoin

class ArcanaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        startKoin {
            modules(appModule)
        }
    }

    companion object {
        lateinit var instance: ArcanaApplication
            private set
    }
}
