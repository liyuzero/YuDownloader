package com.yu.lib.downloader

import android.app.Service
import android.content.Intent
import android.os.IBinder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DownloadService: Service() {

    override fun onCreate() {
        super.onCreate()
    }

    override fun onDestroy() {
        super.onDestroy()
        YuDownloadManager.instance.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        private val mThreadPool: ExecutorService = Executors.newFixedThreadPool(3)

        fun executeFunction(method: () -> Unit) {
            mThreadPool.execute {
                method()
            }
        }
    }
}