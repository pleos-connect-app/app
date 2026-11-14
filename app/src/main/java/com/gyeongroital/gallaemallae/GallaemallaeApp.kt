package com.gyeongroital.gallaemallae

import android.app.Application

class GallaemallaeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
