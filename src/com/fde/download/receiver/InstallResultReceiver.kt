package com.fde.download.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.fde.download.model.Event
import com.fde.download.model.EventType
import com.fde.download.utils.ApkSilentInstaller
import org.greenrobot.eventbus.EventBus

class InstallResultReceiver : BroadcastReceiver() {
    private val TAG = "InstallResultReceiver"
    private val INSTALLED = false

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action
        Log.w(TAG, "Download----InstallResultReceiver  action " + action);
        if (intent != null) { // 安装的广播
            val status =
                intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            val appName = intent.getStringExtra("appName")
            Log.w(TAG, "InstallResultReceiver  appName " + appName + ",status " + status );
            if (status == PackageInstaller.STATUS_SUCCESS) {
                EventBus.getDefault().post(appName?.let { Event(EventType.INSTALL_COMPLETED, it) })
            } else {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.e(TAG, "Install failed: $msg")
            }
            ApkSilentInstaller.setIsInstalling(INSTALLED)
            if (context != null) {
                ApkSilentInstaller.startNextAppInstallation(context)
            }
        }
    }
}