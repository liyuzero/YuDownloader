package com.yu.lib.downloader

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.URL

internal class TaskInfoManager {

    companion object {
        const val TABLE_NAME = "download_task"
        val instance: TaskInfoManager by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            TaskInfoManager()
        }
    }

    private val mDb = DownloadDatabaseHelper.instance.writableDatabase
    private val mDownloadSp: SharedPreferences = YuDownloadManager.instance.mConfig.spFactory.getSp("yu_download_info")

    private fun getTaskList(): List<DownloadTask> {
        val taskList: MutableList<DownloadTask> = ArrayList()

        val cursor = mDb.query(TABLE_NAME, null, null, null, null, null, null)
        while (cursor.moveToNext()) {
            val task = DownloadTask(cursor.getString(1), cursor.getString(2))
            task.id = cursor.getString(0)
            task.setCurStatus(cursor.getInt(3))
            task.totalSize = cursor.getLong(4)
            task.createTime = cursor.getLong(5)
            task.completeTime = cursor.getLong(6)
            task.downloadedSize = mDownloadSp.getLong(task.id, 0)
            taskList.add(task)
        }
        cursor.close()

        return taskList
    }

    fun updateDownloadProgress(taskId: String, downloadedSize: Long) {
        mDownloadSp.edit().putLong(taskId, downloadedSize).apply()
    }

    fun updateTask(task: DownloadTask) {
        val values = ContentValues()
        values.put("file_path", task.filePath)
        values.put("status", task.getCurStatus().type)
        values.put("complete_time", task.completeTime)
        mDb.update(TABLE_NAME, values, "id=?", arrayOf(task.id))
    }

    fun createTask(url: String, filePath: String): DownloadTask {
        val task = DownloadTask(url, filePath)
        task.totalSize = requestFileSize(url)
        task.downloadedSize = 0
        task.createTime = System.currentTimeMillis()
        task.completeTime = -1
        task.setCurStatus(DownloadStatus.PAUSED)
        return task
    }

    private fun requestFileSize(url: String): Long {
        return try {
            val urlCon = URL(url).openConnection() as HttpURLConnection
            val fileLength = urlCon.contentLength
            urlCon.disconnect()
            fileLength.toLong()
        } catch (e: Exception) {
            -1
        }
    }

}

class DownloadDatabaseHelper(context: Context, name: String): SQLiteOpenHelper(context, name, null, 1) {

    companion object {
        val instance: DownloadDatabaseHelper by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            val config = YuDownloadManager.instance.mConfig
            DownloadDatabaseHelper(config.context, config.dbName)
        }
    }

    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL(
            "create table if not exists download_task (id PRIMARY KEY," +
                    "url, file_path, status, total_size, create_time, complete_time)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {

    }
}