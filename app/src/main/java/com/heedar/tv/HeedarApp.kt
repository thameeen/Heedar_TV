package com.heedar.tv

import android.app.Application
import com.heedar.tv.di.AppContainer

class HeedarApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
