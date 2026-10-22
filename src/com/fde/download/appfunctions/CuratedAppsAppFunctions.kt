package com.fde.download.appfunctions

import android.util.Log
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.service.AppFunction
import com.fde.download.utils.Utils

/**
 * High level [AppFunction]s exposing the FDE curated apps store to App Function clients
 * (system agent / launcher / settings…).
 *
 * Functions exposed:
 *  - [getAvailableApps]: returns the curated apps together with their package name, curated
 *    version and current install / update state, so a caller can discover what is installable.
 *  - [downloadAndInstallApp]: submits a background download + silent install for a curated app
 *    (identified by `packageName` or curated `appName`) and returns immediately.
 *
 * @property repository Backing store for the curated apps and download orchestration.
 */
class CuratedAppsAppFunctions(
    private val repository: CuratedAppsRepository
) {
    private val TAG = "CuratedAppsAppFunctions"

    /**
     * Returns the current list of curated apps with their install / update state.
     *
     * @param appFunctionContext App function context.
     * @return [GetAvailableAppsResponse] with the curated apps list.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getAvailableApps(appFunctionContext: AppFunctionContext): GetAvailableAppsResponse {
        val apps = repository.getAvailableApps()
        Log.d(TAG, "getAvailableApps: ${apps.size} curated apps")
        return GetAvailableAppsResponse(apps = apps.map { it.toAvailableApp() })
    }

    /**
     * Submits a background download + silent install of a curated app and returns immediately.
     *
     * The app is resolved against the curated store (the same list used by the download UI),
     * either by its [packageName] (preferred, e.g. `com.tencent.mm`) or by its curated
     * display [appName] (e.g. `微信`). If the target is already installed at the latest curated
     * version the call is a no-op (`status` = `already_installed`); otherwise the download and
     * install is queued in the background and `status` = `submitted`.
     *
     * @param appFunctionContext App function context.
     * @param packageName Package name of the curated app to download & install.
     * @param appName Curated display name of the app (alternative to packageName).
     * @return [DownloadAndInstallResponse] describing whether the task was submitted.
     * @throws [AppFunctionInvalidArgumentException] if neither identifier is provided or no
     *   matching curated app is found.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun downloadAndInstallApp(
        appFunctionContext: AppFunctionContext,
        packageName: String?,
        appName: String?,
    ): DownloadAndInstallResponse {
        if (packageName.isNullOrBlank() && appName.isNullOrBlank()) {
            throw AppFunctionInvalidArgumentException(
                "At least one of packageName or appName must be provided"
            )
        }
        val appInfo = repository.findApp(packageName, appName)
            ?: throw AppFunctionInvalidArgumentException(
                "No curated app found for packageName=$packageName, appName=$appName"
            )
        Log.d(TAG, "downloadAndInstallApp: ${appInfo.getName()} (${appInfo.getPackageName()})")

        val appNameValue = appInfo.getName()
        val packageNameValue = appInfo.getPackageName()
        val versionValue = appInfo.getVersion()

        // Already installed and up to date -> nothing to do.
        if (!packageNameValue.isNullOrBlank() && Utils.isAppInstalled(appFunctionContext.context, packageNameValue)) {
            val current = Utils.getAppVersionName(appFunctionContext.context, packageNameValue)
            if (current != null && !appInfo.isUpdate()) {
                return DownloadAndInstallResponse(
                    submitted = false,
                    appName = appNameValue,
                    packageName = packageNameValue,
                    version = versionValue,
                    status = DownloadAndInstallResponse.STATUS_ALREADY_INSTALLED,
                    message = "$appNameValue is already installed (version $current) and up to date",
                )
            }
        }

        val submitted = repository.startDownload(appFunctionContext.context, appInfo)
        if (submitted) {
            return DownloadAndInstallResponse(
                submitted = true,
                appName = appNameValue,
                packageName = packageNameValue,
                version = versionValue,
                status = DownloadAndInstallResponse.STATUS_SUBMITTED,
                message = "Download and install submitted for $appNameValue",
            )
        }
        throw AppFunctionInvalidArgumentException(
            "No downloadable APK found for $appNameValue"
        )
    }

    /// Decorated responses to the AppFunction clients; the KDoc below is used as the raw
    /// documentation ingested by the clients / agents.

    /**
     * Response from [getAvailableApps].
     *
     * @property apps Curated apps with their install state.
     * @property isTruncated True if the app list was truncated.
     */
    @AppFunctionSerializable(isDescribedByKDoc = true)
    data class GetAvailableAppsResponse(
        val apps: List<AvailableApp>,
        val isTruncated: Boolean = false,
    )

    /**
     * A single curated app entry.
     *
     * @property name Curated display name (spaces replaced with underscores).
     * @property packageName Android package name.
     * @property version Curated version string.
     * @property installed True if the package is already installed.
     * @property updateAvailable True if an installed older version can be updated.
     */
    @AppFunctionSerializable(isDescribedByKDoc = true)
    data class AvailableApp(
        val name: String?,
        val packageName: String?,
        val version: String?,
        val installed: Boolean,
        val updateAvailable: Boolean,
    )

    /**
     * Response from [downloadAndInstallApp].
     *
     * @property submitted True if the background download + install task was submitted.
     * @property appName Curated display name of the app.
     * @property packageName Package name of the app.
     * @property version Curated version that is being installed.
     * @property status Status code: `submitted` when the task was queued, or
     *   `already_installed` when the app is already up to date.
     * @property message Human-readable result message.
     */
    @AppFunctionSerializable(isDescribedByKDoc = true)
    data class DownloadAndInstallResponse(
        val submitted: Boolean,
        val appName: String?,
        val packageName: String?,
        val version: String?,
        val status: String?,
        val message: String?,
    ) {
        companion object {
            const val STATUS_SUBMITTED = "submitted"
            const val STATUS_ALREADY_INSTALLED = "already_installed"
        }
    }

    private fun com.fde.download.model.AppInfo.toAvailableApp(): AvailableApp {
        return AvailableApp(
            name = getName(),
            packageName = getPackageName(),
            version = getVersion(),
            installed = isInstall(),
            updateAvailable = isUpdate(),
        )
    }
}
