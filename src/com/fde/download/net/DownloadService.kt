package com.fde.download.net

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import com.fde.download.model.Event
import com.fde.download.model.EventType
import com.fde.download.utils.ApkSilentInstaller
import okhttp3.Call
import okhttp3.Response
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.concurrent.ConcurrentHashMap

class DownloadService : Service() {
    private val TAG = "DownloadService"
    private val SOCKET_CLOSED_ERROR_1 = "Socket is closed"
    private val SOCKET_CLOSED_ERROR_2 = "Socket Closed"
    private val CANCEL_ERROR = "cancel"

    private val DEFAULT_BYTE_SIZE = 29262516L;//250L * 1024 * 1024
    private lateinit var downloadBinder: DownloadBinder
    private val callMap = ConcurrentHashMap<String, Call>()

    inner class DownloadBinder : Binder() {
        fun getService(): DownloadService = this@DownloadService
    }

    fun cancel(appName: String): Boolean {
        if (!callMap.containsKey(appName)) return false
        callMap[appName]?.cancel()
        callMap.remove(appName)
        return true
    }

    fun downloadApk(
        context: Context,
        url: String,
        appName: String,
        size: Long,
        md5Checksum: String
    ) {
        if (callMap.containsKey(appName)) return
        val call = HttpUtils.get(url, object : HttpUtils.HttpCallback {
            @RequiresApi(Build.VERSION_CODES.Q)
            override fun onResponse(response: Response) {
                if (response.body() == null) {
                    onFailure(IOException("$appName response == null .apk Download Failed"))
                    return
                }
                val contentLength = response.body()?.contentLength() as Long
                val totalSize =
                    if (contentLength > 0 ) contentLength else contentLength?.coerceAtLeast(DEFAULT_BYTE_SIZE) as Long
                Log.w(
                    TAG,
                    "downloadApk-----totalSize " + totalSize + " ,appName " + appName + ",contentLength " + contentLength
                )
                try {
                    saveApk(context, response, appName, totalSize, md5Checksum)
                } catch (e: Exception) {
                    onFailure(e)
                }
            }

            override fun onFailure(e: Exception) {
                downloadFailed(appName, e)
            }

            override fun onError(e: IOException) {
                downloadFailed(appName, e)
            }
        })
        EventBus.getDefault().post(Event(EventType.DOWNLOAD_IN_PROGRESS, appName))
        callMap[appName] = call
    }

    private fun downloadFailed(appName: String, exception: Exception) {
        val message = exception.message
        Log.w(TAG, "downloadFailed message " + message + ",appName " + appName);
        exception.printStackTrace()
        if (message != null && (message.contains(SOCKET_CLOSED_ERROR_1, ignoreCase = true) ||
                    message.contains(SOCKET_CLOSED_ERROR_2, ignoreCase = true) ||
                    message.contains(CANCEL_ERROR, ignoreCase = true))
        ) {
            return
        }
        EventBus.getDefault().post(Event(EventType.DOWNLOAD_FAILED, appName))
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @Throws(IOException::class, NoSuchAlgorithmException::class)
    private fun saveApk(
        context: Context,
        response: Response,
        appName: String,
        totalSize: Long,
        md5Checksum: String
    ) {
        val apkName = "$appName.apk"
//        val downloadDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val downloadDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadDir, apkName)
        if (file.exists()) {
            file.delete()
        }
        var downloadedSize = 0L

        try {
            val inputStream = response.body()?.byteStream()
            val outputStream = FileOutputStream(file)

            val buffer = ByteArray(1024 * 10)
            var bytesRead: Int = 0

            while (inputStream?.read(buffer)?.also { bytesRead = it } != -1) {
                outputStream?.write(buffer, 0, bytesRead)
                downloadedSize += bytesRead
                val progress = (downloadedSize * 100 / totalSize).toInt()
//                Log.w(TAG, "progress " + progress +",totalSize "+totalSize  +",bytesRead "+bytesRead+ ",apkName "+apkName);
                EventBus.getDefault().post(Event(EventType.DOWNLOAD_IN_PROGRESS, appName, progress))
            }
            inputStream?.close()
            outputStream?.flush()
            outputStream?.close()
        } catch (e: Exception) {
            file.delete()
            e.printStackTrace()
        }

        Log.w(TAG,"DOWNLOAD_COMPLETED........")
        EventBus.getDefault().post(Event(EventType.DOWNLOAD_COMPLETED, appName))
        if (md5Checksum.isNotEmpty() && md5Checksum != getFileMD5(file)) {
            EventBus.getDefault().post(Event(EventType.DOWNLOAD_FAILED, appName))
        } else {
            ApkSilentInstaller.enqueueInstall(context, appName, file.absolutePath)
        }
    }

    private fun getFileMD5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        val inputStream = FileInputStream(file)
        val buffer = ByteArray(1024 * 10)
        var bytesRead: Int

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        inputStream.close()

        val bytes = digest.digest()
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override fun onCreate() {
        super.onCreate()
        downloadBinder = DownloadBinder()
        callMap.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        callMap.keys.forEach { cancel(it) }
        callMap.clear()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return downloadBinder
    }
}