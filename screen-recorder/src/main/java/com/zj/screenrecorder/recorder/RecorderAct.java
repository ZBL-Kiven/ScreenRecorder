package com.zj.screenrecorder.recorder;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.os.Build.VERSION_CODES.M;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class RecorderAct extends Activity {

    private static final String TAG = "RecorderService";
    private static final int REQUEST_MEDIA_PROJECTION = 1;
    private static final int REQUEST_PERMISSIONS = 2;
    static final String REQUEST_CANCEL = ".action.Canceled";
    private boolean micRecordEnabled;
    private Intent captureIntent;
    private Intent resultIntent;

    static void start(Context context, boolean micRecordEnabled, Intent captureIntent) {
        Intent intent = new Intent(context, RecorderAct.class);
        intent.putExtra("micRecordEnabled", micRecordEnabled);
        intent.putExtra("captureIntent", captureIntent);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowManager.LayoutParams attrs = getWindow().getAttributes();
        attrs.flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        captureIntent = getIntent().getParcelableExtra("captureIntent");
        micRecordEnabled = getIntent().getBooleanExtra("micRecordEnabled", false);
        if (captureIntent == null) finish();
        else init();
    }

    public void init() {
        if (hasPermissions()) {
            requestMediaProjection();
        } else if (Build.VERSION.SDK_INT >= M) {
            requestPermissions();
        } else {
            Log.e(TAG, "startRecord: failed with no permission!");
        }
    }

    @TargetApi(M)
    private void requestPermissions() {
        ArrayList<String> permissions = new ArrayList<>();
        permissions.add(READ_EXTERNAL_STORAGE);
        permissions.add(WRITE_EXTERNAL_STORAGE);
        if (micRecordEnabled) permissions.add(RECORD_AUDIO);
        boolean showRationale = false;
        List<String> rp = new ArrayList<>();
        for (String perm : permissions) {
            if (checkSelfPermission(perm) == PackageManager.PERMISSION_DENIED) rp.add(perm);
            showRationale |= shouldShowRequestPermissionRationale(perm);
        }
        if (!rp.isEmpty() && !showRationale) {
            String[] rps = new String[rp.size()];
            requestPermissions(rp.toArray(rps), REQUEST_PERMISSIONS);
        } else {
            if (!showRationale) {
                requestMediaProjection();
            } else {
                Log.e(TAG, "permission denied and user reject to ask again!");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_PERMISSIONS) {
            int granted = PackageManager.PERMISSION_GRANTED;
            for (int r : grantResults) {
                granted |= r;
            }
            if (granted == PackageManager.PERMISSION_GRANTED) {
                requestMediaProjection();
            } else {
                Log.e(TAG, "permission denied!");
            }
        }
    }

    private void requestMediaProjection() {
        startActivityForResult(captureIntent, REQUEST_MEDIA_PROJECTION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            resultIntent = new Intent();
            resultIntent.setAction(Recorder.REQUEST_MEDIA_PROJECTION_ID);
            resultIntent.putExtra("resultCode", resultCode);
            resultIntent.putExtra("captureIntent", data);
            finish();
        }
    }

    private boolean hasPermissions() {
        PackageManager pm = getPackageManager();
        String packageName = getPackageName();
        int granted = (micRecordEnabled ? pm.checkPermission(RECORD_AUDIO, packageName) : PackageManager.PERMISSION_GRANTED) | pm.checkPermission(WRITE_EXTERNAL_STORAGE, packageName);
        return granted == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onDestroy() {
        if (resultIntent == null) {
            resultIntent = new Intent();
            resultIntent.setAction(REQUEST_CANCEL);
        }
        sendBroadcast(resultIntent);
        super.onDestroy();
    }
}