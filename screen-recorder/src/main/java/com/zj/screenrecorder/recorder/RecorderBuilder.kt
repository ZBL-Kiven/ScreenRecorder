package com.zj.screenrecorder.recorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodecInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Pair
import android.util.Range
import com.zj.screenrecorder.AudioEncodeConfig
import com.zj.screenrecorder.VideoEncodeConfig
import com.zj.screenrecorder.ann.ScreenOrientation
import com.zj.screenrecorder.recorder.RecorderService.REQUEST_CANCEL
import com.zj.screenrecorder.recorder.Utils.AUDIO_AAC
import com.zj.screenrecorder.recorder.Utils.VIDEO_AVC
import com.zj.screenrecorder.utils.FileUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author Zjj
 * @version 2022/4/14
 */
abstract class RecorderBuilder {

    protected open fun getFileName(): String {
        return SimpleDateFormat("yyyyMMdd-HH:mm:ss", Locale.US).format(Date())
    }

    protected abstract suspend fun onStartingRecord()
    protected abstract suspend fun onCompletedRecord(file: File?): Boolean
    protected abstract suspend fun onStopRecord(isCompleted: Boolean?)

    @ScreenOrientation
    abstract fun getScreenOrientation(): Int
    protected abstract fun getOutputPath(context: Context): String
    protected abstract fun micRecordEnable(): Boolean
    abstract fun getFps(supportedFrameRates: Range<Double>): Int
    abstract fun getVideoBitrate(supportedBitRates: Range<Int>): Int
    abstract fun getIFrameInterval(): Int
    abstract fun getVideoWidth(supportedWidths: Range<Int>): Int
    abstract fun getVideoHeight(supportedHeightForWidth: Range<Int>, width: Int): Int
    protected abstract fun getVideoCodec(codecInfo: Array<MediaCodecInfo>?): String?
    abstract fun getAudioBitrate(bitrateRange: Range<Int>): Int
    abstract fun getAudioSampleRate(supportedSampleRates: IntArray): Int
    abstract fun getAudioChannelCount(): Int
    protected abstract fun getAudioCodec(codecInfo: Array<MediaCodecInfo>?): String?
    open fun getAudioProfileLevel(): Int {
        return MediaCodecInfo.CodecProfileLevel.AACObjectMain
    }

    open fun getVideoProfileLevel(): Pair<Int, Int>? {
        return null
    }

    private var mNotifications: Notifications? = null
    private var mRecorder: ScreenRecorder? = null
    private var mMediaProjection: MediaProjection? = null
    private var mVirtualDisplay: VirtualDisplay? = null
    private var onCodecInfoCreated: AtomicBoolean? = null
    private var outPutFilePath: String? = null
    private lateinit var application: Context
    private lateinit var videoConfig: VideoEncodeConfig
    private lateinit var audioConfig: AudioEncodeConfig

    var isRecording = false; private set
    private var canceled = false
    private var mMediaProjectionManager: MediaProjectionManager? = null
    private val mStopActionReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                REQUEST_CANCEL -> isRecording = false
                REQUEST_MEDIA_PROJECTION_ID -> if (canceled) {
                    stopRecorder(completed = false)
                } else {
                    val rq = intent.getIntExtra("resultCode", 0)
                    val i = intent.getParcelableExtra<Intent>("captureIntent")
                    onMediaProjectionPatched(context, rq, i)
                }
                ACTION_STOP -> stopRecorder(completed = false)
            }
        }
    }
    private val mProjectionCallback: MediaProjection.Callback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopRecorder(completed = false)
        }
    }

    /**
     * call to start
     */
    fun startRecord(c: Context) {
        if (isRecording) {
            return
        }
        application = c.applicationContext
        canceled = false
        isRecording = true
        mMediaProjectionManager = application.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        val captureIntent = mMediaProjectionManager?.createScreenCaptureIntent()
        RecorderService.start(c, micRecordEnable(), captureIntent)
        val filter = IntentFilter()
        filter.addAction(ACTION_STOP)
        filter.addAction(REQUEST_CANCEL)
        filter.addAction(REQUEST_MEDIA_PROJECTION_ID)
        application.registerReceiver(mStopActionReceiver, filter)
    }

    /**
     * call to stop
     */
    fun stopRecord() {
        if (!::application.isInitialized) return
        if (isRecording) {
            canceled = true
        }
        stopRecorder(completed = false)
    }

    private fun onMediaProjectionPatched(context: Context, rq: Int, data: Intent?) {
        if (data == null) {
            Log.e(TAG, "media projection manager request but a null")
            return
        }
        mNotifications = Notifications(context.applicationContext)
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
                stopRecorder(completed = true)
            } else {
                startCapturing(mMediaProjection)
            }
        }
    }

    private fun startCapturing(mediaProjection: MediaProjection?) {
        val dir = File(getOutputPath(application))
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "Cannot start recording , the output dir is unreachable!")
            cancelRecorder()
            return
        }
        val file = File(dir, "${getFileName()}.mp4")
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
                stopRecorder(completed = error == null, useRecord = true)
                if (error != null) {
                    Log.e(TAG, "onStop: Recorder error ! See logcat for more details")
                    error.printStackTrace()
                    if (output.delete()) Log.e(TAG, "onStop: the error file has been deleted")
                }
            }

            override fun onStart() {
                mNotifications?.recording(0)
            }

            override fun onRecording(presentationTimeUs: Long) {
                if (startTime <= 0) {
                    startTime = presentationTimeUs
                }
                val time = (presentationTimeUs - startTime) / 1000
                mNotifications?.recording(time)
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
        return AudioEncodeConfig(AUDIO_AAC, this, { ci -> getAudioCodec(ci) }, onCrated)
    }

    private fun createVideoConfig(onCrated: (Boolean) -> Unit): VideoEncodeConfig {
        return VideoEncodeConfig(VIDEO_AVC, this, { ci -> getVideoCodec(ci) }, onCrated)
    }

    private fun startRecorder() {
        mRecorder?.run { start();postToMain { onStartingRecord() } }
    }

    private fun stopRecorder(case: String? = null, completed: Boolean, useRecord: Boolean = false) {
        isRecording = false
        mNotifications?.clear()
        mRecorder?.quit()
        mRecorder = null
        postToMain {
            onStopRecord(completed)
        }
        kotlin.runCatching {
            application.unregisterReceiver(mStopActionReceiver)
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
        if (useRecord) outPutFilePath?.let {
            val file = File(it)
            postToMain {
                if (onCompletedRecord(file)) {
                    videoConfig.run {
                        FileUtils.notifyVideosToAlbum(application, file.absolutePath, mimeType) { b ->
                            Log.e(TAG, String.format("%s to save to album!", if (b) "success" else "failed"))
                        }
                    }
                }
            }
            outPutFilePath = null
        }
    }

    private fun cancelRecorder() {
        if (mRecorder == null) return
        stopRecorder(completed = false)
    }

    private fun <T> T.postToMain(r: suspend T.() -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            r.invoke(this@postToMain)
        }
    }

    companion object {
        private const val TAG = "RecorderBuilder"
        const val ACTION_STOP = ".action.STOP"
        const val REQUEST_MEDIA_PROJECTION_ID = ".action.ProjectionRequest"
    }
}