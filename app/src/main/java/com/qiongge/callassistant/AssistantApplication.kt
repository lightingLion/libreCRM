package com.qiongge.callassistant

import android.app.Application
import com.qiongge.callassistant.data.AppContainer

class AssistantApplication : Application() {
    val appContainer: AppContainer by lazy { AppContainer(this) }
}
