package com.zj.screenrecorder.recorder;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.view.Surface;

import com.zj.screenrecorder.configs.VideoEncodeConfig;

import java.util.Objects;

/**
 * @author Zjj
 * @version 2022/4/13
 */
class VideoEncoder extends BaseEncoder {

    private final VideoEncodeConfig mConfig;
    private Surface mSurface;


    VideoEncoder(VideoEncodeConfig config) {
        super(config.codecName);
        this.mConfig = config;
    }

    @Override
    protected void onEncoderConfigured(MediaCodec encoder) {
        mSurface = encoder.createInputSurface();
    }

    @Override
    protected MediaFormat createMediaFormat() {
        return mConfig.toFormat();
    }

    /**
     * @throws NullPointerException if prepare() not call
     */
    Surface getInputSurface() {
        return Objects.requireNonNull(mSurface, "doesn't prepare()");
    }

    @Override
    public void release() {
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
        super.release();
    }
}
