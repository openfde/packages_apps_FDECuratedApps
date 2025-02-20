package com.fde.download

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fde.download.adapter.AppAdapter
import com.fde.download.adapter.AppDownloadAdapter
import com.fde.download.adapter.AppNoDownloadAdapter
import com.fde.download.model.AppDownloadInfo
import com.fde.download.model.AppInfo
import com.fde.download.model.Event
import com.fde.download.model.EventType
import com.fde.download.model.RequestStatus
import com.fde.download.net.DownloadService
import com.fde.download.net.HttpUtils
import com.fde.download.utils.Singleton
import com.fde.download.utils.Utils
import com.fde.download.utils.Utils.Companion.isNetworkAvailable
import com.google.gson.JsonParser
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File
import java.io.IOException

class MainActivity : ComponentActivity() {
    companion object {
        const val SUCCESS = 1
        const val FAILURE = 2
        const val ERROR = 3

        const val IS_SELECTED = true
        const val IS_INITIATED = true
        const val IS_NOT_SELECTED = false

        var DOWNLOAD_STATUS = 0
    }

    private val TAG = "DownloadAppActivity"

    private val singleton = Singleton.instance
    private var downloadService: DownloadService? = null
    private lateinit var intentService: Intent

    private lateinit var context: Context

    private lateinit var nextBtn: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var downloadingRecyclerView: RecyclerView
    private lateinit var noDownloadingRecyclerView: RecyclerView

