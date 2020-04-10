package com.yu.lib.common.demo

import android.annotation.SuppressLint
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.tencent.mmkv.MMKV
import com.yu.lib.common.bundles.monitor.MAEMonitorFragment
import com.yu.lib.common.bundles.monitor.MAEPermissionCallback
import com.yu.lib.common.ui.adapter.single.BaseSingleViewHolder
import com.yu.lib.common.ui.adapter.single.SingleTypeAdapter
import com.yu.lib.common.utils.ToastUtil
import com.yu.lib.downloader.*
import com.yu.lib.downloader.demo.R
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        MMKV.initialize(application)

        YuDownloadManager.init(YuDownloadConfig(application, "yu_download_info_db",
            Environment.getExternalStorageDirectory().absolutePath, 60, object : SpFactory {
            override fun getSp(): SharedPreferences {
                return MMKV.mmkvWithID("yu_download_info_sp")
            }

        }))

        val downloadingList = YuDownloadManager.instance.getDownloadingList()
        val downloadCompleteList = YuDownloadManager.instance.getDownloadCompleteList()

        downloadingRecyclerView.layoutManager = LinearLayoutManager(this)
        val adapter = DownloadAdapter(R.layout.item, downloadingList)
        adapter.setOnItemClickListener(object : SingleTypeAdapter.OnItemClickListener{
            override fun onItemClick(view: View, position: Int) {
                YuDownloadManager.instance.dispatchStartOrPauseTask(downloadingList[position])
            }

        })
        downloadingRecyclerView.adapter = adapter

        completeRecyclerView.layoutManager = LinearLayoutManager(this)
        val completeAdapter = DownloadAdapter(R.layout.item, downloadCompleteList)
        completeAdapter.setOnItemClickListener(object : SingleTypeAdapter.OnItemClickListener{
            override fun onItemClick(view: View, position: Int) {
                YuDownloadManager.instance.dispatchStartOrPauseTask(downloadCompleteList[position])
            }

        })
        completeAdapter.setOnItemLongClickListener(object : SingleTypeAdapter.OnItemLongClickListener{
            override fun onItemLongClick(view: View, position: Int): Boolean {
                val task = downloadCompleteList[position]

                AlertDialog.Builder(view.context)
                    .setTitle("确定删除吗？")
                    .setMessage(task.filePath.substring(task.filePath.lastIndexOf("/") + 1))
                    .setPositiveButton("确认") { dialogInterface, _: Int ->
                        dialogInterface.dismiss()
                        YuDownloadManager.instance.deleteTask(task, true)
                        downloadCompleteList.remove(task)
                        completeAdapter.notifyItemRemoved(position)
                    }
                    .setNegativeButton("取消") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
                return true
            }

        })
        completeRecyclerView.adapter = completeAdapter

        for(task in downloadingList) {
            task.addDownloadListener(this@MainActivity,
                Observer {
                    if(it.getCurStatus() == DownloadStatus.COMPLETE || it.getCurStatus() == DownloadStatus.COMPLETE_FILE_NOT_EXIST) {
                        downloadingList.remove(it)
                        downloadingRecyclerView.adapter?.notifyItemRemoved(downloadingList.indexOf(it))
                        downloadCompleteList.add(0, it)
                        completeRecyclerView.adapter?.notifyItemInserted(0)
                    } else {
                        downloadingRecyclerView.adapter?.notifyItemChanged(downloadingList.indexOf(it))
                        Log.d("hehe", "当前状态：" + task.getCurStatus().message + " == " + task.downloadedSize + " - " + task.totalSize + " == " + downloadingList.indexOf(it))
                    }
                })
        }

        val clickView = findViewById<View>(R.id.click)
        clickView.setOnClickListener {
            MAEMonitorFragment.getInstance(this).requestPermission(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                object : MAEPermissionCallback{
                    override fun onPermissionApplySuccess() {
                        YuDownloadManager.instance.createAndInitTask("http://img3.doubanio.com/dae/a" +
                                "ndariel/static/upload/com.douban.book.reader_douban_5.11.1.1_212.apk", object : OnYuDownloadListener{
                            override fun createSuccess(task: DownloadTask?) {
                                if(task?.getCurStatus() == DownloadStatus.COMPLETE) {
                                    Toast.makeText(this@MainActivity, "已经下载完成", Toast.LENGTH_SHORT).show()
                                } else {
                                    if(task != null) {
                                        if(downloadingList.contains(task)) {
                                            clickView.post {
                                                ToastUtil.showToast(clickView.context,"下载队列包含")
                                            }
                                            return
                                        }

                                        if(downloadCompleteList.contains(task)) {
                                            clickView.post {
                                                ToastUtil.showToast(clickView.context,"完成队列包含")
                                            }
                                            return
                                        }

                                        Log.d("hehe", task.filePath + " == " + task.totalSize)

                                        clickView.post {
                                            AlertDialog.Builder(this@MainActivity)
                                                .setTitle("下载文件")
                                                .setMessage("名字" + task.filePath + "\n大小：" + task.getFileSizeDes())
                                                .setPositiveButton("确认"
                                                ) { _, _ ->
                                                    downloadingList.add(0, task)
                                                    downloadingRecyclerView.adapter?.notifyItemInserted(0)
                                                    YuDownloadManager.instance.enqueueTask(task)

                                                    task.addDownloadListener(this@MainActivity,
                                                        Observer {
                                                            if(it.getCurStatus() == DownloadStatus.COMPLETE || it.getCurStatus() == DownloadStatus.COMPLETE_FILE_NOT_EXIST) {
                                                                downloadingList.remove(it)
                                                                downloadingRecyclerView.adapter?.notifyItemRemoved(downloadingList.indexOf(it))
                                                                downloadCompleteList.add(0, it)
                                                                completeRecyclerView.adapter?.notifyItemInserted(0)
                                                            } else {
                                                                downloadingRecyclerView.adapter?.notifyItemChanged(downloadingList.indexOf(it))
                                                                Log.d("hehe", "当前状态：" + task.getCurStatus().message + " == " + task.downloadedSize + " - " + task.totalSize + " == " + downloadingList.indexOf(it))
                                                            }
                                                        })
                                                }
                                                .setNegativeButton("取消"
                                                ) { dialog, _ ->
                                                    dialog.dismiss()
                                                }
                                                .show()
                                        }

                                    } else {
                                        Log.d("hehe", "创建失败");
                                    }
                                }
                            }

                        })
                    }

                    override fun onPermissionApplyFailure(
                        notGrantedPermissions: MutableList<String>?,
                        shouldShowRequestPermissions: MutableList<Boolean>?
                    ) {

                    }

                })
        }
    }
}

class DownloadAdapter(mLayoutRes: Int,
                      mData: MutableList<DownloadTask>
) : SingleTypeAdapter<DownloadTask>(mLayoutRes, mData) {

    @SuppressLint("SetTextI18n")
    override fun onBindData(holder: BaseSingleViewHolder, data: DownloadTask, position: Int) {
        val progressBar = holder.getView<ProgressBar>(R.id.progress)
        progressBar?.progress = (data.downloadedSize / data.totalSize.toFloat() * 100).toInt()

        val textView = holder.getView<TextView>(R.id.info)
        textView?.text = data.getCurStatus().message + "\n" + data.filePath.substring((data.filePath.lastIndexOf("/") + 1 ))
    }

}
