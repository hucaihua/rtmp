package com.hch.bilibili;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by hucaihua on 2022/8/9
 *
 * @author hucaihua@bytedance.com
 */
public class AudioEncoder extends Thread {
    private final LinkedBlockingQueue<RTMPPackage> queue;
    MediaCodec mediaCodec;
    private AudioRecord audioRecord;
    int minBufferSize;
    long startTime = 0;

    private static String bilibilyRTMPURL = "rtmp://live-push.bilivideo.com/live-bvc/?streamname=live_212228851_21446951&key=59f5fcfb903695f199233e7f2c8112b2&schedule=rtmp&pflag=1";
    private boolean isLiving = false;


    public AudioEncoder(LinkedBlockingQueue<RTMPPackage> queue) {
        this.queue = queue;
        configEncodeCodec();
    }

    @SuppressLint("MissingPermission")
    private void configEncodeCodec() {

        //采集音频的参数，采样率44.1khz，单声道
        MediaFormat mediaFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 44100, 1);
        //描述要使用的AAC配置文件的键
        mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        // 比特率 128k 或者64k
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128_000);
        try {
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            //声音量化三要素：1.采样位数/采样大小 -采样的数据用几位表示 2.采样频率-每秒采样多少次 3.声道数 单声道（Mono）和双声道（Stereo）
            minBufferSize = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 44100, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, minBufferSize);
        }catch (Exception e){
            e.printStackTrace();
        }
    }


    @Override
    public void run() {
        super.run();
        isLiving = true;
        mediaCodec.start();
        audioRecord.startRecording();
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        while (isLiving){
            byte[] buffer = new byte[minBufferSize];
            //读取pcm数据
            int len = audioRecord.read(buffer , 0 , buffer.length);
            if (len <= 0){
                continue;
            }

            Log.d("hch" , "read audio buffer len = " + len);

            int inputIndex = mediaCodec.dequeueInputBuffer(1000);
            if (inputIndex >= 0){
                ByteBuffer byteBuffer = mediaCodec.getInputBuffer(inputIndex);
                byteBuffer.put(buffer , 0 , len);
                mediaCodec.queueInputBuffer(inputIndex , 0 , len , System.nanoTime()/1000 , 0);
            }

            int outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo , 1000);
            //输入的一块数据，输出不一定是一块
            while (outputIndex >= 0 && isLiving){
                ByteBuffer byteBuffer = mediaCodec.getOutputBuffer(outputIndex);
                byte[] encodeAAC = new byte[bufferInfo.size];
                byteBuffer.get(encodeAAC);

                //时间戳采用相对时间戳，因为服务器需要用相对时间戳
                if (startTime == 0){
                    startTime = bufferInfo.presentationTimeUs / 1000;
                }
                RTMPPackage rtmpPackage =
                        new RTMPPackage(encodeAAC , bufferInfo.presentationTimeUs / 1000 - startTime).setType(RTMPPackage.TYPE_AUDIO);
                queue.offer(rtmpPackage);
                mediaCodec.releaseOutputBuffer(outputIndex ,false);

                //可能一帧音频数据，解码出来会有多帧aac数据。
                outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
            }
        }
    }

    public void stopLive() {
        isLiving = false;
        mediaCodec.stop();
        audioRecord.stop();
    }
}
