package com.zj.screenrecorder.recorder;

import android.media.MediaFormat;

import com.zj.screenrecorder.configs.AudioEncodeConfig;

/**
 * @author Zjj
 * @version 2022/4/13
 */
class AudioEncoder extends BaseEncoder {
    private final AudioEncodeConfig mConfig;

    AudioEncoder(AudioEncodeConfig config) {
        super(config.codecName);
        this.mConfig = config;
    }

    @Override
    protected MediaFormat createMediaFormat() {
        return mConfig.toFormat();
    }

}
