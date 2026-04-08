package org.cadence.mobile

import android.app.Application
import org.cadence.mobile.di.appModule
import org.koin.core.context.startKoin

class CadenceApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        startKoin {
            modules(appModule)
        }
    }

    companion object {
        lateinit var instance: CadenceApplication
            private set
    }
}
