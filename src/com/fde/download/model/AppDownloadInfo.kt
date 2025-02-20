package com.fde.download.model

import android.graphics.Bitmap

data class AppDownloadInfo(
    var appInfo: AppInfo,
    var isSelected: Boolean = true,
    var bitmap: Bitmap? = null,
    var progress: Int = -1,
    var eventType: EventType = EventType.DOWNLOAD_PENDING
) {
    constructor(appInfo: AppInfo, isSelected: Boolean, bitmap: Bitmap) : this(
        appInfo,
        isSelected,
        bitmap,
        0
    )

    override fun toString(): String {
        return "AppDownloadInfo{" +
                "appInfo=$appInfo" +
                ", isSelected=$isSelected" +
                ", bitmap=$bitmap" +
                ", progress=$progress" +
                ", eventType=$eventType" +
                '}'
    }
}
