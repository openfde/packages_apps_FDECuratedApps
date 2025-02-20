package com.fde.download.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fde.download.model.AppDownloadInfo
import com.fde.download.R
import com.fde.download.model.ButtonTextEvent
import com.fde.download.utils.EventBusUtils

class AppAdapter : RecyclerView.Adapter<AppAdapter.ApplicationHolder>() {
    private val TAG = "AppAdapter"
    private var appDownloadInfoList = mutableListOf<AppDownloadInfo>()
    private var mRecyclerView: RecyclerView? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ApplicationHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.item_application, parent, false)
        return ApplicationHolder(view)
    }

    override fun onBindViewHolder(holder: ApplicationHolder, position: Int) {
        val context = holder.itemView.context
        val appDownloadInfo = appDownloadInfoList[position]
        holder.applicationIcon.setImageBitmap(appDownloadInfo.bitmap)
        holder.applicationName.text = appDownloadInfo.appInfo.name
        holder.checkedIcon.visibility =
            if (appDownloadInfo.isSelected) View.VISIBLE else View.INVISIBLE

        holder.itemView.setOnClickListener {
            if (holder.checkedIcon.visibility == View.INVISIBLE) {
                holder.checkedIcon.visibility = View.VISIBLE
                appDownloadInfo.isSelected = true
            } else if (holder.checkedIcon.visibility == View.VISIBLE) {
                holder.checkedIcon.visibility = View.INVISIBLE
                appDownloadInfo.isSelected = false
            }
            EventBusUtils.sendButtonTextEvent(ButtonTextEvent(generateButtonText(context)))
        }
    }

    override fun getItemCount(): Int = appDownloadInfoList.size

    inner class ApplicationHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val applicationIcon: ImageView = itemView.findViewById<ImageView>(R.id.appIcon)?: throw IllegalArgumentException("ImageView not found")
        val applicationName: TextView = itemView.findViewById<TextView>(R.id.applicationName)?: throw IllegalArgumentException("TextView not found")
        val checkedIcon: ImageView = itemView.findViewById<ImageView>(R.id.checked)?: throw IllegalArgumentException("ImageView not found")
    }

    private fun generateButtonText(context: Context): String {
        return if (isNothingSelected()) context.getString(R.string.done_button_text) else context.getString(
            R.string.start_download
        )
    }

    private fun isNothingSelected(): Boolean {
        return appDownloadInfoList.none { it.isSelected }
    }

    fun setAppDownloadInfoList(newList: List<AppDownloadInfo>) {
        appDownloadInfoList.clear()
        appDownloadInfoList.addAll(newList)
        notifyDataSetChanged()
    }
}