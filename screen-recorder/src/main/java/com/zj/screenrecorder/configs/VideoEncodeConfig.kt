package com.zj.screenrecorder.configs

import android.content.res.Configuration
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecInfo.VideoCapabilities
import android.media.MediaFormat
import android.os.Build
import android.os.Build.VERSION_CODES.M
import com.zj.screenrecorder.recorder.Utils
import com.zj.screenrecorder.recorder.Utils.VIDEO_AVC
import java.util.*

/**
 * @author Zjj
 * @version 2022/4/13
 */
internal class VideoEncodeConfig(mimeType: String, builder: RecorderBuilder, onCrated: (Array<MediaCodecInfo>?) -> String?, consumer: (Boolean) -> Unit) {

    @JvmField var codecName: String? = null
    var width = 0
    var height = 0
    var mimeType: String = ""
    private var bitrate = 0
    private var frameRate = 0
    private var iframeInterval = 0
    private var codecProfileLevel: CodecProfileLevel? = null
    private var mAvcCodecInfo: Array<MediaCodecInfo>? = null

    init {
        this.mimeType = Objects.requireNonNull(mimeType)
        Utils.findEncodersByTypeAsync(VIDEO_AVC) { codecs: Array<MediaCodecInfo>? ->
            if (codecs.isNullOrEmpty()) {
                codecName = onCrated(null)
            } else {
                try {
                    codecName = onCrated(codecs)
                    Utils.logCodecInfo(codecs, VIDEO_AVC)
                    mAvcCodecInfo = codecs
                    val selectedWithHeight = getVideoSize(builder)
                    val isLandscape = builder.getScreenOrientation() == Configuration.ORIENTATION_LANDSCAPE
                    width = selectedWithHeight[if (isLandscape) 0 else 1]
                    height = selectedWithHeight[if (isLandscape) 1 else 0]
                    frameRate = getVideoFrameRate(builder, selectedWithHeight)
                    iframeInterval = builder.getIFrameInterval()
                    bitrate = getVideoBitrate(builder)
                    var level: CodecProfileLevel? = null
                    val pair = builder.getVideoProfileLevel()
                    if (pair != null) {
                        level = CodecProfileLevel()
                        level.profile = pair.first
                        level.level = pair.second
                    }
                    codecProfileLevel = level
                    consumer(true)
                } catch (e: Exception) {
                    e.printStackTrace()
                    consumer(false)
                }
            }
        }
    }

    private fun getVideoSize(builder: RecorderBuilder): IntArray {
        val vc = videoCapability ?: return intArrayOf(1920, 1080)
        val w = builder.getVideoWidth(vc.supportedWidths)
        val h = builder.getVideoHeight(vc.getSupportedHeightsFor(w), w)
        return intArrayOf(w, h)
    }

    private fun getVideoBitrate(builder: RecorderBuilder): Int {
        val vc = videoCapability ?: return 0
        return builder.getVideoBitrate(vc.bitrateRange)
    }

    private fun getVideoFrameRate(builder: RecorderBuilder, selectedWithHeight: IntArray): Int {
        val vc = videoCapability
        val w: Int
        val h: Int
        if (builder.getScreenOrientation() == Configuration.ORIENTATION_LANDSCAPE) {
            w = selectedWithHeight[1]
            h = selectedWithHeight[0]
        } else {
            w = selectedWithHeight[0]
            h = selectedWithHeight[1]
        }
        val fps = vc?.getSupportedFrameRatesFor(w, h) ?: return 0
        return builder.getFps(fps)
    }

    private val videoCodecInfo: MediaCodecInfo?
        get() {
            if (codecName == null) return null
            if (mAvcCodecInfo == null) {
                mAvcCodecInfo = Utils.findEncodersByType(VIDEO_AVC)
            }
            var codec: MediaCodecInfo? = null
            for (info in mAvcCodecInfo ?: return null) {
                if (info.name == codecName) {
                    codec = info
                    break
                }
            }
            return codec
        }
    private val videoCapability: VideoCapabilities?
        get() {
            val codec = videoCodecInfo ?: return null
            return codec.getCapabilitiesForType(VIDEO_AVC).videoCapabilities
        }

    fun toFormat(): MediaFormat {
        val format = MediaFormat.createVideoFormat(mimeType, width, height)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iframeInterval)
        codecProfileLevel?.let {
            if (it.profile != 0 && it.level != 0) {
                format.setInteger(MediaFormat.KEY_PROFILE, it.profile)
                if (Build.VERSION.SDK_INT >= M) {
                    format.setInteger(MediaFormat.KEY_LEVEL, it.level)
                }
            }
        }
        format.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 20000)
        return format
    }

    override fun toString(): String {
        try {
            return "VideoEncodeConfig{" + "width=" + width + ", height=" + height + ", bitrate=" + bitrate + ", frameRate=" + frameRate + ", iframeInterval=" + iframeInterval + ", codecName='" + codecName + '\'' + ", mimeType='" + mimeType + '\'' + ", codecProfileLevel=" + (if (codecProfileLevel == null) "" else Utils.avcProfileLevelToString(codecProfileLevel)) + '}'
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return super.toString()
    }
}