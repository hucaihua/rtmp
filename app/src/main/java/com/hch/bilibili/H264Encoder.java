package com.hch.bilibili;

import android.hardware.display.DisplayManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by hucaihua on 2022/7/28
 *
 * @author hucaihua@bytedance.com
 */
public class H264Encoder extends Thread{

    private final LinkedBlockingQueue<RTMPPackage> queue;
    MediaProjection mediaProjection;
    MediaCodec mediaCodec;
    private int width;
    private int height;

    private static String bilibilyRTMPURL="rtmp://live-push.bilivideo.com/live-bvc/?streamname=live_212228851_21446951&key=59f5fcfb903695f199233e7f2c8112b2&schedule=rtmp&pflag=1";
    private boolean isLiving = false;
    long startTime = 0;


    public H264Encoder(MediaProjection mediaProjection, LinkedBlockingQueue<RTMPPackage> queue) {
        this.mediaProjection = mediaProjection;
        this.width = 640;
        this.height = 1920;
        this.queue = queue;
        configEncodeCodec();

    }

    //帧率 ， I帧间隔 ， 码率 ， 数据格式
    // mediaCodec负责提供surface给mediaProjection使用
    private void configEncodeCodec() {

        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE , 20);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 30);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, width * height);
        //指定编码的数据格式是由surface决定的。
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT , MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);

        try{
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            mediaCodec.configure(mediaFormat , null , null , MediaCodec.CONFIGURE_FLAG_ENCODE);
            Surface surface = mediaCodec.createInputSurface();
            //到此已经完成录屏到写入codec提供的surface的过程
            mediaProjection.createVirtualDisplay("project-encoder" ,width,height , 2 ,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC , surface , null , null);
        }catch (Exception e){
            e.printStackTrace();
        }

    }

    private long lastKeyFrameTime ;
    @Override
    public void run() {
        super.run();
        isLiving = true;
        mediaCodec.start();

        if (!connectRtmp(bilibilyRTMPURL)) {
            Log.d("hch", "connect failed");
            return;
        }

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (isLiving) {

            //超过2秒，强制输出I帧。
            if (System.currentTimeMillis() - lastKeyFrameTime > 2000){
                Bundle b = new Bundle();
                b.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME , 0);
                mediaCodec.setParameters(b);
                lastKeyFrameTime = System.currentTimeMillis();
            }

            try {
                int outIndex = mediaCodec.dequeueOutputBuffer(info, 10000);
                byte[] bytes = new byte[info.size];
                if (outIndex >= 0) {
                    if (startTime == 0){
                        startTime = info.presentationTimeUs / 1000;
                    }
                    ByteBuffer byteBuffer = mediaCodec.getOutputBuffer(outIndex);
                    byteBuffer.get(bytes);
                    FileUtils.writeBytes(bytes);
                    FileUtils.writeContent(bytes);
                    queue.offer(new RTMPPackage(bytes, info.presentationTimeUs / 1000 - startTime).setType(RTMPPackage.TYPE_VIDEO));
                    mediaCodec.releaseOutputBuffer(outIndex, false);
                    Log.d("hch", "queue offer size:"+bytes.length);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        Log.d("hch", "live stopped");
    }

    private native boolean connectRtmp(String url);

    public void stopLive() {
        isLiving = false;
        mediaCodec.stop();
    }
}