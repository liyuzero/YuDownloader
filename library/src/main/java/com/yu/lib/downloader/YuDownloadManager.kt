package com.yu.lib.downloader

import android.annotation.SuppressLint
import android.os.Handler
import java.io.BufferedInputStream
import java.io.File
import java.io.RandomAccessFile
import java.security.cert.CertificateException
import java.util.concurrent.PriorityBlockingQueue
import javax.net.ssl.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection


class YuDownloadManager {

    companion object {
        val instance: YuDownloadManager by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            YuDownloadManager()
        }

        private const val MAX_DOWNLOADING_NUM = 3

        fun init(config: YuDownloadConfig) {
            instance.mConfig = config
            instance.mTaskInfoManager = TaskInfoManager(config)
            instance.initData()
        }
    }

    internal lateinit var mTaskInfoManager: TaskInfoManager
    internal lateinit var mConfig: YuDownloadConfig
    val mMainHandler = Handler()

    private var mDownloadingQueue: PriorityBlockingQueue<DownloadTask> = PriorityBlockingQueue()
    private var mDownloadCompleteQueue: PriorityBlockingQueue<DownloadTask> = PriorityBlockingQueue()

    fun getDownloadingList(): ArrayList<DownloadTask>{
        val list = ArrayList<DownloadTask>()
        for (task in mDownloadingQueue) {
            list.add(task)
        }
        return list
    }

    fun getDownloadCompleteList(): ArrayList<DownloadTask>{
        val list = ArrayList<DownloadTask>()
        for (task in mDownloadCompleteQueue) {
            list.add(task)
        }
        return list
    }

    //初始化数据队列
    private fun initData() {
        val taskList = mTaskInfoManager.getTaskList()
        for (task in taskList) {
            if(task.getCurStatus().isComplete()) {
                mDownloadCompleteQueue.add(task)
            } else {
                mDownloadingQueue.add(task)
            }

            if(!task.getCurStatus().isComplete() && task.getCurStatus() != DownloadStatus.ERROR) {
                task.setCurStatus(DownloadStatus.PAUSED)
            }
        }
    }

    /*------------------------------------- API START -----------------------------------*/

    //下载任务的初始化
    fun createAndInitTask(url: String, listener: OnYuDownloadListener) {
        DownloadService.executeFunction {
            val size = DownloadUtils.getNetFileSize(url).toLong()
            listener.createSuccess(if(size == -1L) null else DownloadTask(url, size))
        }
    }

    //添加下载任务
    fun enqueueTask(task: DownloadTask) {
        if(mDownloadingQueue.contains(task)) {
            return
        }
        mTaskInfoManager.insertOrUpdateTask(task)
        DownloadService.executeFunction {
            task.setCurStatus(DownloadStatus.WAITING)
            mDownloadingQueue.add(task)
            if(getCurDownloadingNum() >= MAX_DOWNLOADING_NUM) {
                task.notifyListeners(DownloadStatus.WAITING)
            } else {
                autoDispatchTask()
            }
        }
    }

    //状态操作转移，针对开始和暂停操作，且只针对非完成状态，省的外面自己写判断逻辑
    fun dispatchStartOrPauseTask(task: DownloadTask) {
        if (task.getCurStatus() == DownloadStatus.COMPLETE) {
            return
        }
        DownloadService.executeFunction {
            when(task.getCurStatus()) {
                DownloadStatus.ERROR,
                DownloadStatus.PAUSED -> {
                    startDownloadTask(task)
                }
                DownloadStatus.RUNNING -> {
                    pauseDownloadTask(task)
                }
                DownloadStatus.WAITING -> {
                    task.notifyListeners(DownloadStatus.PAUSED)
                }
                //余下完成状态不做任何处理
                DownloadStatus.COMPLETE_FILE_NOT_EXIST,
                DownloadStatus.COMPLETE -> {
                    //do nothing
                }
            }
        }
    }

    fun deleteTask(task: DownloadTask, isDeleteFile: Boolean) {
        mDownloadingQueue.remove(task)
        mDownloadCompleteQueue.remove(task)
        mTaskInfoManager.deleteTask(task)
        if(isDeleteFile) {
            File(task.filePath).delete()
        }
    }


    /*------------------------------------- API END -----------------------------------*/

    private fun startDownloadTask(task: DownloadTask) {
        task.setCurStatus(DownloadStatus.RUNNING)
        DownloadUtils.download(task)
    }

    private fun pauseDownloadTask(task: DownloadTask) {
        task.setCurStatus(DownloadStatus.PAUSED)
    }

    private fun autoDispatchTask() {
        var firstWaitingTask: DownloadTask? = null

        for (task in mDownloadingQueue) {
            if (firstWaitingTask == null && task.getCurStatus() == DownloadStatus.WAITING) {
                firstWaitingTask = task
            }
        }
        if (getCurDownloadingNum() < MAX_DOWNLOADING_NUM) {
            if (firstWaitingTask != null) {
                startDownloadTask(firstWaitingTask)
            }
        }
    }

    private fun getCurDownloadingNum(): Int {
        var curDownloadingNum = 0

        for (task in mDownloadingQueue) {
            if (task.getCurStatus() == DownloadStatus.RUNNING) {
                curDownloadingNum++
            }
        }

        return curDownloadingNum
    }

    internal fun onDestroy() {
        for (task in mDownloadingQueue) {
            if(!task.getCurStatus().isComplete() && task.getCurStatus() != DownloadStatus.ERROR) {
                task.setCurStatus(DownloadStatus.PAUSED)
            }
        }
    }
}

