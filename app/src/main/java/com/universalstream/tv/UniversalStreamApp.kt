package com.universalstream.tv

import android.app.Application

class UniversalStreamApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
