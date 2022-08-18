package com.hch.bilibili;

import static com.hch.bilibili.YUVUtil.nv12;

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
    byte[] yuv;

    public CameraEncoder(LinkedBlockingQueue<RTMPPackage> queue, int width, int height) {
        super(queue);
        this.width = width;
        this.height = height;
        yuv = new byte[width * height * 3 / 2];
    }

    public void input(byte[] data) {
        if (isLiving){
            Log.d("hch" , "start input data to queue:  " + data.length);
            inputQueue.offer(data);
        }
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
        //读取nv21数据，从camera出来的数据不能被codec直接处理，否则会花屏
        byte[] buffer = inputQueue.poll();
        if (buffer != null && buffer.length > 0){
            Log.d("hch" , "input camera buffer len = " + buffer.length);

            YUVUtil.showImage(buffer , width , height);

            nv12 = YUVUtil.nv21toNV12(buffer);
            YUVUtil.portraitData2Raw(nv12, yuv, width, height);

            int inputIndex = mediaCodec.dequeueInputBuffer(1000);
            if (inputIndex >= 0){
                ByteBuffer byteBuffer = mediaCodec.getInputBuffer(inputIndex);
                byteBuffer.put(buffer , 0 , buffer.length);
                mediaCodec.queueInputBuffer(inputIndex , 0 , buffer.length , System.nanoTime()/1000 , 0);
            }
        }
    }
}
