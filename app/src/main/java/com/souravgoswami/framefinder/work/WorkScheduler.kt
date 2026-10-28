package com.souravgoswami.framefinder.work

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.souravgoswami.framefinder.ai.SearchModel
import com.souravgoswami.framefinder.appContainer

object WorkScheduler {
    fun index(
        context: Context,
        analyzeAll: Boolean = false,
        forceRebuild: Boolean = false,
        model: SearchModel = context.appContainer().modelManager.selectedModel
    ) {
        val request = OneTimeWorkRequestBuilder<PhotoIndexWorker>()
            .setInputData(
                Data.Builder()
                    .putBoolean(WorkContract.KEY_ANALYZE_ALL, analyzeAll)
                    .putBoolean(WorkContract.KEY_FORCE_REBUILD, forceRebuild)
                    .putString(WorkContract.KEY_MODEL_ID, model.id)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WorkContract.INDEX_WORK,
            if (analyzeAll || forceRebuild) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WorkContract.INDEX_WORK)
    }
}
