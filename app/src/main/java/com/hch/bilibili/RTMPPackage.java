package com.hch.bilibili;

public class RTMPPackage {

    public static int  TYPE_VIDEO = 0;
    public static int  TYPE_AUDIO = 1;

    //    帧数据
    private byte[] buffer;

    //    时间戳
    private long tms;
    private int type = TYPE_VIDEO;

    public RTMPPackage(byte[] buffer, long tms) {
        this.buffer = buffer;
        this.tms = tms;
    }

    public byte[] getBuffer() {
        return buffer;
    }

    public void setBuffer(byte[] buffer) {
        this.buffer = buffer;
    }

    public long getTms() {
        return tms;
    }

    public void setTms(long tms) {
        this.tms = tms;
    }

    public int getType() {
        return type;
    }
}
