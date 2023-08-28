package com.zj.screenrecorder.ann;

import static android.media.MediaCodecInfo.CodecProfileLevel.*;

import androidx.annotation.IntDef;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Inherited
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.PARAMETER, ElementType.METHOD})
@IntDef({AACObjectELD, AACObjectERLC, AACObjectHE, AACObjectHE_PS, AACObjectLC, AACObjectLD, AACObjectLTP, AACObjectMain, AACObjectSSR, AACObjectScalable})
public @interface AACProfileLevel {}


