package com.example.appcall

import android.app.Application
import com.example.appcall.data.calling.CallingManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent

@HiltAndroidApp
class AppCallApplication : Application() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CallingManagerEntryPoint {
        fun callingManager(): CallingManager
    }

    override fun onCreate() {
        super.onCreate()
        // Eagerly initialize CallingManager so the TelephonyCallback is registered
        // from the moment the app process starts. This ensures ALL phone calls
        // are auto-detected and recorded, even if the user hasn't opened the call screen.
        EntryPointAccessors
            .fromApplication(this, CallingManagerEntryPoint::class.java)
            .callingManager()
    }
}
