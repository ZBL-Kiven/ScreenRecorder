package com.zj.screenrecorder.ann;

import static android.media.MediaCodecInfo.CodecProfileLevel.AACObjectELD;
import static android.media.MediaCodecInfo.CodecProfileLevel.AACObjectERLC;
import static android.media.MediaCodecInfo.CodecProfileLevel.AACObjectHE;
import static android.media.MediaCodecInfo.CodecProfileLevel.AACObjectHE_PS;
import static android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC;
import static android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLD;
import static android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLTP;
import static android.media.MediaCodecInfo.CodecProfileLevel.AACObjectMain;
import static android.media.MediaCodecInfo.CodecProfileLevel.AACObjectSSR;
import static android.media.MediaCodecInfo.CodecProfileLevel.AACObjectScalable;

import androidx.annotation.IntDef;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Inherited
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
@IntDef({AACObjectELD, AACObjectERLC, AACObjectHE, AACObjectHE_PS, AACObjectLC, AACObjectLD, AACObjectLTP, AACObjectMain, AACObjectSSR, AACObjectScalable})
public @interface AVCLevel {}


