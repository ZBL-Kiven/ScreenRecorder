@file:Suppress("unused")

package com.zj.screenrecorder

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.zj.screenrecorder.configs.RecorderBuilder
import com.zj.screenrecorder.recorder.Recorder

object ScreenRecorder {

    private var connectionService: Recorder? = null
    private var serviceConn: RecorderServiceConn? = null
    private var isServiceConnected = false
    private lateinit var appContext: Context

    fun startRecord(context: Context, builder: RecorderBuilder) {
        this.appContext = context.applicationContext
        val pid = ProcessUtil.getCurrentProcessName(appContext)
        val packageName = appContext.applicationContext.packageName
        if (pid != packageName) {
            Log.e("BaseRecorder", "initService: unable to start im service , please call it in your app main process!")
        } else {
            serviceConn = RecorderServiceConn(builder)
            try {
                serviceConn?.let {
                    appContext.bindService(Intent(appContext, Recorder::class.java), it, Service.BIND_AUTO_CREATE)
                }
            } catch (e: Exception) {
                Log.e("BaseRecorder", "initService: unable to start im service , case: ${e.message}")
            }
        }
    }

    internal fun stopRecordService(case: String? = null, completed: Boolean = false, callBack: Boolean = false) {
        if (::appContext.isInitialized && connectionService != null && isServiceConnected && serviceConn != null) {
            connectionService?.release(case, completed, callBack)
            serviceConn?.let { appContext.unbindService(it) }
        } else if (case.isNullOrEmpty().not()) {
            Log.e("BaseRecorder", "stopRecord: the record are stopped by Exception : $case")
        } else {
            Log.e("BaseRecorder", "stopRecord: seems you're never success to start a recorder!")
        }
        connectionService = null
        isServiceConnected = false
        serviceConn = null
    }

    fun stopRecord() {
        stopRecordService()
    }

    private class RecorderServiceConn(private val builder: RecorderBuilder) : ServiceConnection {

        override fun onServiceDisconnected(name: ComponentName?) {
            isServiceConnected = false
            connectionService?.release("on service disconnected!", completed = false, callBack = true)
        }

        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder !is Recorder.ConnectionBinder) return
            connectionService = binder.service
            isServiceConnected = true
            connectionService?.onServiceConnected(builder)
        }
    }
}