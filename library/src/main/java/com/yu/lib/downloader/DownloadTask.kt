package com.yu.lib.downloader

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import java.io.File

class DownloadTask(val url: String, val filePath: String): Comparable<DownloadTask>{
    internal var id: String = YuDownloadUtils.md5(url + "_" + filePath)
    private var curStatus: DownloadStatus? = DownloadStatus.WAITING

    var downloadedSize: Long = 0
    var totalSize = 0L
    var createTime = 0L
    var completeTime = 0L

    val mListenData: MutableLiveData<DownloadListenData> = MutableLiveData()

    fun addDownloadListener(owner: LifecycleOwner, observer: Observer<DownloadListenData>) {
        mListenData.observe(owner, observer)
    }

    override fun compareTo(other: DownloadTask): Int {
        return other.createTime.compareTo(createTime)
    }

    fun getCurStatus(): DownloadStatus {
        if(curStatus == DownloadStatus.COMPLETE && !File(filePath).exists()) {
            return DownloadStatus.COMPLETE_FILE_NOT_EXIST
        }
        return curStatus!!
    }

    fun setCurStatus(status: DownloadStatus?) {
        curStatus = status
    }

    fun setCurStatus(status: Int) {
        val downloadStatus: DownloadStatus? = null
        when(status) {
            DownloadStatus.PAUSED.type -> {
                DownloadStatus.PAUSED
            }
            DownloadStatus.RUNNING.type -> {
                DownloadStatus.RUNNING
            }
            DownloadStatus.WAITING.type -> {
                DownloadStatus.WAITING
            }
            DownloadStatus.ERROR.type -> {
                DownloadStatus.ERROR
            }
            DownloadStatus.COMPLETE.type -> {
                DownloadStatus.COMPLETE
            }
            DownloadStatus.COMPLETE_FILE_NOT_EXIST.type -> {
                DownloadStatus.COMPLETE_FILE_NOT_EXIST
            }
        }
        setCurStatus(downloadStatus)
    }
}

data class DownloadListenData(val status: DownloadListenStatus, val task: DownloadTask)

enum class DownloadListenStatus {
    WAITING, START, PROGRESS, FAIL, COMPLETE
}

interface DownloadListener {
    fun waiting(task: DownloadTask)
    fun start(task: DownloadTask)
    fun progress(task: DownloadTask)
    fun fail(task: DownloadTask)
    fun complete(task: DownloadTask)
}

enum class DownloadStatus(val message: String, val type: Int) {
    PAUSED("暂停", 0),
    RUNNING("下载中", 1),
    WAITING("等待中", 2),
    ERROR("下载失败", 3),

    COMPLETE("下载完成", 4),
    COMPLETE_FILE_NOT_EXIST("下载完成，但是文件不存在", 5)
}