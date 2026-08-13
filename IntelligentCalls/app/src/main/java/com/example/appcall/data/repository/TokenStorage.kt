package com.example.appcall.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("appcall_prefs", Context.MODE_PRIVATE)

    var token: String?
        get() {
            val t = prefs.getString("jwt_token", null)
            return if (t.isNullOrBlank() || t == "null") null else t
        }
        set(value) {
            if (value.isNullOrBlank() || value == "null") {
                prefs.edit().remove("jwt_token").commit()
            } else {
                prefs.edit().putString("jwt_token", value).commit()
            }
        }

    val authHeader: String?
        get() = token?.let { "Bearer $it" }

    fun clear() {
        prefs.edit().remove("jwt_token").commit()
    }
}
