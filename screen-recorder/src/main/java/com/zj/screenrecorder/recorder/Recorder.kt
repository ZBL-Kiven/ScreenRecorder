package com.zj.screenrecorder.recorder

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.zj.screenrecorder.configs.AudioEncodeConfig
import com.zj.screenrecorder.configs.RecorderBuilder
import com.zj.screenrecorder.configs.VideoEncodeConfig
import com.zj.screenrecorder.recorder.RecorderAct.REQUEST_CANCEL
import com.zj.screenrecorder.recorder.Utils.AUDIO_AAC
import com.zj.screenrecorder.recorder.Utils.VIDEO_AVC
import com.zj.screenrecorder.utils.FileUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author Zjj
 * @version 2022/4/14
 */
internal class Recorder : Service() {

    companion object {
        private const val TAG = "RecorderService"
        const val ACTION_STOP = ".action.STOP"
        const val REQUEST_MEDIA_PROJECTION_ID = ".action.ProjectionRequest"
    }

    data class ConnectionBinder(val service: Recorder) : Binder()

    override fun onBind(intent: Intent?): IBinder {
        return ConnectionBinder(this@Recorder)
    }

    private var canceled = false
    private var isRecording = false
    private var outPutFilePath: String? = null
    private var mRecorder: ScreenRecorder? = null
    private var mNotifications: Notifications? = null
    private var mVirtualDisplay: VirtualDisplay? = null
    private var onCodecInfoCreated: AtomicBoolean? = null
    private var mMediaProjection: MediaProjection? = null
    private lateinit var builder: RecorderBuilder
    private lateinit var videoConfig: VideoEncodeConfig
    private lateinit var audioConfig: AudioEncodeConfig
    private var mMediaProjectionManager: MediaProjectionManager? = null

