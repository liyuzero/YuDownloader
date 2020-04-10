package com.yu.lib.downloader

import android.content.ContentValues
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File

internal class TaskInfoManager(config: YuDownloadConfig) {

    companion object {
        private const val TABLE_NAME = "download_task"
    }

    private val mDownloadDatabaseHelper = DownloadDatabaseHelper(config)
    private val mDb = mDownloadDatabaseHelper.writableDatabase
    private val mDownloadSp: SharedPreferences =
        YuDownloadManager.instance.mConfig.spFactory.getSp()

    //不保留被删除的文件下载记录
    fun getTaskList(): List<DownloadTask> {
        val taskList: MutableList<DownloadTask> = ArrayList()

        val cursor = mDb.query(Companion.TABLE_NAME, null, null, null, null, null, null)
        while (cursor.moveToNext()) {
            val task = DownloadTask(cursor.getString(1), cursor.getLong(4))
            task.id = cursor.getString(0)
            task.setCurStatus(cursor.getInt(3))
            task.createTime = cursor.getLong(5)
            task.completeTime = cursor.getLong(6)
            task.downloadedSize = mDownloadSp.getLong(task.id, 0)

            if (task.getCurStatus() == DownloadStatus.COMPLETE && !File(task.filePath).exists()) {
                mDb.delete(Companion.TABLE_NAME, "id=?", arrayOf(task.id))
            } else {
                taskList.add(task)
            }
        }
        cursor.close()

        return taskList
    }

    fun saveDownloadProgress(taskId: String, downloadedSize: Long) {
        mDownloadSp.edit().putLong(taskId, downloadedSize).apply()
    }

    fun updateTaskStatus(task: DownloadTask) {
        val values = ContentValues()
        values.put("status", task.getCurStatus().type)
        mDb.update(TABLE_NAME, values, "id=?", arrayOf(task.id))
    }

    fun insertOrUpdateTask(task: DownloadTask) {
        val cursor = mDb.rawQuery("select count(*) from " + TABLE_NAME + " where id='" + task.id + "'", null)
        cursor.moveToNext()
        val isInsert = cursor.getLong(0) == 0L

        val values = ContentValues()
        values.put("url", task.url)
        values.put("total_size", task.totalSize)
        values.put("id", task.id)
        values.put("status", task.getCurStatus().type)
        values.put("file_path", task.filePath)
        values.put("create_time", task.createTime)
        values.put("complete_time", task.completeTime)
        if (isInsert) {
            mDb.insert(TABLE_NAME, "id", values)
        } else {
            mDb.update(Companion.TABLE_NAME, values, "id=?", arrayOf(task.id))
        }
    }

    fun deleteTask(task: DownloadTask) {
        mDb.delete(TABLE_NAME, "id=?", arrayOf(task.id))
        mDownloadSp.edit().remove(task.id).apply()
    }

    fun onDestroy() {
        mDownloadDatabaseHelper.close()
    }
}

private class DownloadDatabaseHelper(config: YuDownloadConfig) : SQLiteOpenHelper(
    config.context, config.dbName,
    null, 1
) {

    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL(
            "create table if not exists download_task (id PRIMARY KEY," +
                    "url, file_path, status, total_size, create_time, complete_time)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {

    }
}