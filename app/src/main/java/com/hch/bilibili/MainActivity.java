package com.hch.bilibili;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.concurrent.LinkedBlockingQueue;

public class MainActivity extends AppCompatActivity implements Camera.PreviewCallback, CameraHelper.OnChangedSizeListener {

    // Used to load the 'bilybily' library on application startup.
    static {
        System.loadLibrary("bilybily");
    }

    MediaProjectionManager projectionManager;
    MediaProjection mediaProjection;
    MediaProjectionEncoder projectionEncoder;
    CameraEncoder cameraEncoder;
    boolean isProjection = false;

//    LocalSurfaceView localSurfaceView;

    SurfaceView surfaceView;
    CameraHelper cameraHelper;

    private LinkedBlockingQueue<RTMPPackage> queue = new LinkedBlockingQueue<>();
    PackageSender packageSender;
    private AudioEncoder audioEncoder;
    private static String bilibilyRTMPURL="rtmp://live-push.bilivideo.com/live-bvc/?streamname=live_212228851_21446951&key=9a02e8eb340ba9a2c825887422dda816&schedule=rtmp&pflag=1";

    boolean isConnectedToRTMP = false;
    private int mPreviewWidth;
    private int mPreviewHeight;

    public boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CAMERA
            }, 1);

        }
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        localSurfaceView = findViewById(R.id.surfaceview);
//        localSurfaceView.addOnPreviewCallback(this);

        cameraHelper = new CameraHelper(this,Camera.CameraInfo.CAMERA_FACING_FRONT,480,640);
        surfaceView = findViewById(R.id.surfaceview);
        cameraHelper.setPreviewCallback(this);
        cameraHelper.setPreviewDisplay(surfaceView.getHolder());
        cameraHelper.setOnChangedSizeListener(this);
        cameraHelper.switchCamera();

        checkPermission();

        if (!connectRtmp(bilibilyRTMPURL)) {
            isConnectedToRTMP = false;
            Log.d("hch", "connect failed");
            Toast.makeText(this,"?????????????????????" , Toast.LENGTH_LONG).show();
            return;
        }else{
            isConnectedToRTMP = true;
            Toast.makeText(this,"?????????????????????" , Toast.LENGTH_LONG).show();
        }
        packageSender = new PackageSender(queue);
        packageSender.start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || requestCode != 1) return;

        mediaProjection = projectionManager.getMediaProjection(resultCode , data);
        projectionEncoder = new MediaProjectionEncoder(mediaProjection,queue);
        projectionEncoder.start();
        if (audioEncoder == null){
            audioEncoder = new AudioEncoder(queue);
            audioEncoder.start();
        }
    }

    private native boolean connectRtmp(String url);

    public void startLive(View view) {
        if (!isConnectedToRTMP){
            Toast.makeText(this,"?????????????????????" , Toast.LENGTH_LONG).show();
        }
        isProjection = true;

        projectionManager = (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        Intent captureIntent = projectionManager.createScreenCaptureIntent();
        startActivityForResult(captureIntent, 1);
    }

    public void stopLive(View view){
        if (!isConnectedToRTMP){
            Toast.makeText(this,"?????????????????????" , Toast.LENGTH_LONG).show();
        }
        if (projectionEncoder != null){
            projectionEncoder.stopLive();
            projectionEncoder = null;
        }
        if (cameraEncoder != null){
            cameraEncoder.stopLive();
            cameraEncoder = null;
        }
        if (audioEncoder != null){
            audioEncoder.stopLive();
        }
        if (packageSender != null){
            packageSender.stopLive();
        }
//        localSurfaceView.removePreviewCallback();

    }

    public void startCameraLive(View view) {
        if (!isConnectedToRTMP){
            Toast.makeText(this,"?????????????????????" , Toast.LENGTH_LONG).show();
        }
        isProjection = false;
        cameraEncoder.start();

        if (audioEncoder == null){
            audioEncoder = new AudioEncoder(queue);
            audioEncoder.start();
        }
    }

//    @Override
//    public void onPreviewFrame(byte[] data, Camera.Size size) {
//        if (cameraEncoder == null){
//            cameraEncoder = new CameraEncoder(queue , size.width , size.height);
//
//        }
//        cameraEncoder.input(data);
//    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (cameraEncoder == null){
            cameraEncoder = new CameraEncoder(queue , mPreviewWidth , mPreviewHeight);

        }
        cameraEncoder.input(data);
    }

    @Override
    public void onChanged(int w, int h) {
        mPreviewWidth = w;
        mPreviewHeight = h;
    }
}