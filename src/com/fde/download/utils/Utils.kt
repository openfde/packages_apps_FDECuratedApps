package com.fde.download.utils

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64

class Utils {
    companion object {
        fun toInt(obj: Any?): Int {
            return try {
                toDouble(obj).toInt()
            } catch (e: Exception) {
                0
            }
        }

        // 将任意对象转换为 Double
        fun toDouble(obj: Any?): Double {
            return try {
                toString(obj).toDouble()
            } catch (e: Exception) {
                0.0
            }
        }

        // 将任意对象转换为 String
        fun toString(obj: Any?): String {
            return obj?.toString()?.trim() ?: ""
        }

        // 将任意对象转换为 Boolean
        fun toBoolean(obj: Any?): Boolean {
            return try {
                toString(obj).toBooleanStrict()
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        fun String.toBooleanStrict(): Boolean {
            return when (this) {
                "true" -> true
                "false" -> false
                else -> throw IllegalArgumentException("Invalid boolean value: $this")
            }
        }

        fun base64ToBitmap(base64String: String?): Bitmap? {
            if (base64String.isNullOrBlank()) {
                return null
            }
            // 将 Base64 字符串解码为字节数组
            val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
            // 使用 BitmapFactory 将字节数组解码为 Bitmap
            return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        }

        fun getAppName(context: Context, packageName: String): String {
            return try {
                val applicationInfo = context.packageManager.getApplicationInfo(packageName, 0)
                context.packageManager.getApplicationLabel(applicationInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                packageName
            }
        }

        fun isAppInstalled(context: Context, packageName: String): Boolean {
            return try {
                context.packageManager.getApplicationInfo(packageName, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }

        fun getAppVersionName(context: Context, packageName: String): String? {
            return try {
                val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
                packageInfo.versionName
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
                null
            }
        }

        fun compareVersion(version1: String, version2: String): Int {
            try {
                val parts1 = version1.split(".").map { it.toIntOrNull() ?: 0 }
                val parts2 = version2.split(".").map { it.toIntOrNull() ?: 0 }
                val maxLength = maxOf(parts1.size, parts2.size)

                for (i in 0 until maxLength) {
                    val v1 = parts1.getOrNull(i) ?: 0
                    val v2 = parts2.getOrNull(i) ?: 0

                    if (v1 > v2) return 1
                    if (v1 < v2) return -1
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return 0
        }


        fun isNetworkAvailable(context: Context): Boolean {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = connectivityManager?.activeNetwork
            val capabilities = connectivityManager?.getNetworkCapabilities(network)

            return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }

    }
}