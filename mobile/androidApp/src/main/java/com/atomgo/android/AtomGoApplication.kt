package com.atomgo.android

import android.app.Application
import com.atomgo.android.di.AppContainer
import com.atomgo.android.di.DefaultAppContainer

class AtomGoApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = DefaultAppContainer(application = this)
    }
}
