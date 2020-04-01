package com.yu.lib.downloader

import android.annotation.SuppressLint
import android.os.Handler
import java.io.BufferedInputStream
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
        }
    }

    lateinit var mConfig: YuDownloadConfig
    val mMainHandler = Handler()

    private var mDownloadingQueue: PriorityBlockingQueue<DownloadTask> =
        PriorityBlockingQueue<DownloadTask>()
    private var mCompleteQueue: PriorityBlockingQueue<DownloadTask> =
        PriorityBlockingQueue<DownloadTask>()

    /*------------------------------------- API START -----------------------------------*/

    fun addTask(url: String, filePath: String) {
        val task = TaskInfoManager.instance.createTask(url, filePath)
        mDownloadingQueue.add(task)
        if(getCurDownloadingNum() >= MAX_DOWNLOADING_NUM) {
            mMainHandler.post {
                task.mListenData.value = DownloadListenData(DownloadListenStatus.WAITING, task)
            }
            return
        }
        dispatchTask()
    }

    fun start(task: DownloadTask) {
        if (task.getCurStatus() == DownloadStatus.RUNNING || task.getCurStatus() == DownloadStatus.COMPLETE) {
            return
        }
        when (task.getCurStatus()) {
            DownloadStatus.COMPLETE_FILE_NOT_EXIST,
            DownloadStatus.PAUSED,
            DownloadStatus.WAITING,
            DownloadStatus.ERROR -> {
                DownloadUtils.download(task)
            }
        }
    }

    fun stop(task: DownloadTask) {
        task.setCurStatus(DownloadStatus.PAUSED)
    }

    /*------------------------------------- API END -----------------------------------*/

    private fun dispatchTask() {
        var firstWaitingTask: DownloadTask? = null

        for (task in mDownloadingQueue) {
            if (firstWaitingTask == null && task.getCurStatus() == DownloadStatus.WAITING) {
                firstWaitingTask = task
            }
        }
        if (getCurDownloadingNum() < MAX_DOWNLOADING_NUM) {
            if (firstWaitingTask != null) {
                start(firstWaitingTask)
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
            stop(task)
        }
    }
}

internal object DownloadUtils {

    fun download(task: DownloadTask) {
        val conn = getConn(task)
        conn.connect()
        val stream = BufferedInputStream(conn.getInputStream())
        val accessFile = RandomAccessFile(task.filePath, "rw")
        accessFile.seek(task.downloadedSize)

        post(Runnable {
            task.mListenData.value = DownloadListenData(DownloadListenStatus.START, task)
        })

        try {
            var len: Int
            val buffer = ByteArray(1024);
            while ((stream.read(buffer).apply { len = this }) > 0) {
                accessFile.write(buffer, 0, len)
                task.downloadedSize += len

                post(Runnable {
                    task.mListenData.value = DownloadListenData(DownloadListenStatus.PROGRESS, task)
                })
            }

            post(Runnable {
                task.mListenData.value = DownloadListenData(DownloadListenStatus.COMPLETE, task)
            })
        } catch (e: java.lang.Exception) {
            post(Runnable {
                task.mListenData.value = DownloadListenData(DownloadListenStatus.FAIL, task)
            })
        } finally {
            stream.close()
            accessFile.close()
        }
    }

    private fun post(runnable: Runnable) {
        YuDownloadManager.instance.mMainHandler.post(runnable)
    }

    private fun getConn(task: DownloadTask): URLConnection {
        val url = URL(task.url)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.requestMethod = "GET"

        val useHttps = task.url.startsWith("https")
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