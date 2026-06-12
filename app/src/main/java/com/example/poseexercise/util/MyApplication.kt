package com.example.poseexercise.util

import android.app.Application
import com.example.poseexercise.network.AuthManager

class MyApplication : Application() {
    companion object {
        private lateinit var instance: MyApplication
        fun getInstance(): MyApplication = instance
    }

    lateinit var authManager: AuthManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        authManager = AuthManager(this)
        authManager.init()
    }
}