    private val appAdapter = AppAdapter()
    private val appDownloadAdapter = AppDownloadAdapter()
    private val appNoDownloadAdapter = AppNoDownloadAdapter()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Log.w(TAG, "onServiceConnected.......... ")
            if (service is DownloadService.DownloadBinder) {
                downloadService = service.getService()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.w(TAG, "onServiceDisconnected.......... ")
        }
    }

    private val handler = Handler(Looper.getMainLooper()) {
        when (it.what) {
            SUCCESS -> {
                appAdapter.setAppDownloadInfoList(singleton.getAppDownloadInfoList())
                installApp()
                // recyclerView.visibility = View.VISIBLE
            }

            FAILURE, ERROR -> {
                // recyclerView.visibility = View.INVISIBLE
            }
        }
        true
    }

    private val mInstallResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            val packageName = intent.data?.schemeSpecificPart
            val appName = packageName?.let { Utils.getAppName(context, it) }
            Log.w(
                TAG,
                "InstallResultReceiver  action $action, packageName $packageName, appName $appName"
            )
            EventBus.getDefault().post(appName?.let { Event(EventType.INSTALL_COMPLETED, it) })

            val appNameExtra = intent.getStringExtra("appName")
            Log.w(TAG, "InstallResultReceiver  appName $appNameExtra")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.download_app_page)
        context = this
        initView()
        EventBus.getDefault().register(this)
        intentService = Intent(this, DownloadService::class.java)
        startService(intentService)
        bindService(intentService, connection, Context.BIND_AUTO_CREATE)

        initData()

        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_INSTALL)
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }

    }

    private fun initView() {
        nextBtn = findViewById<Button>(R.id.nextBtn)  ?: throw IllegalArgumentException("Button not found")
        recyclerView = findViewById<RecyclerView>(R.id.application_recycler_view)  ?: throw IllegalArgumentException("RecyclerView not found") 
        downloadingRecyclerView = findViewById<RecyclerView>(R.id.downloadingRecyclerView)  ?: throw IllegalArgumentException("RecyclerView not found") 
        noDownloadingRecyclerView = findViewById<RecyclerView>(R.id.noDownloadingRecyclerView)  ?: throw IllegalArgumentException("RecyclerView not found") 

        val spanCount = 3
        recyclerView.layoutManager = GridLayoutManager(context, spanCount)
        recyclerView.adapter = appAdapter

        downloadingRecyclerView.layoutManager = LinearLayoutManager(context)
        downloadingRecyclerView.adapter = appDownloadAdapter
//        (downloadingRecyclerView.itemAnimator as? RecyclerView.ItemAnimator)?.isSupportsChangeAnimations =
//            false

        noDownloadingRecyclerView.layoutManager = LinearLayoutManager(context)
        noDownloadingRecyclerView.adapter = appNoDownloadAdapter
//        (noDownloadingRecyclerView.itemAnimator as? RecyclerView.ItemAnimator)?.isSupportsChangeAnimations =
//            false
    }

    private fun initData() {
        if(Utils.isNetworkAvailable(context)){
            getAppInfoList()
        }else{
            getRawList()
        }


        nextBtn.setOnClickListener {
//            if (DOWNLOAD_STATUS == 0) {
//            DOWNLOAD_STATUS = 1;
//            recyclerView.setVisibility(View.GONE);
//            downloadingRecyclerView.setVisibility(View.VISIBLE);
//            noDownloadingRecyclerView.setVisibility(View.VISIBLE);
//            installApp();
//            }
            getAppInfoList()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    fun handlerEvent(event: Event) {
        val appName = event.appName
        when (event.eventType) {
            EventType.DOWNLOAD_START -> downloadStart(appName)
            EventType.DOWNLOAD_IN_PROGRESS -> updateProgress(appName, event.progress)
            EventType.DOWNLOAD_STOP -> downloadStop(appName)
            EventType.DOWNLOAD_COMPLETED, EventType.INSTALL_STARTED -> installStart(appName)
            EventType.INSTALL_COMPLETED -> installComplete(appName)
            EventType.DOWNLOAD_FAILED -> downloadFailed(appName)
            EventType.DOWNLOAD_PENDING -> {}
            EventType.DOWNLOAD_ENTRY ->downloadStart(appName)
            EventType.INSTALL_IN_PROGRESS -> TODO()
            EventType.INSTALL_FAILED -> TODO()
        }
    }

    private fun parseContent( jsonResponse:String){
        try {
            val jsonArray = JsonParser.parseString(jsonResponse).asJsonArray
            val appInfoList = mutableListOf<AppInfo>()
            for (i in 0 until jsonArray.size()) {
                val jsonObject = jsonArray[i].asJsonObject

                var name = jsonObject.get("name").asString
                name = name?.replace(" ", "_")

                var packageName: String? = null 
                packageName = jsonObject.get("packageName").asString 

                var version: String? = null 
                version = jsonObject.get("version").asString 

                var isAvailable: Boolean? = null 
                isAvailable = Utils.toBoolean(jsonObject.get("isAvailable").asString )

                var iconString: String? = null 
                iconString = jsonObject.get("iconString").asString 

                var primaryUrl: String? = null 
                primaryUrl = jsonObject.get("primaryUrl").asString 

                var isInstall = false ;
                var isUpdate = false ;
                var primarySize = 1L
                var primaryMd5Checksum = ""
                var backupUrl = ""
                var backupSize = 1L
                var backupMd5Checksum = ""

                var appInfo = AppInfo( 
                    name  ,
                    packageName ,
                    version ,
                    isAvailable ,
                    isInstall ,
                    isUpdate  ,
                    iconString ,
                    primaryUrl ,
                    primarySize ,
                    primaryMd5Checksum ,
                    backupUrl ,
                    backupSize ,
                    backupMd5Checksum 
                )

                if (appInfo.packageName != null) {
                    var isInstall = Utils.isAppInstalled(context, appInfo.packageName!!)
                    var curVersion =
                        Utils.getAppVersionName(context, appInfo.packageName!!)
                    Log.w(TAG, "curVersion " + curVersion + ",isInstall " + isInstall)
                    if (curVersion != null) {
                        var isUpdate =
                            Utils.compareVersion(appInfo.version!!, curVersion)
                        appInfo.isInstall = isInstall;
                        appInfo.isUpdate = isUpdate > 0
                    }
                }

                appInfoList.add(appInfo)
            }
            val appDownloadInfoList = appInfoList.map {
                AppDownloadInfo(
                    it,
                    IS_SELECTED,
                    Utils.base64ToBitmap(it.iconString)
                )
            }
            singleton.setAppDownloadInfoList(appDownloadInfoList)
            singleton.setRequestStatus(RequestStatus.REQUEST_SUCCESS)
            Log.w(TAG, "appDownloadInfoList size ${appDownloadInfoList.size}")
            handler.sendMessage(handler.obtainMessage(SUCCESS))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getRawList(){
        val inputStream = context.resources.openRawResource(R.raw.apps)
        val jsonString = inputStream.bufferedReader().use { it.readText() }
        if (jsonString != null) {
            parseContent(jsonString)
        }
    }

    private fun getAppInfoList() {
        if (!singleton.hasNetworkRequestSucceeded()) {
            HttpUtils.get(HttpUtils.APP_INFO_URL, object : HttpUtils.HttpCallback {
                override fun onResponse(response: okhttp3.Response) {
                    try {
                        val jsonResponse = response.body()?.string()
                        Log.w(TAG, "jsonResponse:  ${jsonResponse}")
                        if (jsonResponse != null) {
                            parseContent(jsonResponse)
                        }
                    } catch (e: Exception) {
                        singleton.setRequestStatus(RequestStatus.REQUEST_FAILED)
                        Log.e(TAG, "http onResponse exception = ${e.message}")
                    }
                }

                override fun onFailure(e: Exception) {
                    Log.e(TAG, "http failure exception = ${e.message}")
                    handler.sendMessage(handler.obtainMessage(FAILURE))
                }

                override fun onError(e: IOException) {
                    Log.e(TAG, "http error exception = ${e.message}")
                    handler.sendMessage(handler.obtainMessage(ERROR))
                }
            })
        } else {
            handler.sendMessage(handler.obtainMessage(SUCCESS))
        }
    }

    fun updateProgress(appName: String, progress: Int) {
        val appDownloadInfo = singleton.getAppDownloadInfo(appName)
        if (appDownloadInfo?.eventType != EventType.DOWNLOAD_IN_PROGRESS || progress == appDownloadInfo.progress) {
            return
        }
        appDownloadInfo.progress = progress
        appDownloadAdapter.updateProgress(appName)
    }

    fun requestFinish(appName: String) {
        val appDownloadInfo = singleton.getAppDownloadInfo(appName)
        val eventType = appDownloadInfo?.eventType
        if (eventType != EventType.DOWNLOAD_PENDING && eventType != EventType.DOWNLOAD_FAILED) {
            return
        }
        appDownloadInfo.eventType = EventType.DOWNLOAD_PENDING
        EventBus.getDefault().post(Event(EventType.DOWNLOAD_PENDING, appName))

        val appInfo = appDownloadInfo.appInfo
        appDownloadInfo.isSelected = IS_SELECTED

        if (appInfo == null) {
            Log.w(TAG, "appInfo is null")
            return
        }

        if (downloadService == null) {
            Log.w(TAG, "downloadService is null")
            return
        }

        appDownloadAdapter.add(appDownloadInfo)
        appNoDownloadAdapter.remove(appDownloadInfo)
    }

    fun downloadStart(appName: String) {
        val apkName = "$appName.apk"
        val downloadDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadDir, apkName)
        if (file.exists()) {
            file.delete()
        }

        val appDownloadInfo = singleton.getAppDownloadInfo(appName)
        val eventType = appDownloadInfo?.eventType
        if (eventType != EventType.DOWNLOAD_PENDING && eventType != EventType.DOWNLOAD_FAILED  && eventType != EventType.DOWNLOAD_ENTRY) {
            Log.w(TAG, "eventType is error " + eventType)
         //   return
        }
        appDownloadInfo?.eventType = EventType.DOWNLOAD_IN_PROGRESS
        val appInfo = appDownloadInfo?.appInfo
        if (appInfo == null) {
            Log.w(TAG, "appInfo is null")
            return
        } else {
            Log.w(TAG, "appInfo " + appInfo)
            if (appInfo.packageName != null && appInfo.version != null) {
                var curVersion = Utils.getAppVersionName(context, appInfo.packageName!!)
                Log.w(TAG, "curVersion " + curVersion)
                if (curVersion != null) {
                    var isUpdate = Utils.compareVersion(appInfo.version!!, curVersion)
                    Log.w(TAG, "isUpdate " + isUpdate)
                    if (isUpdate <= 0) {
                        runOnUiThread {
                            Toast.makeText(
                                context,
                                "$appName ${context.getString(R.string.latest_version)}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        appDownloadInfo.eventType = EventType.DOWNLOAD_PENDING
                        return
                    }
                }

            }

        }

        EventBus.getDefault().post(Event(EventType.DOWNLOAD_IN_PROGRESS, appName))
        appDownloadInfo.isSelected = IS_SELECTED

        if (downloadService == null) {
            Log.w(TAG, "downloadService is null")
            return
        }

        if (eventType == EventType.DOWNLOAD_FAILED && appInfo.backupUrl != null) {
            appInfo.name?.let {
                appInfo.backupMd5Checksum?.let { it1 ->
                    downloadService?.downloadApk(
                        context,
                        appInfo.backupUrl!!,
                        it,
                        appInfo.backupSize,
                        it1
                    )
                }
            }
        } else {
            appInfo.primaryUrl?.let {
                appInfo.name?.let { it1 ->
                    appInfo.primaryMd5Checksum?.let { it2 ->
                        downloadService?.downloadApk(
                            context,
                            it,
                            it1,
                            appInfo.primarySize,
                            it2
                        )
                    }
                }
            }
        }
        appDownloadAdapter.add(appDownloadInfo)
        appNoDownloadAdapter.remove(appDownloadInfo)
    }

    fun downloadStop(appName: String) {
        val appDownloadInfo = singleton.getAppDownloadInfo(appName)
        appDownloadInfo?.progress = -1
        appDownloadInfo?.isSelected = IS_NOT_SELECTED
        appDownloadInfo?.eventType = EventType.DOWNLOAD_PENDING

        downloadService?.cancel(appName)

        if (appDownloadInfo != null) {
            appDownloadAdapter.remove(appDownloadInfo)
            appNoDownloadAdapter.add(appDownloadInfo)
        }
    }

    fun installStart(appName: String) {
        Log.w(TAG, "installStart  appName $appName")
        val appDownloadInfo = singleton.getAppDownloadInfo(appName)
        appDownloadInfo?.eventType = EventType.INSTALL_STARTED
        appDownloadAdapter.updateEventType(appName)
    }

    fun installComplete(appName: String) {
        Log.w(TAG, "installComplete  appName $appName")
        val appDownloadInfo = singleton.getAppDownloadInfo(appName)
        if (appDownloadInfo == null) {
            Log.e(TAG, "appDownloadInfo is null")
            return
        }
        appDownloadInfo.eventType = EventType.INSTALL_COMPLETED
        downloadService?.cancel(appName)
        appDownloadAdapter.updateEventType(appName)
    }

    // downloadFailed 方法
    fun downloadFailed(appName: String) {
        Toast.makeText(
            context,
            "$appName ${context.getString(R.string.download_failed)}",
            Toast.LENGTH_SHORT
        ).show()

        val appDownloadInfo = singleton.getAppDownloadInfo(appName) ?: return
        appDownloadInfo.progress = -1
        appDownloadInfo.eventType = EventType.DOWNLOAD_FAILED

        downloadService?.cancel(appName)

        appDownloadAdapter.updateEventType(appName)
    }

    // installApp 方法
    fun installApp() {
        val appDownloadInfoList = Singleton.instance.getAppDownloadInfoList() ?: return

        appDownloadInfoList.forEach { appDownloadInfo ->
            val appInfo = appDownloadInfo.appInfo
            val primaryUrl = appInfo.primaryUrl

            if (primaryUrl != null && appDownloadInfo.isSelected) {
                appInfo.name?.let { requestFinish(it) }
            } else {
                appNoDownloadAdapter.add(appDownloadInfo)
            }
        }
    }

    fun getDownloadService(): DownloadService? {
        return downloadService
    }

    override fun onDestroy() {
        super.onDestroy()  // Kotlin 中建议先调用 super.onDestroy()

        try {
            EventBus.getDefault().unregister(this)
            intentService?.let { stopService(it) }  // 如果 intentService 不为 null，则调用 stopService
            connection?.let { unbindService(it) }   // 如果 connection 不为 null，则调用 unbindService
            android.os.Process.killProcess(android.os.Process.myPid());
            // unregisterReceiver(mInstallResultReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}