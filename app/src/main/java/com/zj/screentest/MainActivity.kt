package com.zj.screentest

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.zj.screenrecorder.BaseScreenRecorder
import java.io.File

class MainActivity : AppCompatActivity() {

    var starBtn: TextView? = null
    private val recorder = object : BaseScreenRecorder() {
        override suspend fun onStartingRecord() {
            starBtn?.text = "stop record"
        }

        override suspend fun onCompletedRecord(file: File?): Boolean {
            val path = file?.path
            return true
        }

        override suspend fun onStopRecord(isCompleted: Boolean?) {
            starBtn?.text = "start record"
        }

        override fun getOutputPath(context: Context): String {
            return context.externalCacheDir?.path ?: context.cacheDir.path
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        starBtn = findViewById(R.id.record_button)
        starBtn?.setOnClickListener { v: View ->
            v.isSelected = !v.isSelected
            if (v.isSelected) recorder.startRecord(this) else recorder.stopRecord()
        }
    }
}