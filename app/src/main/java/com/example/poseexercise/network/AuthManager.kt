package com.example.poseexercise.network

import android.content.Context
import android.content.SharedPreferences

class AuthManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString("token", null)
        set(value) {
            prefs.edit().putString("token", value).apply()
            ApiClient.setToken(value)
        }

    var username: String?
        get() = prefs.getString("username", null)
        set(value) = prefs.edit().putString("username", value).apply()

    var email: String?
        get() = prefs.getString("email", null)
        set(value) = prefs.edit().putString("email", value).apply()

    val isLoggedIn: Boolean
        get() = !token.isNullOrBlank()

    fun logout() {
        token = null
        username = null
        email = null
        ApiClient.setToken(null)
    }

    fun init() {
        token?.let { ApiClient.setToken(it) }
    }
}
