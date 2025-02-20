package com.fde.download.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.FileUtils
import android.util.Log
import androidx.annotation.RequiresApi
import com.fde.download.model.Event
import com.fde.download.model.EventType
import com.fde.download.model.InstallRequest
import com.fde.download.receiver.InstallResultReceiver
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentLinkedQueue

class ApkSilentInstaller {
    companion object {
        private val TAG = "ApkSilentInstaller"
        private val APP_NAME = "appName"
        private var isInstalling = false
        private val installQueue = ConcurrentLinkedQueue<InstallRequest>()
        private var mSessionId = -1

        /**
         * Enqueue an installation request.
         */
        @RequiresApi(Build.VERSION_CODES.Q)
        @Synchronized
        fun enqueueInstall(context: Context, appName: String, apkFilePath: String) {
            installQueue.add(InstallRequest(appName, apkFilePath))
            startNextAppInstallation(context)
        }

        /**
         * Start the next installation in the queue.
         */
        @RequiresApi(Build.VERSION_CODES.Q)
        @Synchronized
        fun startNextAppInstallation(context: Context) {
            if (installQueue.isEmpty() || isInstalling) return
            isInstalling = true
            val installRequest = installQueue.poll()
            installApk(context, installRequest)
        }

        /**
         * Set the installation status.
         */
        fun setIsInstalling(isInstalling: Boolean) {
            this.isInstalling = isInstalling
        }

        /**
         * Install the APK file.
         */
        @RequiresApi(Build.VERSION_CODES.Q)
        fun installApk(context: Context, installRequest: InstallRequest?): Boolean? {
            installRequest ?: return null
            Log.w(TAG,"installApk........")
            val appName = installRequest.appName
            val apkFilePath = installRequest.apkFilePath
            EventBus.getDefault().post(Event(EventType.INSTALL_STARTED, appName))
            val apkFile = File(apkFilePath)
            if (!apkFile.exists()) {
                return null
            }

            val packageInstaller = context.packageManager.packageInstaller
            val sessionParams =
                PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            sessionParams.setSize(apkFile.length())

//            packageInstaller.registerSessionCallback(object : PackageInstaller.SessionCallback() {
//                override fun onCreated(i: Int) {
//                }
//
//                override fun onBadgingChanged(i: Int) {
//                }
//
//                override fun onActiveChanged(i: Int, b: Boolean) {
//                }
//
//                override fun onProgressChanged(sessionId: Int, progress: Float) {
//                    Log.w(TAG, "Install progress: ${progress * 100}%")
//                }
//
//                override fun onFinished(sessionId: Int, success: Boolean) {
//                    if (success) {
//                        Log.w(TAG, "Installation finished successfully")
//                    } else {
//                        Log.w(TAG, "Installation failed")
//                    }
//                }
//            })

            try {
                mSessionId = packageInstaller.createSession(sessionParams)
            } catch (e: IOException) {
                e.printStackTrace()
            }

            if (mSessionId != -1) {
                Log.w(TAG, "mSessionId != -1")
                val copySuccess = transferApkFile(context, apkFilePath)
                if (copySuccess) {
                    execInstallApp(appName, context)
                }
            }
            return null
        }

        /**
         * Transfer the APK file via file streams.
         */
        @RequiresApi(Build.VERSION_CODES.Q)
        private fun transferApkFile(context: Context, apkFilePath: String): Boolean {
            val apkFile = File(apkFilePath)
            val packageInstaller = context.packageManager.packageInstaller
            var session: PackageInstaller.Session? = null
            var inputStream: InputStream? = null
            var outputStream: OutputStream? = null
            var success = false

            try {
                session = packageInstaller.openSession(mSessionId)
                outputStream = session.openWrite("base.apk", 0, apkFile.length())
                inputStream = FileInputStream(apkFile)
                FileUtils.copy(inputStream, outputStream)
                session.fsync(outputStream)
                success = true
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                session?.close()
                inputStream?.close()
                outputStream?.close()
            }
            return success
        }

        /**
         * Execute the installation and notify the result.
         */
        private fun execInstallApp(appName: String, context: Context) {
            val packageInstaller = context.packageManager.packageInstaller
            var session: PackageInstaller.Session? = null
            try {
                session = packageInstaller.openSession(mSessionId)
                val intent = Intent(context, InstallResultReceiver::class.java).apply {
                    action = "com.android.oobe.ACTION_INSTALL_RESULT"
                    putExtra("SessionId", mSessionId)
                    putExtra(APP_NAME, appName)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    1,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT 
                )
                session.commit(pendingIntent.intentSender)
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                session?.close()
            }
        }
    }
}