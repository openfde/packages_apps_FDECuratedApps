package com.fde.download.appfunctions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.fde.download.R
import com.fde.download.model.AppInfo
import com.fde.download.net.DownloadService
import com.fde.download.net.HttpUtils
import com.fde.download.utils.Utils
import com.google.gson.JsonParser
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Backing store for the curated apps store.
 *
 * The curated app list is loaded from the bundled `res/raw/apps.json` resource and refreshed from
 * the network (`HttpUtils.APP_INFO_URL`, the same list used by the download UI). The freshest
 * successfully loaded list is kept in memory and re-used for the process lifetime, so AppFunction
 * calls share a single snapshot.
 *
 * Downloads reuse the existing [DownloadService] pipeline (MD5 verification + silent install),
 * which is started and bound from [startDownload].
 */
class CuratedAppsRepository(private val context: Context) {

    private val TAG = "CuratedAppsRepository"

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var cachedApps: List<AppInfo>? = null

    /**
     * Returns the curated apps with their current install / update state, guaranteeing a
     * non-null, non-empty list (network first when available, otherwise the raw resource).
     */
    suspend fun getAvailableApps(): List<AppInfo> {
        val apps = ensureLoaded()
        apps.forEach { refreshInstallState(it) }
        return apps
    }

    /**
     * Resolves a curated app by [packageName] (preferred) or by its curated display name.
     * Matches the display name both in raw form (`FDE txt`) and in the underscore form used
     * by the download UI (`FDE_txt`).
     *
     * @return the matching [AppInfo], or null if no curated app matches.
     */
    suspend fun findApp(packageName: String?, appName: String?): AppInfo? {
        val apps = ensureLoaded()
        val pkg = packageName?.trim()
        if (!pkg.isNullOrBlank()) {
            apps.firstOrNull {
                pkg.equals(it.getPackageName(), ignoreCase = true)
            }?.let { return it }
        }
        val name = appName?.trim()
        if (!name.isNullOrBlank()) {
            return apps.firstOrNull { app ->
                val curated = app.getName()
                name.equals(curated, ignoreCase = true) ||
                    name.equals(curated?.replace("_", " "), ignoreCase = true)
            }
        }
        return null
    }

    /**
     * Submits a background download + silent install of [appInfo] through the existing
     * [DownloadService] pipeline and returns immediately; the actual download and install
     * continue asynchronously.
     *
     * Starts the service (so it stays alive for the whole download) and binds to it to obtain the
     * service instance needed for [DownloadService.downloadApk].
     *
     * @return true if a download was submitted, false if the app has no downloadable URL.
     */
    suspend fun startDownload(context: Context, appInfo: AppInfo): Boolean {
        val appName = appInfo.getName() ?: return false
        var url = appInfo.getPrimaryUrl()
        var size = appInfo.getPrimarySize()
        var md5 = appInfo.getPrimaryMd5Checksum()
        if (url.isNullOrBlank()) {
            url = appInfo.getBackupUrl()
            size = appInfo.getBackupSize()
            md5 = appInfo.getBackupMd5Checksum()
        }
        if (url.isNullOrBlank()) {
            Log.w(TAG, "startDownload: no URL found for ${appName}")
            return false
        }
        return submitViaDownloadService(context, appName, url, size, md5.orEmpty())
    }

    /// Loading -------------------------------------------------------------------------------

    private suspend fun ensureLoaded(): List<AppInfo> {
        cachedApps?.let { return it }
        var apps: List<AppInfo>? = null
        if (Utils.isNetworkAvailable(context)) {
            apps = fetchAppList()
        }
        if (apps.isNullOrEmpty()) {
            apps = loadFromRawResource()
        }
        // Cache whatever we ended up with so subsequent calls are cheap. Using the raw
        // resource as fallback guarantees we always have at least the bundled list.
        apps.let { cachedApps = it }
        return apps
    }

