package com.fde.download.net

import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

object HttpUtils {
    private const val TAG = "HttpUtils"
    const val APP_INFO_URL = "https://gitee.com/openfde/provision/releases/download/1.3.2/apps.json"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .addInterceptor(RetryInterceptor())
        .dispatcher(Dispatcher().apply {
            maxRequests = 2
            maxRequestsPerHost = 1
        })
        .build()

    fun get(url: String, callback: HttpCallback): Call {
        val request = Request.Builder().url(url).build()
        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "onFailure, Exception = ${e.message}")
                e.printStackTrace()
                callback.onFailure(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    callback.onResponse(response)
                } else {
                    Log.e(TAG, "onResponse, Unexpected code = $response")
                    callback.onError(IOException("Unexpected code $response"))
                }
            }
        })
        return call
    }

    interface HttpCallback {
        fun onResponse(response: Response)
        fun onFailure(e: Exception)
        fun onError(e: IOException)
    }
}