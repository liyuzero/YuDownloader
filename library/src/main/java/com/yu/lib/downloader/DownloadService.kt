package com.yu.lib.downloader

import android.app.IntentService
import android.content.Intent

class DownloadService(name: String): IntentService(name) {
    override fun onHandleIntent(intent: Intent?) {

    }

    override fun onDestroy() {
        super.onDestroy()
    }
}