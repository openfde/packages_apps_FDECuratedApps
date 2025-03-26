package com.fde.download.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fde.download.R
import com.fde.download.model.AppDownloadInfo
import com.fde.download.model.Event
import com.fde.download.model.EventType
import org.greenrobot.eventbus.EventBus

class AppNoDownloadAdapter(
    private var appDownloadInfoList: MutableList<AppDownloadInfo> = ArrayList()
) : RecyclerView.Adapter<AppNoDownloadAdapter.CustomViewHolder>() {
    private val TAG = "AppNoDownloadAdapter"

    constructor() : this(mutableListOf())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_application_no_downloading, parent, false)
        return CustomViewHolder(view)
    }


    override fun onBindViewHolder(holder: CustomViewHolder, position: Int) {
        val appDownloadInfo = appDownloadInfoList[position]
        val appName = appDownloadInfo.appInfo.name
        val bitmap = appDownloadInfo.bitmap
        holder.appName.text = appName?.replace("_"," ")
        holder.appIcon.setImageBitmap(bitmap)

        holder.downloadBtn.setOnClickListener {
            EventBus.getDefault().post(appName?.let { it1 -> Event(EventType.DOWNLOAD_ENTRY, it1) })
        }
    }

    override fun getItemCount(): Int {
        return appDownloadInfoList.size
    }

    private fun getIndexByAppName(appName: String): Int {
        return appDownloadInfoList.indexOfFirst {
            it.appInfo.name.equals(
                appName,
                ignoreCase = true
            )
        }
    }

    fun add(appDownloadInfo: AppDownloadInfo) {
        if (appDownloadInfoList.contains(appDownloadInfo)) return
        appDownloadInfoList.add(appDownloadInfo)
        notifyDataSetChanged()
    }

    fun remove(appDownloadInfo: AppDownloadInfo) {
        appDownloadInfoList.remove(appDownloadInfo)
        notifyDataSetChanged()
    }

    inner class CustomViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById<ImageView>(R.id.appIcon) ?: throw IllegalArgumentException("ImageView not found")
        val appName: TextView = view.findViewById<TextView>(R.id.appName) ?: throw IllegalArgumentException("TextView not found")
        val downloadBtn: Button = view.findViewById<Button>(R.id.downloadBtn) ?: throw IllegalArgumentException("Button not found")
    }
}