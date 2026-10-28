package com.souravgoswami.framefinder

import android.app.Application
import com.souravgoswami.framefinder.ai.MobileClipEmbedder
import com.souravgoswami.framefinder.ai.ModelManager
import com.souravgoswami.framefinder.data.PhotoRepository
import com.souravgoswami.framefinder.data.FrameFinderDatabase
import com.souravgoswami.framefinder.data.FrameFinderPreferences
import com.souravgoswami.framefinder.data.AnalysisBackupManager

class FrameFinderApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    val database = FrameFinderDatabase.create(application)
    val preferences = FrameFinderPreferences(application)
    val modelManager = ModelManager(application, preferences)
    val embedder = MobileClipEmbedder(modelManager)
    val repository = PhotoRepository(database.photoDao(), embedder, modelManager)
    val analysisBackupManager = AnalysisBackupManager(application, database)

    init {
        modelManager.deleteObsoleteDownloads()
    }
}

fun android.content.Context.appContainer(): AppContainer =
    (applicationContext as FrameFinderApplication).container
