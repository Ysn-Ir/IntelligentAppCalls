package com.example.appcall.data.api

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DynamicUrlInterceptor @Inject constructor(
    @ApplicationContext private val context: Context
) : Interceptor {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("network_settings", Context.MODE_PRIVATE)

    var customBaseUrl: String?
        get() = prefs.getString("custom_base_url", null)
        set(value) {
            prefs.edit().putString("custom_base_url", value).apply()
        }

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val customUrl = customBaseUrl

        if (!customUrl.isNullOrEmpty() && customUrl.isNotBlank()) {
            val newHttpUrl = customUrl.toHttpUrlOrNull()
            if (newHttpUrl != null) {
                val updatedUrl = request.url.newBuilder()
                    .scheme(newHttpUrl.scheme)
                    .host(newHttpUrl.host)
                    .port(newHttpUrl.port)
                    .build()
                request = request.newBuilder().url(updatedUrl).build()
            }
        }
        return chain.proceed(request)
    }

    private fun String?.isNull_or_empty(): Boolean = this.isNullOrEmpty() || this.isBlank()
}