internal object DownloadUtils {

    fun getNetFileSize(url: String): Int {
        return try {
            val conn = getConn(url)
            conn.setRequestProperty("Accept-Encoding","identity");
            conn.connect()
            val size = conn.contentLength
            size
        } catch (e: Exception) {
            -1
        }
    }

    fun download(task: DownloadTask) {
        val conn = getConn(task.url)
        conn.setRequestProperty("Range", "bytes=" + task.downloadedSize + "-")
        conn.setRequestProperty("Connection", "Keep-Alive")
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/4.0 (compatible; MSIE 7.0; Windows NT 5.2; Trident/4.0; " +
                    ".NET CLR 1.1.4322; .NET CLR 2.0.50727; .NET CLR 3.0.04506.30; " +
                    ".NET CLR 3.0.4506.2152; .NET CLR 3.5.30729)")
        val stream = BufferedInputStream(conn.getInputStream())
        val accessFile = RandomAccessFile(task.filePath, "rw")
        accessFile.seek(task.downloadedSize)

        task.notifyListeners(DownloadStatus.RUNNING)

        try {

            if(task.getCurStatus() == DownloadStatus.PAUSED) {
                task.notifyListeners(DownloadStatus.PAUSED)
                return
            }

            var len: Int
            val buffer = ByteArray(1024);
            while ((stream.read(buffer).apply { len = this }) > 0) {
                if(task.getCurStatus() == DownloadStatus.PAUSED) {
                    task.notifyListeners(DownloadStatus.PAUSED)
                    return
                }

                accessFile.write(buffer, 0, len)
                task.downloadedSize += len
                YuDownloadManager.instance.mTaskInfoManager.saveDownloadProgress(task.id, task.downloadedSize)

                val curTime = System.currentTimeMillis()
                if(task.preProgressNotifyTime == 0L || curTime - task.preProgressNotifyTime >= YuDownloadManager.instance.mConfig.minProgressCallbackTimeMills) {
                    task.preProgressNotifyTime = curTime
                    task.notifyListeners(DownloadStatus.RUNNING)
                }
            }

            if(File(task.filePath).exists()) {
                task.notifyListeners(DownloadStatus.COMPLETE)
            } else {
                task.notifyListeners(DownloadStatus.COMPLETE_FILE_NOT_EXIST)
            }
        } catch (e: java.lang.Exception) {
            task.notifyListeners(DownloadStatus.ERROR)
        } finally {
            stream.close()
            accessFile.close()
        }
    }

    private fun post(runnable: Runnable) {
        YuDownloadManager.instance.mMainHandler.post(runnable)
    }

    private fun getConn(urlStr: String): URLConnection {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.requestMethod = "GET"

        val useHttps = urlStr.startsWith("https")
        if (useHttps) {
            val https = conn as HttpsURLConnection
            trustAllHosts(https)
            https.hostnameVerifier = HostnameVerifier { _, _ -> true }
        }

        return conn
    }

    /**
     * 信任所有
     * @param connection
     * @return
     */
    private fun trustAllHosts(connection: HttpsURLConnection) {
        val trustAllCerts = arrayOf<X509TrustManager>(object : X509TrustManager {
            @SuppressLint("TrustAllX509TrustManager")
            @Throws(CertificateException::class)
            override fun checkClientTrusted(
                chain: Array<java.security.cert.X509Certificate>,
                authType: String
            ) {
            }

            @SuppressLint("TrustAllX509TrustManager")
            @Throws(CertificateException::class)
            override fun checkServerTrusted(
                chain: Array<java.security.cert.X509Certificate>,
                authType: String
            ) {
            }

            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> {
                return arrayOf()
            }
        })

        // Install the all-trusting trust manager
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        // Create an ssl socket factory with our all-trusting manager
        //val sslSocketFactory = sslContext.socketFactory
        // val oldFactory = connection.sslSocketFactory
        try {
            val sc = SSLContext.getInstance("TLS")
            sc.init(null, trustAllCerts, java.security.SecureRandom())
            val newFactory = sc.socketFactory
            connection.sslSocketFactory = newFactory
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}

interface OnYuDownloadListener {
    fun createSuccess(task: DownloadTask?)
}