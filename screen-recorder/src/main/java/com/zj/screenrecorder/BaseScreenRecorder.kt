package com.zj.screenrecorder

import android.content.Context
import android.content.res.Configuration
import android.media.MediaCodecInfo
import android.util.Range
import com.zj.screenrecorder.recorder.RecorderBuilder
import java.io.File
import java.math.BigDecimal

abstract class BaseScreenRecorder : RecorderBuilder() {

    override fun getOutputPath(context: Context): String {
        return File(context.cacheDir, "/screen-shot").path
    }

    override fun micRecordEnable(): Boolean {
        return true
    }

    override fun getScreenOrientation(): Int {
        return Configuration.ORIENTATION_PORTRAIT
    }

    override fun getFps(supportedFrameRates: Range<Double>): Int {
        return BigDecimal.valueOf(supportedFrameRates.upper).toInt()
    }

    override fun getVideoBitrate(supportedBitRates: Range<Int>): Int {
        return supportedBitRates.upper
    }

    override fun getIFrameInterval(): Int {
        return 1
    }

    override fun getVideoWidth(supportedWidths: Range<Int>): Int {
        return 1080
    }

    override fun getVideoHeight(supportedHeightForWidth: Range<Int>, width: Int): Int {
        return 1920
    }

    override fun getVideoCodec(codecInfo: Array<MediaCodecInfo>?): String? {
        return codecInfo?.firstOrNull()?.name
    }

    override fun getAudioBitrate(bitrateRange: Range<Int>): Int {
        return bitrateRange.upper
    }

    override fun getAudioSampleRate(supportedSampleRates: IntArray): Int {
        return supportedSampleRates.lastOrNull() ?: 44100
    }

    override fun getAudioChannelCount(): Int {
        return 1
    }

    override fun getAudioCodec(codecInfo: Array<MediaCodecInfo>?): String? {
        return codecInfo?.get(0)?.name
    }
}