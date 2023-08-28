package com.zj.screenrecorder.configs

import android.content.Context
import android.media.MediaCodecInfo
import android.util.Log
import android.util.Pair
import android.util.Range
import com.zj.screenrecorder.ScreenRecorder
import com.zj.screenrecorder.ann.ScreenOrientation
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

abstract class RecorderBuilder {

    open fun getFileName(): String {
        return SimpleDateFormat("yyyyMMdd-HH:mm:ss", Locale.US).format(Date())
    }

    abstract suspend fun onStartingRecord()
    abstract suspend fun onCompletedRecord(file: File?): Boolean
    abstract suspend fun onStopRecord(isCompleted: Boolean?)
    open fun onError(e: Throwable?) {

    }

    @ScreenOrientation
    abstract fun getScreenOrientation(): Int
    abstract fun getOutputPath(context: Context): String
    abstract fun micRecordEnable(): Boolean
    abstract fun getFps(supportedFrameRates: Range<Double>): Int
    abstract fun getVideoBitrate(supportedBitRates: Range<Int>): Int
    abstract fun getIFrameInterval(): Int
    abstract fun getVideoWidth(supportedWidths: Range<Int>): Int
    abstract fun getVideoHeight(supportedHeightForWidth: Range<Int>, width: Int): Int
    abstract fun getVideoCodec(codecInfo: Array<MediaCodecInfo>?): String?
    abstract fun getAudioBitrate(bitrateRange: Range<Int>): Int
    abstract fun getNotifyStringRecordingHint(): String
    abstract fun getNotifyStringRecordFlushHint(): String
    abstract fun getNotifyStringRecordingTime(time: Long): String
    abstract fun getAudioSampleRate(supportedSampleRates: IntArray): Int
    abstract fun getAudioChannelCount(): Int
    abstract fun getAudioCodec(codecInfo: Array<MediaCodecInfo>?): String?
    open fun getAudioProfileLevel(): Int {
        return MediaCodecInfo.CodecProfileLevel.AACObjectMain
    }

    open fun getVideoProfileLevel(): Pair<Int, Int>? {
        return null
    }

    internal fun stopRecordByError(case: String? = null, completed: Boolean, useRecord: Boolean = false) {
        ScreenRecorder.stopRecordService(case, completed, useRecord)
    }
}