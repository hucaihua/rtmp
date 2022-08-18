package com.hch.bilibili;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Bundle;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by hucaihua on 2022/7/28
 *
 * @author hucaihua@bytedance.com
 */
public class H264Encoder extends Thread{
    protected int width;
    protected int height;
    protected long startTime = 0;
    protected boolean isLiving = false;
    protected long lastKeyFrameTime ;
    protected MediaCodec mediaCodec;
    protected LinkedBlockingQueue<RTMPPackage> queue;
    protected MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

    public H264Encoder(LinkedBlockingQueue<RTMPPackage> queue) {
        this.width = 640;
        this.height = 1920;
        this.queue = queue;
    }

    //帧率 ， I帧间隔 ， 码率 ， 数据格式
    // mediaCodec负责提供surface给mediaProjection使用
    protected void configEncodeCodec(){
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE , 20);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 30);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, width * height);
        //指定编码的数据格式是由surface决定的。
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT , MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);

        try{
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            mediaCodec.configure(mediaFormat , null , null , MediaCodec.CONFIGURE_FLAG_ENCODE);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        super.run();
        isLiving = true;
        if (mediaCodec == null){
            configEncodeCodec();
        }
        mediaCodec.start();

        while (isLiving) {
            input();
            output();
        }
        Log.d("hch", "live stopped");
    }

    protected void input() {

    }

    protected void output(){
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
//                FileUtils.writeBytes(bytes);
//                FileUtils.writeContent(bytes);
                queue.offer(new RTMPPackage(bytes, info.presentationTimeUs / 1000 - startTime).setType(RTMPPackage.TYPE_VIDEO));
                mediaCodec.releaseOutputBuffer(outIndex, false);
//                Log.d("hch", "queue offer size:"+bytes.length);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void stopLive() {
        isLiving = false;
        mediaCodec.flush();
//        mediaCodec.release();
        mediaCodec.stop();
    }
}