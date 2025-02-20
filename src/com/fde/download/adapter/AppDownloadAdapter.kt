package com.fde.download.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fde.download.R
import com.fde.download.model.AppDownloadInfo
import com.fde.download.model.Event
import com.fde.download.model.EventType
import com.fde.download.utils.Utils
import org.greenrobot.eventbus.EventBus

class AppDownloadAdapter(
    private var appDownloadInfoList: MutableList<AppDownloadInfo> = ArrayList()
) : RecyclerView.Adapter<AppDownloadAdapter.CustomViewHolder>() {
    companion object {
        private const val TAG = "AppDownloadAdapter"
        private const val CLICKABLE = true
        private const val UNCLICKABLE = false
    }

    constructor() : this(ArrayList())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_application_downloading, parent, false)
        return CustomViewHolder(view)
    }

    override fun onBindViewHolder(holder: CustomViewHolder, position: Int) {
        val appDownloadInfo = appDownloadInfoList[position]
        val eventType = appDownloadInfo.eventType

        val appName = appDownloadInfo.appInfo.name
        val bitmap = appDownloadInfo.bitmap
        val progress = appDownloadInfo.progress

        holder.appName.text = appName
        holder.appIcon.setImageBitmap(bitmap)

        holder.appProgressBar.progress = progress
        holder.appProgressBar.progressTintList =
            ColorStateList.valueOf(Color.BLUE)
        holder.stopBtn.setOnClickListener {
            EventBus.getDefault().post(appName?.let { it1 -> Event(EventType.DOWNLOAD_STOP, it1) })
        }

        if(!appDownloadInfo.appInfo.isInstall){
            holder.stopBtn.text = holder.itemView.context.getString(R.string.install)
            holder.stopBtn.setTextColor(holder.itemView.context.getColor(R.color.white))
            holder.stopBtn.setBackground(holder.itemView.context.getDrawable(R.drawable.bg_install_button))
        }else if(appDownloadInfo.appInfo.isUpdate){
            holder.stopBtn.text = holder.itemView.context.getString(R.string.update)
            holder.stopBtn.setTextColor(holder.itemView.context.getColor(R.color.install_bg))
            holder.stopBtn.setBackground(holder.itemView.context.getDrawable(R.drawable.bg_update_button))
        }else{
            holder.stopBtn.text = holder.itemView.context.getString(R.string.installed)
            holder.stopBtn.setTextColor(holder.itemView.context.getColor(R.color.install_bg))
            holder.stopBtn.setBackground(holder.itemView.context.getDrawable(R.drawable.bg_update_button))
        }

        when (eventType) {
            EventType.INSTALL_STARTED -> {
                holder.stopBtn.isClickable = UNCLICKABLE
                holder.stopBtn.text = holder.itemView.context.getString(R.string.installing)
            }

            EventType.INSTALL_COMPLETED -> {
                holder.stopBtn.isClickable = UNCLICKABLE
                holder.stopBtn.text = holder.itemView.context.getString(R.string.installed)
                holder.stopBtn.setTextColor(holder.itemView.context.getColor(R.color.install_bg))
                holder.stopBtn.setBackground(holder.itemView.context.getDrawable(R.drawable.bg_update_button))
            }

            EventType.DOWNLOAD_IN_PROGRESS -> {
                holder.stopBtn.isClickable = CLICKABLE
//                holder.stopBtn.text = holder.itemView.context.getString(R.string.cancel)
//                holder.appProgressBar.progressTintList =
//                    ColorStateList.valueOf(Color.BLUE)
                holder.stopBtn.setOnClickListener {
//                    EventBus.getDefault().post(appName?.let { it1 ->
//                        Event(
//                            EventType.DOWNLOAD_STOP,
//                            it1
//                        )
//                    })
                }
            }

            EventType.DOWNLOAD_FAILED -> {
                holder.stopBtn.isClickable = CLICKABLE
                holder.stopBtn.text = holder.itemView.context.getString(R.string.retry)
                holder.appProgressBar.progressTintList =
                    ColorStateList.valueOf(Color.RED)
                holder.stopBtn.setOnClickListener {
                    EventBus.getDefault().post(appName?.let { it1 ->
                        Event(
                            EventType.DOWNLOAD_START,
                            it1
                        )
                    })
                }
            }

            EventType.DOWNLOAD_PENDING -> {
                holder.stopBtn.isClickable = CLICKABLE
//                holder.stopBtn.text = holder.itemView.context.getString(R.string.update)
                holder.stopBtn.setOnClickListener {

                EventBus.getDefault().post(appName?.let { it1 ->
                    Event(
                        EventType.DOWNLOAD_START,
                        it1
                    )
                })
                }
            }

            EventType.DOWNLOAD_ENTRY -> {}
            EventType.DOWNLOAD_START -> TODO()
            EventType.DOWNLOAD_COMPLETED -> TODO()
            EventType.DOWNLOAD_STOP -> TODO()
            EventType.INSTALL_IN_PROGRESS -> TODO()
            EventType.INSTALL_FAILED -> TODO()
        }
    }

    override fun getItemCount(): Int = appDownloadInfoList.size

    private fun getIndexByAppName(appName: String): Int {
        return appDownloadInfoList.indexOfFirst {
            it.appInfo.name.equals(
                appName,
                ignoreCase = true
            )
        }
    }

    fun updateProgress(appName: String) {
        val position = getIndexByAppName(appName)
        if (position != -1) {
            notifyItemChanged(position)
        }
    }

    fun updateEventType(appName: String) {
        val position = getIndexByAppName(appName)
        if (position != -1) {
            notifyItemChanged(position)
        }
    }

    fun remove(appDownloadInfo: AppDownloadInfo) {
        if (appDownloadInfoList.remove(appDownloadInfo)) {
            notifyDataSetChanged()
        }
    }

    fun add(appDownloadInfo: AppDownloadInfo) {
        if (!appDownloadInfoList.contains(appDownloadInfo)) {
            appDownloadInfoList.add(appDownloadInfo)
            notifyDataSetChanged()
        }
    }

    inner class CustomViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById<ImageView>(R.id.appIcon) ?: throw IllegalArgumentException("ImageView not found")
        val appName: TextView = view.findViewById<TextView>(R.id.appName) ?: throw IllegalArgumentException("TextView not found")
        val appProgressBar: ProgressBar = view.findViewById<ProgressBar>(R.id.appProgressBar) ?: throw IllegalArgumentException("ProgressBar not found")
        val stopBtn: Button = view.findViewById<Button>(R.id.stopBtn) ?: throw IllegalArgumentException("Button not found")
    }
}