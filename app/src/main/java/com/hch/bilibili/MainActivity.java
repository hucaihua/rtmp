package com.hch.bilibili;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.LinkedBlockingQueue;

public class MainActivity extends AppCompatActivity {

    // Used to load the 'bilybily' library on application startup.
    static {
        System.loadLibrary("bilybily");
    }

    MediaProjectionManager projectionManager;
    MediaProjection mediaProjection;
    H264Encoder h264Encoder;

    private LinkedBlockingQueue<RTMPPackage> queue = new LinkedBlockingQueue<>();
    PackageSender packageSender;

    public boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.CAMERA
            }, 1);

        }
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkPermission();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || requestCode != 1) return;

        packageSender = new PackageSender(queue);
        mediaProjection = projectionManager.getMediaProjection(resultCode , data);
        h264Encoder = new H264Encoder(mediaProjection,queue);
        h264Encoder.start();
        packageSender.start();
    }

    public void startLive(View view) {
        projectionManager = (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        Intent captureIntent = projectionManager.createScreenCaptureIntent();
        startActivityForResult(captureIntent, 1);
    }

    public void stopLive(View view){
        if (h264Encoder != null){
            h264Encoder.stopLive();
        }
        if (packageSender != null){
            packageSender.stopLive();
        }
    }
}