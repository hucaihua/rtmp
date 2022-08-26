package com.hch.bilibili;

import android.hardware.display.DisplayManager;
import android.media.projection.MediaProjection;
import android.view.Surface;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by hucaihua on 2022/8/11
 *
 * @author hucaihua@bytedance.com
 */
public class MediaProjectionEncoder extends H264Encoder{
    MediaProjection mediaProjection;

    public MediaProjectionEncoder(MediaProjection mediaProjection, LinkedBlockingQueue<RTMPPackage> queue) {
        super(queue, 640, 1920);
        this.mediaProjection = mediaProjection;
    }

    @Override
    public void run() {
        Surface surface = mediaCodec.createInputSurface();
        //到此已经完成录屏到写入codec提供的surface的过程
        mediaProjection.createVirtualDisplay("project-encoder" , width , height , 2 ,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC , surface , null , null);
        super.run();
    }
}
