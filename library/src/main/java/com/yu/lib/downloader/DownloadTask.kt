package com.yu.lib.downloader

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import java.io.File

class DownloadTask(val url: String, val totalSize: Long): Comparable<DownloadTask>{
    var id: String
    private var curStatus: DownloadStatus? = DownloadStatus.WAITING

    val filePath: String = YuDownloadManager.instance.mConfig.rootDirPath + File.separator + url.substring(url.lastIndexOf("/") + 1)
    var downloadedSize: Long = 0
    var createTime = 0L
    var completeTime = 0L

    internal var preProgressNotifyTime = 20L

    init {
        id = YuDownloadUtils.md5(url + "_" + filePath)
        downloadedSize = 0
        createTime = System.currentTimeMillis()
        completeTime = -1
        curStatus = DownloadStatus.WAITING
    }

    private val mListenData: MutableLiveData<DownloadTask> = MutableLiveData()

    fun addDownloadListener(owner: LifecycleOwner, observer: Observer<DownloadTask>) {
        mListenData.observe(owner, observer)
    }

    internal fun notifyListeners(downloadStatus: DownloadStatus) {
        setCurStatus(downloadStatus)
        YuDownloadManager.instance.mMainHandler.post {
            mListenData.value = this
        }
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
        if(curStatus != status) {
            curStatus = status
            YuDownloadManager.instance.mTaskInfoManager.updateTaskStatus(this)
        }
    }

    fun setCurStatus(status: Int) {
        when(status) {
            DownloadStatus.PAUSED.type -> {
                curStatus = DownloadStatus.PAUSED
            }
            DownloadStatus.RUNNING.type -> {
                curStatus = DownloadStatus.RUNNING
            }
            DownloadStatus.WAITING.type -> {
                curStatus = DownloadStatus.WAITING
            }
            DownloadStatus.ERROR.type -> {
                curStatus = DownloadStatus.ERROR
            }
            DownloadStatus.COMPLETE.type -> {
                curStatus = DownloadStatus.COMPLETE
            }
            DownloadStatus.COMPLETE_FILE_NOT_EXIST.type -> {
                curStatus = DownloadStatus.COMPLETE_FILE_NOT_EXIST
            }
        }
    }

    fun getFileSizeDes(): String {
        val kb = totalSize / 1024
        if(kb < 1024) {
            return "$kb" + "kb"
        }
        val mb = totalSize / 1024.0 / 1024.0
        if(mb < 1024) {
            return String.format("%.1f", mb) + "m"
        }
        return String.format("%.2f", totalSize / 1024.0 / 1024.0 / 1024.0) + "m"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DownloadTask

        if (url != other.url) return false

        return true
    }

    override fun hashCode(): Int {
        return url.hashCode()
    }


}

enum class DownloadStatus(val message: String, val type: Int) {
    PAUSED("暂停", 0),
    RUNNING("下载中", 1),
    WAITING("等待中", 2),
    ERROR("下载失败", 3),

    COMPLETE("下载完成", 4),
    COMPLETE_FILE_NOT_EXIST("下载完成，但是文件不存在", 5);

    fun isComplete(): Boolean {
        return type >= 4
    }
}