package com.hch.bilibili;

import android.util.Log;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by hucaihua on 2022/8/3
 *
 * @author hucaihua@bytedance.com
 */
public class PackageSender extends Thread{
    LinkedBlockingQueue<RTMPPackage> queue;
    Thread thread;
    private boolean isLiving = false;

    PackageSender(LinkedBlockingQueue<RTMPPackage> queue ){
        this.queue = queue;
        setName("package-sender");
    }

    @Override
    public void run() {
        super.run();
        isLiving = true;
        try{
            while (isLiving){
                RTMPPackage rtmpPackage = queue.poll();
                if (rtmpPackage != null) {
                    Log.d("hch", "queue take package success");
                    sendData(rtmpPackage.getBuffer(), rtmpPackage.getBuffer().length, rtmpPackage.getTms(),
                            rtmpPackage.getType());
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }


    private native int sendData(byte[] data , int len , long timestamp , int type);

    public void stopLive() {
        isLiving = false;
    }
}
