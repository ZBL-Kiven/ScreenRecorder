package com.zj.screenrecorder.ann;

import android.content.res.Configuration;

import androidx.annotation.IntDef;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Inherited
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.PARAMETER, ElementType.METHOD})
@IntDef({Configuration.ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_PORTRAIT})
public @interface ScreenOrientation {

}
