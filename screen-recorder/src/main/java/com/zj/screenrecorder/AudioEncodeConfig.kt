package com.zj.screenrecorder

import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.AudioCapabilities
import android.media.MediaFormat
import com.zj.screenrecorder.recorder.RecorderBuilder
import com.zj.screenrecorder.recorder.Utils
import com.zj.screenrecorder.recorder.Utils.AUDIO_AAC
import java.util.*

/**
 * @author Zjj
 * @version 2022/4/13
 */
internal class AudioEncodeConfig(mimeType: String, builder: RecorderBuilder, onCrated: (Array<MediaCodecInfo>?) -> String?, consumer: (Boolean) -> Unit) {

    private var mimeType: String = ""
    private var bitRate = 0
    private var profile = 0
    @JvmField var codecName: String? = null
    @JvmField var sampleRate = 0
    @JvmField var channelCount = 0
    private var mAacCodecInfo: Array<MediaCodecInfo>? = null

    init {
        Utils.findEncodersByTypeAsync(AUDIO_AAC) { codecs: Array<MediaCodecInfo>? ->
            if (codecs.isNullOrEmpty()) {
                codecName = onCrated(null)
            } else {
                try {
                    Utils.logCodecInfo(codecs, AUDIO_AAC)
                    mAacCodecInfo = codecs
                    codecName = onCrated(codecs)
                    this.mimeType = Objects.requireNonNull(mimeType)
                    bitRate = getAudioBitRates(builder)
                    sampleRate = getAudioSampleRates(builder)
                    channelCount = builder.getAudioChannelCount()
                    profile = builder.getAudioProfileLevel()
                    consumer(true)
                } catch (e: Exception) {
                    e.printStackTrace()
                    consumer(false)
                }
            }
        }
    }

    private fun getAudioSampleRates(builder: RecorderBuilder): Int {
        val ac = audioCapability ?: return 0
        return builder.getAudioSampleRate(ac.supportedSampleRates)
    }

    private fun getAudioBitRates(builder: RecorderBuilder): Int {
        val capabilities = audioCapability ?: return 0
        return builder.getAudioBitrate(capabilities.bitrateRange)
    }

    private val audioCapability: AudioCapabilities?
        get() {
            val codec = audioCodecInfo ?: return null
            return codec.getCapabilitiesForType(AUDIO_AAC).audioCapabilities
        }
    private val audioCodecInfo: MediaCodecInfo?
        get() {
            if (codecName == null) return null
            if (mAacCodecInfo == null) {
                mAacCodecInfo = Utils.findEncodersByType(AUDIO_AAC)
            }
            var codec: MediaCodecInfo? = null
            for (info in mAacCodecInfo ?: return null) {
                if (info.name == codecName) {
                    codec = info
                    break
                }
            }
            return codec
        }

    fun toFormat(): MediaFormat {
        val format = MediaFormat.createAudioFormat(mimeType, sampleRate, channelCount)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, profile)
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        return format
    }

    override fun toString(): String {
        return "AudioEncodeConfig{codecName='$codecName', mimeType='$mimeType', bitRate=$bitRate, sampleRate=$sampleRate, channelCount=$channelCount, profile=$profile}"
    }
}