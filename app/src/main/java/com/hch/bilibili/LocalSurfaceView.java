package com.hch.bilibili;

import android.content.Context;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

import java.io.IOException;

//1.打开camera 2. 设置camera对应的surface 3. 添加预览回调 4.开始预览 5.预览回调buffer给codec编码发送给客户端
public class LocalSurfaceView extends SurfaceView implements SurfaceHolder.Callback, Camera.PreviewCallback {
    private Camera mCamera;
    private Camera.Size size;
    byte[] buffer;
    private Camera.PreviewCallback onPreviewCallback;

    public LocalSurfaceView(Context context) {
        super(context);
    }

    public LocalSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        getHolder().addCallback(this);

    }

    public void addOnPreviewCallback(Camera.PreviewCallback callback){
        this.onPreviewCallback = callback;
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (onPreviewCallback != null){
            onPreviewCallback.onPreviewFrame(data,camera);
        }

        mCamera.addCallbackBuffer(data);
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
        startPreview();
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {

    }

    private void startPreview() {
        mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
        size = mCamera.getParameters().getPreviewSize();
        try {
            mCamera.setPreviewDisplay(getHolder());
        } catch (IOException e) {
            e.printStackTrace();
        }
        buffer = new byte[size.width * size.height * 3/2];
        mCamera.addCallbackBuffer(buffer);
        mCamera.setDisplayOrientation(90);
        mCamera.setPreviewCallback(this);
        mCamera.startPreview();
    }

    public void removePreviewCallback() {
        this.onPreviewCallback = null;
    }
}
