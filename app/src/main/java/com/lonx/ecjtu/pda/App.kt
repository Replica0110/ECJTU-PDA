package com.lonx.ecjtu.pda

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import com.lonx.ecjtu.pda.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import timber.log.Timber

class App: Application() {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate() {
        super.onCreate()
            Timber.plant(Timber.DebugTree())


        startKoin {
            androidLogger(Level.DEBUG)
            // 声明 Android Context
            androidContext(this@App)
            modules(appModule)
        }
    }

}