package com.fde.download

import android.app.Application
import android.util.Log
import androidx.appfunctions.service.AppFunctionConfiguration
import com.fde.download.appfunctions.CuratedAppsAppFunctions
import com.fde.download.appfunctions.CuratedAppsRepository

/**
 * Application entry point, also the [AppFunctionConfiguration.Provider] for the FDE curated
 * apps store. The AppFunction service(s) themselves are auto-registered from the
 * `androidx.appfunctions-service` library manifest.
 */
class DownloadApplication : Application(), AppFunctionConfiguration.Provider {

    private val TAG = "DownloadApplication"

    private lateinit var repository: CuratedAppsRepository

    override fun onCreate() {
        super.onCreate()
        repository = CuratedAppsRepository(applicationContext)
    }

    override val appFunctionConfiguration: AppFunctionConfiguration
        get() {
            if (!::repository.isInitialized) {
                repository = CuratedAppsRepository(applicationContext)
            }
            Log.d(TAG, "getAppFunctionConfiguration")
            return AppFunctionConfiguration.Builder()
                .addEnclosingClassFactory(CuratedAppsAppFunctions::class.java) {
                    CuratedAppsAppFunctions(repository)
                }
                .build()
        }
}
