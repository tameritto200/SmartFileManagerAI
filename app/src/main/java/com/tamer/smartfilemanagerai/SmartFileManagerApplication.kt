package com.tamer.smartfilemanagerai

import android.app.Application
import com.tamer.smartfilemanagerai.data.local.SmartFileDatabase

class SmartFileManagerApplication : Application() {
    val database: SmartFileDatabase by lazy {
        SmartFileDatabase.getInstance(this)
    }
}
