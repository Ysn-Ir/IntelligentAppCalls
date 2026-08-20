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

    var appLanguage: String
        get() = prefs.getString("app_language", "en") ?: "en"
        set(value) {
            prefs.edit().putString("app_language", value).apply()
        }

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val rawCustomUrl = customBaseUrl?.trim()

        val requestBuilder = request.newBuilder()
            .header("X-App-Language", appLanguage)
            .header("Accept-Language", appLanguage)

        if (!rawCustomUrl.isNullOrEmpty()) {
            var normalizedUrl = rawCustomUrl
            if (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) {
                normalizedUrl = "http://$normalizedUrl"
            }
            val newHttpUrl = normalizedUrl.toHttpUrlOrNull()
            if (newHttpUrl != null) {
                val updatedUrl = request.url.newBuilder()
                    .scheme(newHttpUrl.scheme)
                    .host(newHttpUrl.host)
                    .port(if (newHttpUrl.port != 80 && newHttpUrl.port != 443) newHttpUrl.port else if (rawCustomUrl.contains(":8000")) 8000 else newHttpUrl.port)
                    .build()
                requestBuilder.url(updatedUrl)
            }
        }
        return chain.proceed(requestBuilder.build())
    }
}
