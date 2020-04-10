package com.yu.lib.downloader

import android.app.Application
import android.content.SharedPreferences

data class YuDownloadConfig(val context: Application, val dbName: String, val rootDirPath: String,
                            val minProgressCallbackTimeMills: Long, val spFactory: SpFactory)

interface SpFactory {
    fun getSp(): SharedPreferences
}