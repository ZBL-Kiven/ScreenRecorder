package com.zj.screenrecorder.recorder

import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Log
import android.util.SparseArray
import java.lang.reflect.Modifier
import java.util.*
import java.util.concurrent.Executors

internal object Utils {

    private var sAACProfiles = SparseArray<String>()
    private var sAVCProfiles = SparseArray<String>()
    private var sAVCLevels = SparseArray<String>()

    const val VIDEO_AVC = MediaFormat.MIMETYPE_VIDEO_AVC // H.264 Advanced Video Coding
    const val AUDIO_AAC = MediaFormat.MIMETYPE_AUDIO_AAC // H.264 Advanced Audio Coding

    var sColorFormats = SparseArray<String>()

    fun findEncodersByTypeAsync(mimeType: String?, callback: (Array<MediaCodecInfo>?) -> Unit) {
        Executors.newSingleThreadExecutor().execute { callback(findEncodersByType(mimeType)) }
    }

    /**
     * Find an encoder supported specified MIME type
     *
     * @return Returns empty array if not found any encoder supported specified MIME type
     */
    fun findEncodersByType(mimeType: String?): Array<MediaCodecInfo> {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        val mediaCodecs: MutableList<MediaCodecInfo> = ArrayList()
        for (info in codecList.codecInfos) {
            if (!info.isEncoder) {
                continue
            }
            try {
                info.getCapabilitiesForType(mimeType) ?: continue
            } catch (e: IllegalArgumentException) {
                continue
            }
            mediaCodecs.add(info)
        }
        return mediaCodecs.toTypedArray()
    }

    /**
     * @param avcProfileLevel AVC CodecProfileLevel
     */
    fun avcProfileLevelToString(avcProfileLevel: CodecProfileLevel?): String {
        if (avcProfileLevel == null) return "null-avcProfileLevel"
        if (sAVCProfiles.size() == 0 || sAVCLevels.size() == 0) {
            initProfileLevels()
        }
        var profile: String? = null
        var level: String? = null
        var i = sAVCProfiles.indexOfKey(avcProfileLevel.profile)
        if (i >= 0) {
            profile = sAVCProfiles.valueAt(i)
        }
        i = sAVCLevels.indexOfKey(avcProfileLevel.level)
        if (i >= 0) {
            level = sAVCLevels.valueAt(i)
        }
        if (profile == null) {
            profile = avcProfileLevel.profile.toString()
        }
        if (level == null) {
            level = avcProfileLevel.level.toString()
        }
        return "$profile-$level"
    }

    private fun initProfileLevels() {
        val fields = CodecProfileLevel::class.java.fields
        for (f in fields) {
            if (f.modifiers and (Modifier.STATIC or Modifier.FINAL) == 0) {
                continue
            }
            val name = f.name
            val target: SparseArray<String> = if (name.startsWith("AVCProfile")) {
                sAVCProfiles
            } else if (name.startsWith("AVCLevel")) {
                sAVCLevels
            } else if (name.startsWith("AACObject")) {
                sAACProfiles
            } else {
                continue
            }
            try {
                target.put(f.getInt(null), name)
            } catch (e: IllegalAccessException) { //ignored
            }
        }
    }

    private fun toHumanReadable(colorFormat: Int): String {
        if (sColorFormats.size() == 0) {
            initColorFormatFields()
        }
        val i = sColorFormats.indexOfKey(colorFormat)
        return if (i >= 0) sColorFormats.valueAt(i) else "0x" + Integer.toHexString(colorFormat)
    }

    private fun initColorFormatFields() {
        val fields = CodecCapabilities::class.java.fields
        for (f in fields) {
            if (f.modifiers and (Modifier.STATIC or Modifier.FINAL) == 0) {
                continue
            }
            val name = f.name
            if (name.startsWith("COLOR_")) {
                try {
                    val value = f.getInt(null)
                    sColorFormats.put(value, name)
                } catch (e: IllegalAccessException) { // ignored
                }
            }
        }
    }

    /**
     * Print information of all MediaCodec on this device.
     */
    fun logCodecInfo(codecInfo: Array<MediaCodecInfo>, mimeType: String) {
        for (info in codecInfo) {
            val builder = StringBuilder(512)
            val caps = info.getCapabilitiesForType(mimeType)
            builder.append("Encoder '").append(info.name).append('\'').append("\n  supported : ").append(Arrays.toString(info.supportedTypes))
            val videoCaps = caps.videoCapabilities
            if (videoCaps != null) {
                builder.append("\n  Video capabilities:").append("\n  Widths: ").append(videoCaps.supportedWidths).append("\n  Heights: ").append(videoCaps.supportedHeights).append("\n  Frame Rates: ").append(videoCaps.supportedFrameRates).append("\n  Bitrate: ").append(videoCaps.bitrateRange)
                if (VIDEO_AVC == mimeType) {
                    val levels = caps.profileLevels
                    builder.append("\n  Profile-levels: ")
                    for (level in levels) {
                        builder.append("\n  ").append(avcProfileLevelToString(level))
                    }
                }
                builder.append("\n  Color-formats: ")
                for (c in caps.colorFormats) {
                    builder.append("\n  ").append(toHumanReadable(c))
                }
            }
            val audioCaps = caps.audioCapabilities
            if (audioCaps != null) {
                builder.append("\n Audio capabilities:").append("\n Sample Rates: ").append(Arrays.toString(audioCaps.supportedSampleRates)).append("\n Bit Rates: ").append(audioCaps.bitrateRange).append("\n Max channels: ").append(audioCaps.maxInputChannelCount)
            }
            Log.i("screen-recorder", builder.toString())
        }
    }
}