    private fun loadFromRawResource(): List<AppInfo> {
        return try {
            val inputStream = context.resources.openRawResource(R.raw.apps)
            val json = inputStream.bufferedReader().use { it.readText() }
            parseAppsJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "loadFromRawResource failed", e)
            emptyList()
        }
    }

    private suspend fun fetchAppList(): List<AppInfo>? = suspendCoroutine { cont ->
        val done = AtomicBoolean(false)
        val timeout = Runnable {
            if (done.compareAndSet(false, true)) {
                Log.w(TAG, "fetchAppList timed out")
                cont.resume(null)
            }
        }
        mainHandler.postDelayed(timeout, NETWORK_TIMEOUT_MS)
        try {
            HttpUtils.get(HttpUtils.APP_INFO_URL, object : HttpUtils.HttpCallback {
                override fun onResponse(response: okhttp3.Response) {
                    mainHandler.removeCallbacks(timeout)
                    if (!done.compareAndSet(false, true)) return
                    try {
                        val json = response.body()?.string()
                        if (json == null) {
                            cont.resume(null)
                        } else {
                            cont.resume(parseAppsJson(json))
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "fetchAppList parse failed", e)
                        cont.resume(null)
                    }
                }

                override fun onFailure(e: Exception) {
                    mainHandler.removeCallbacks(timeout)
                    if (!done.compareAndSet(false, true)) return
                    Log.w(TAG, "fetchAppList failed: ${e.message}")
                    cont.resume(null)
                }

                override fun onError(e: IOException) {
                    mainHandler.removeCallbacks(timeout)
                    if (!done.compareAndSet(false, true)) return
                    Log.w(TAG, "fetchAppList error: ${e.message}")
                    cont.resume(null)
                }
            })
        } catch (e: Exception) {
            mainHandler.removeCallbacks(timeout)
            if (done.compareAndSet(false, true)) {
                Log.w(TAG, "fetchAppList threw", e)
                cont.resume(null)
            }
        }
    }

    /// Parsing -------------------------------------------------------------------------------

    private fun parseAppsJson(json: String): List<AppInfo> {
        val apps = mutableListOf<AppInfo>()
        try {
            val jsonArray = JsonParser.parseString(json).asJsonArray
            for (i in 0 until jsonArray.size()) {
                val obj = jsonArray[i]?.asJsonObject ?: continue
                val name = obj.get("name")?.asString?.replace(" ", "_") ?: continue
                val packageName = obj.get("packageName")?.asString?.takeIf { it.isNotBlank() }
                    ?: continue
                val rawApp = AppInfo(
                    name,
                    packageName,
                    obj.get("version")?.asString,
                    parseBoolean(obj.get("isAvailable")?.asString),
                    false,
                    false,
                    obj.get("iconString")?.asString,
                    obj.get("primaryUrl")?.asString,
                    obj.get("primarySize")?.asString?.toLongOrNull() ?: 0L,
                    obj.get("primaryMd5Checksum")?.asString.orEmpty(),
                    obj.get("backupUrl")?.asString,
                    obj.get("backupSize")?.asString?.toLongOrNull() ?: 0L,
                    obj.get("backupMd5Checksum")?.asString.orEmpty()
                )
                refreshInstallState(rawApp)
                apps.add(rawApp)
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseAppsJson failed", e)
        }
        return apps
    }

    private fun parseBoolean(value: String?): Boolean {
        return try {
            value?.trim()?.equals("true", ignoreCase = true) == true
        } catch (e: Exception) {
            false
        }
    }

    /** Recomputes the install / update state of [appInfo] against the device. */
    private fun refreshInstallState(appInfo: AppInfo) {
        val packageName = appInfo.getPackageName() ?: return
        if (!Utils.isAppInstalled(context, packageName)) {
            appInfo.setInstall(false)
            appInfo.setUpdate(false)
            return
        }
        appInfo.setInstall(true)
        val currentVersion = Utils.getAppVersionName(context, packageName)
        val curatedVersion = appInfo.getVersion()
        val isUpdate = currentVersion != null && curatedVersion != null &&
            Utils.compareVersion(curatedVersion, currentVersion) > 0
        appInfo.setUpdate(isUpdate)
    }

    /// Download submission -------------------------------------------------------------------

    private suspend fun submitViaDownloadService(
        context: Context,
        appName: String,
        url: String,
        size: Long,
        md5: String
    ): Boolean = suspendCoroutine { cont ->
        val done = AtomicBoolean(false)
        val timeout = Runnable {
            if (done.compareAndSet(false, true)) {
                Log.w(TAG, "submitViaDownloadService: binding timed out")
                cont.resume(false)
            }
        }
        mainHandler.postDelayed(timeout, BIND_TIMEOUT_MS)

        mainHandler.post {
            val intent = Intent(context, DownloadService::class.java)
            // Keep the service alive for the whole background download, independent of this
            // binding. The service's onDestroy() cancels in-flight downloads.
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "submitViaDownloadService: startService failed", e)
            }

            var downloaded = false
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    mainHandler.removeCallbacks(timeout)
                    if (!done.compareAndSet(false, true)) return
                    val bound = binder is DownloadService.DownloadBinder
                    try {
                        if (bound) {
                            val service = (binder as DownloadService.DownloadBinder).getService()
                            service.downloadApk(context, url, appName, size, md5)
                            downloaded = true
                        } else {
                            Log.w(TAG, "submitViaDownloadService: unexpected binder class")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "submitViaDownloadService: downloadApk failed", e)
                    } finally {
                        try {
                            context.unbindService(this)
                        } catch (ignored: Exception) {
                        }
                    }
                    cont.resume(downloaded)
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    // The download continues regardless (OkHttp callbacks are fire-and-forget).
                }
            }

            val bound = context.bindService(
                intent,
                connection,
                Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT
            )
            if (!bound) {
                mainHandler.removeCallbacks(timeout)
                if (done.compareAndSet(false, true)) {
                    Log.w(TAG, "submitViaDownloadService: bindService returned false")
                    cont.resume(false)
                }
            }
        }
    }

    private companion object {
        const val NETWORK_TIMEOUT_MS = 15_000L
        const val BIND_TIMEOUT_MS = 5_000L
    }
}
