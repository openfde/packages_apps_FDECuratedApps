package com.fde.download.utils

import com.fde.download.model.AppDownloadInfo
import com.fde.download.model.EventType
import com.fde.download.model.RequestStatus

class Singleton private constructor() {
    private val TAG = "Singleton"
    private var requestStatus: RequestStatus = RequestStatus.NOT_REQUESTED
    private var appDownloadInfoList: MutableList<AppDownloadInfo> = mutableListOf()

    companion object {
        val instance: Singleton = Singleton()
    }

    fun setRequestStatus(requestStatus: RequestStatus) {
        this.requestStatus = requestStatus
    }

    fun hasNetworkRequestBeenInitiated(): Boolean {
        return requestStatus != RequestStatus.NOT_REQUESTED
    }

    fun hasNetworkRequestSucceeded(): Boolean {
        return requestStatus == RequestStatus.REQUEST_SUCCESS
    }

    fun getAppDownloadInfo(position: Int): AppDownloadInfo? {
        return if (appDownloadInfoList.isEmpty() || position < 0 || position >= appDownloadInfoList.size) {
            null
        } else {
            appDownloadInfoList[position]
        }
    }

    fun getAppDownloadInfo(appName: String): AppDownloadInfo? {
        if (appName.isNullOrBlank()) return null
        return appDownloadInfoList.find { it.appInfo.name == appName }
    }

    fun getAppDownloadInfoList(): List<AppDownloadInfo> {
        return appDownloadInfoList
    }

    fun setAppDownloadInfoList(appDownloadInfoList: List<AppDownloadInfo>) {
        this.appDownloadInfoList = appDownloadInfoList.toMutableList()
    }

    fun updateProgress(appName: String, progress: Int): Int {
        val index = appDownloadInfoList.indexOfFirst { it.appInfo.name == appName }
        if (index != -1) {
            appDownloadInfoList[index].progress = progress
        }
        return index
    }

    fun isNothingSelected(): Boolean {
        return appDownloadInfoList.none { it.isSelected }
    }

    fun isNothingDownload(): Boolean {
        return appDownloadInfoList.all {
            it.eventType == EventType.DOWNLOAD_PENDING ||
                    it.eventType == EventType.INSTALL_COMPLETED ||
                    it.eventType == EventType.DOWNLOAD_FAILED ||
                    it.eventType == EventType.INSTALL_FAILED
        }
    }
}
