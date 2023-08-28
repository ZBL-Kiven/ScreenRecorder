package com.zj.screenrecorder.recorder;

import java.io.IOException;

/**
 * @author Zjj
 * @version 2022/4/14
 */
@SuppressWarnings("unused")
interface Encoder {
    void prepare() throws IOException;

    void stop();

    void release();

    void setCallback(Callback callback);

    interface Callback {
        void onError(Encoder encoder, Exception exception);
    }
}
