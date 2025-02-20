package com.fde.download.net

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class RetryInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request: Request = chain.request()
        var response: Response? = null
        var responseOK = false
        var tryCount = 0
        while (!responseOK && tryCount < 3) {
            response = chain.proceed(request)
            responseOK = response.isSuccessful
            tryCount++
        }
        if (response == null || !responseOK) {
            throw IOException("Failed to execute request after $tryCount attempts")
        }
        return response
    }
}