    private val mStopActionReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                REQUEST_CANCEL -> stopRecorder("Recorder request context has been canceled!")
                REQUEST_MEDIA_PROJECTION_ID -> if (canceled) {
                    stopRecorder("Record already canceled!")
                } else {
                    val rq = intent.getIntExtra("resultCode", 0)
                    val i = intent.getParcelableExtra<Intent>("captureIntent")
                    onMediaProjectionPatched(rq, i)
                }
                ACTION_STOP -> stopRecorder()
            }
        }
    }
    private val mProjectionCallback: MediaProjection.Callback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopRecorder(completed = false)
        }
    }

    fun onServiceConnected(builder: RecorderBuilder) {
        this.builder = builder
        startRecord()
    }

    /**
     * call to start
     */
    private fun startRecord() {
        if (isRecording) {
            return
        }
        canceled = false
        isRecording = true
        mNotifications = Notifications(this, builder.getNotifyStringRecordingHint(), builder.getNotifyStringRecordFlushHint())
        mNotifications?.recording(this, builder.getNotifyStringRecordingTime(0))
        mMediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        val captureIntent = mMediaProjectionManager?.createScreenCaptureIntent()
        RecorderAct.start(this, builder.micRecordEnable(), captureIntent)
        val filter = IntentFilter()
        filter.addAction(ACTION_STOP)
        filter.addAction(REQUEST_CANCEL)
        filter.addAction(REQUEST_MEDIA_PROJECTION_ID)
        registerReceiver(mStopActionReceiver, filter)
    }

    private fun onMediaProjectionPatched(rq: Int, data: Intent?) {
        if (rq != Activity.RESULT_OK) {
            stopRecorder("failed to request media projection!")
            return
        }
        if (data == null) {
            Log.e(TAG, "media projection manager request but a null")
            return
        }
        val mediaProjection = mMediaProjectionManager?.getMediaProjection(rq, data)
        if (mediaProjection == null) {
            Log.e(TAG, "media projection is null")
            return
        }
        mMediaProjection = mediaProjection
        mMediaProjection?.registerCallback(mProjectionCallback, Handler(Looper.getMainLooper()))
        onCodecInfoCreated = null
        videoConfig = createVideoConfig { checkOrStart(it) }
        audioConfig = createAudioConfig { checkOrStart(it) }
    }

    private fun checkOrStart(b: Boolean) {
        if (!b) {
            stopRecorder("cannot create config!", false)
        } else if (onCodecInfoCreated == null) {
            onCodecInfoCreated = AtomicBoolean(false)
        } else if (onCodecInfoCreated?.get() == false) {
            onCodecInfoCreated?.set(true)
            if (mRecorder != null) {
                stopRecorder("there is a recording task running")
            } else {
                startCapturing(mMediaProjection)
            }
        }
    }

    private fun startCapturing(mediaProjection: MediaProjection?) {
        val dir = File(builder.getOutputPath(application))
        if (!dir.exists() && !dir.mkdirs()) {
            stopRecorder("Cannot start recording , the output dir is unreachable!")
            return
        }
        val file = File(dir, "${builder.getFileName()}.mp4")
        outPutFilePath = file.path
        mRecorder = newRecorder(mediaProjection, videoConfig, audioConfig, file)
        startRecorder()
    }

    private fun newRecorder(mediaProjection: MediaProjection?, video: VideoEncodeConfig, audio: AudioEncodeConfig?, output: File): ScreenRecorder {
        val display = getOrCreateVirtualDisplay(mediaProjection, video)
        val r = ScreenRecorder(video, audio, display, output.absolutePath)
        r.setCallback(object : ScreenRecorder.Callback {
            var startTime: Long = 0
            override fun onStop(error: Throwable?) {
                release(completed = error == null, callBack = true)
                if (error != null) {
                    Log.e(TAG, "onStop: Recorder error ! See logcat for more details")
                    error.printStackTrace()
                    if (output.delete()) Log.e(TAG, "onStop: the error file has been deleted")
                }
            }

            override fun onStart() {
                mNotifications?.recording(this@Recorder, builder.getNotifyStringRecordingTime(0))
            }

            override fun onRecording(presentationTimeUs: Long) {
                if (startTime <= 0) {
                    startTime = presentationTimeUs
                }
                val time = (presentationTimeUs - startTime) / 1000
                mNotifications?.recording(this@Recorder, builder.getNotifyStringRecordingTime(time))
            }
        })
        return r
    }

    private fun getOrCreateVirtualDisplay(mediaProjection: MediaProjection?, config: VideoEncodeConfig): VirtualDisplay? {
        if (mVirtualDisplay == null) {
            mVirtualDisplay = mediaProjection?.createVirtualDisplay("ScreenRecorder-display0", config.width, config.height, 1, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, null, null, null)
        } else {
            val sizeLower = Point()
            val sizeUpper = Point()
            mVirtualDisplay?.display?.getCurrentSizeRange(sizeLower, sizeUpper)
            val widthSuite = (sizeLower.x..sizeUpper.x).contains(config.width)
            val heightSuite = (sizeLower.x..sizeUpper.x).contains(config.width)
            if (!widthSuite || !heightSuite) {
                mVirtualDisplay?.resize(config.width, config.height, 1)
            }
        }
        return mVirtualDisplay
    }

    private fun createAudioConfig(onCrated: (Boolean) -> Unit): AudioEncodeConfig {
        return AudioEncodeConfig(AUDIO_AAC, builder, { ci -> builder.getAudioCodec(ci) }, onCrated)
    }

    private fun createVideoConfig(onCrated: (Boolean) -> Unit): VideoEncodeConfig {
        return VideoEncodeConfig(VIDEO_AVC, builder, { ci -> builder.getVideoCodec(ci) }, onCrated)
    }

    private fun startRecorder() {
        mRecorder?.run { start();postToMain { builder.onStartingRecord() } }
    }

    private fun stopRecorder(case: String? = null, completed: Boolean = false, callBack: Boolean = false) {
        if (!isRecording) return else {
            canceled = true
        }
        builder.stopRecordByError(case, completed, callBack)
    }

    internal fun release(case: String? = null, completed: Boolean, callBack: Boolean = false) {
        kotlin.runCatching {
            stopForeground(true)
        }
        isRecording = false
        mNotifications?.clear(this@Recorder)
        mRecorder?.quit()
        mRecorder = null
        kotlin.runCatching {
            unregisterReceiver(mStopActionReceiver)
        }
        mVirtualDisplay?.surface = null
        mVirtualDisplay?.release()
        mVirtualDisplay = null
        kotlin.runCatching {
            mMediaProjection?.unregisterCallback(mProjectionCallback)
        }
        mMediaProjection?.stop()
        mMediaProjection = null
        if (case.isNullOrEmpty().not()) Log.e(TAG, "stopRecording case: $case")
        if (callBack) {
            outPutFilePath?.let {
                val file = File(it)
                postToMain {
                    if (builder.onCompletedRecord(file)) {
                        videoConfig.run {
                            FileUtils.notifyVideosToAlbum(application, file.absolutePath, mimeType) { b ->
                                Log.e(TAG, String.format("%s to save to album!", if (b) "success" else "failed"))
                            }
                        }
                    }
                }
                outPutFilePath = null
            }
            postToMain {
                builder.onStopRecord(completed)
            }
        }
    }

    private fun <T> T.postToMain(r: suspend T.() -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            r.invoke(this@postToMain)
        }
    }

    override fun onDestroy() {
        stopRecorder("service destroyed", false)
        super.onDestroy()
    }
}