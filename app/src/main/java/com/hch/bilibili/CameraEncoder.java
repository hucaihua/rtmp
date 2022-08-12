package com.hch.bilibili;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by hucaihua on 2022/8/11
 *
 * @author hucaihua@bytedance.com
 */
public class CameraEncoder extends H264Encoder{

    private LinkedBlockingQueue<byte[]> inputQueue = new LinkedBlockingQueue<byte[]>();

    public CameraEncoder(LinkedBlockingQueue<RTMPPackage> queue) {
        super(queue);
    }

    public void input(byte[] data) {
        inputQueue.offer(data);
    }

    protected void configEncodeCodec(){
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE , 20);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 30);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, width * height);
        //指定编码的数据格式是由surface决定的。
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT , MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);

        try{
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            mediaCodec.configure(mediaFormat , null , null , MediaCodec.CONFIGURE_FLAG_ENCODE);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    protected void input() {
        //读取pcm数据
        byte[] buffer = inputQueue.poll();
        if (buffer.length > 0){
            Log.d("hch" , "input camera buffer len = " + buffer.length);

            int inputIndex = mediaCodec.dequeueInputBuffer(1000);
            if (inputIndex >= 0){
                ByteBuffer byteBuffer = mediaCodec.getInputBuffer(inputIndex);
                byteBuffer.put(buffer , 0 , buffer.length);
                mediaCodec.queueInputBuffer(inputIndex , 0 , buffer.length , System.nanoTime()/1000 , 0);
            }
        }
    }
}
