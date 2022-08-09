
#include <jni.h>
#include <string>
extern "C"
{
    #include  "librtmp/rtmp.h"
}
#include <android/log.h>
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,"hch===>",__VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,"hch===>",__VA_ARGS__)

typedef struct {
    int16_t sps_len;
    int16_t pps_len;
    int8_t *sps;
    int8_t *pps;
}LiveSpspps;

LiveSpspps * spspps = nullptr;
int ppsFlagLen = 4;
int spsFLagLen = 4;
RTMP * rtmp;

RTMPPacket *createSPSPPSPacket();

int sendAudio(jbyte *bytes, jint len, jlong timestamp);

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_hch_bilibili_H264Encoder_connectRtmp(JNIEnv *env, jobject thiz, jstring url_) {
//能1  不能2 MK  2.3
    const char *url = env->GetStringUTFChars(url_, 0);
    int ret;
//实例化对象
    rtmp = RTMP_Alloc();
    RTMP_Init(rtmp);
    rtmp->Link.timeout = 10;
    ret =RTMP_SetupURL(rtmp, (char*)url);
    if (ret == TRUE) {
        LOGI("RTMP_SetupURL");
    }
    RTMP_EnableWrite(rtmp);
    LOGI("RTMP_EnableWrite");

    ret = RTMP_Connect(rtmp, 0);
    if (ret == TRUE) {
        LOGI("RTMP_Connect ");
    }
    ret = RTMP_ConnectStream(rtmp, 0);
    if (ret == TRUE) {
        LOGI("connect success");
    }
    env->ReleaseStringUTFChars(url_, url);
    return ret;
}

// 不包含 67 ,只包含0x00000001 或者0x000001
int16_t spsStartIndex(int8_t *bytes){
    int start = 0;
    if ((bytes[start] == 0x00 && bytes[start+1] == 0x00 && bytes[start+2] == 0x00 && bytes[start+3] == 0x01 &&
         (bytes[start+4] & 0x1f) == 7)){
        spsFLagLen = start = 4;
    }else if ((bytes[start] == 0x00 && bytes[start+1] == 0x00 && bytes[start+2] == 0x01&&
                   (bytes[start+3] & 0x1f) == 7) ){
        spsFLagLen = start = 3;
    }
    return start;
}

// 不包含 68 ,只包含0x00000001 或者0x000001
int16_t ppsStartIndex(int spsStartIndex , int8_t *bytes , int16_t len){
    int start = 1;
    for (int i = spsStartIndex; i + 5 < len; i++) {
        if ((bytes[i] == 0x00 && bytes[i+1] == 0x00 && bytes[i+2] == 0x00 && bytes[i+3] == 0x01 &&
             (bytes[i+4] & 0x1f) == 8)){
            ppsFlagLen = 4;
            start = i + ppsFlagLen;
        }else if ((bytes[i] == 0x00 && bytes[i+1] == 0x00 && bytes[i+2] == 0x01&&
                   (bytes[i+3] & 0x1f) == 8) ){
            ppsFlagLen = 3;
            start = i + ppsFlagLen;
        }
    }
    return start;
}

void prepareVideo(int8_t *bytes , int16_t len){
    if (spspps == nullptr){
        spspps = (LiveSpspps *)malloc(sizeof(LiveSpspps));
    }
    if (spspps && ((!spspps->pps) || (!spspps->sps))){
        // find sps
        int spsStart = spsStartIndex(bytes);
        if (spsStart != 0){
            LOGI("find sps success spsStart=%d" , spsStart);
            int16_t ppsStart = ppsStartIndex(spsStart , bytes , len);
            if (ppsStart == -1){
                LOGI("find pps failed");
                return ;
            }
            spspps->sps_len = ppsStart - spsStart - ppsFlagLen;
            spspps->sps = static_cast<int8_t *>(malloc(spspps->sps_len));
            memcpy(spspps->sps , bytes + spsStart , spspps->sps_len);

            spspps->pps_len = len - ppsStart;
            spspps->pps = static_cast<int8_t *>(malloc(spspps->pps_len));
            memcpy(spspps->pps , bytes + ppsStart , spspps->pps_len);

            LOGI("sps pps ready -----> spsStart=%d , ppsStart=%d , spsFlagLen=%d , ppsFLagLen=%d , spsLen=%d , "
                 "ppsLen=%d" , spsStart,
                 ppsStart,spsFLagLen,ppsFlagLen,spspps->sps_len,spspps->pps_len);
        }else{
            LOGE("find sps failed");
        }
    }
}

int sendPacket(RTMPPacket *pPacket) {
    //使用rtmp的队列
    int r = RTMP_SendPacket(rtmp ,pPacket , 1);
    //释放body
    RTMPPacket_Free(pPacket);
    //释放packet
    free(pPacket);
    return r;
}

RTMPPacket *createSPSPPSPacket() {
    //sps , pps  , 16 表示 rtmp协议中规定的spspps包的其他字节的大小。
    int spsppsPacketLen = 16 + spspps->sps_len + spspps->pps_len;

    RTMPPacket *p = (RTMPPacket *)malloc(sizeof (RTMPPacket));
    RTMPPacket_Alloc(p , spsppsPacketLen);
    int i = 0;
    p->m_body[i++] = 0x17;
    p->m_body[i++] = 0x00;
    p->m_body[i++] = 0x00;
    p->m_body[i++] = 0x00;
    p->m_body[i++] = 0x00;
    p->m_body[i++] = 0x01;
    p->m_body[i++] = spspps->sps[1]; // baseline
    p->m_body[i++] = spspps->sps[2]; // 兼容性
    p->m_body[i++] = spspps->sps[3]; // level ，码率等级
    p->m_body[i++] = 0xff; // nalu长度
    p->m_body[i++] = 0xe1; //
    p->m_body[i++] = (spspps->sps_len >>8) & 0xff; // 先取高8位 ，右移后仍然是int类型，&0xff后变成字节类型
    p->m_body[i++] = spspps->sps_len  & 0xff; // 再取低8位 ，右移后仍然是int类型，&0xff后变成字节类型
    memcpy(&p->m_body[i] , spspps->sps , spspps->sps_len); // copy sps content
    i = i + spspps->sps_len;
    p->m_body[i++ ] = 0x01;
    p->m_body[i++] = (spspps->pps_len >>8) & 0xff; // 先取高8位 ，右移后仍然是int类型，&0xff后变成字节类型
    p->m_body[i++] = spspps->pps_len  & 0xff; // 再取低8位 ，右移后仍然是int类型，&0xff后变成字节类型
    memcpy(&p->m_body[i] , spspps->pps , spspps->pps_len); // copy pps content
    //配置rtmp
    p->m_packetType = RTMP_PACKET_TYPE_VIDEO;
    p->m_nBodySize = spsppsPacketLen;
    //channel自定义
    p->m_nChannel = 0x04;
    //采用相对时间戳
    p->m_hasAbsTimestamp = 0;
    p->m_nTimeStamp = 0;
    //给服务器做参考的
    p->m_headerType = RTMP_PACKET_SIZE_LARGE;
    p->m_nInfoField2 = rtmp->m_stream_id;
    return p;
}

// bytes 包含了分隔符0000000165xxxxxxx
RTMPPacket *createVideoPacket(int type ,int8_t *bytes , int16_t len , long timems) {
    //由于分隔符一般情况下是一样长的，因此可以用spsFlagLen ，bytes中的数据不需要包含分隔符，但是要包含帧类型
    bytes += spsFLagLen;
    len -= spsFLagLen;
    // 9 为rtmp协议中定义的i帧包头的长度
    int iFramePacketLen = 9 + len;
    RTMPPacket* p = (RTMPPacket*)malloc(sizeof(RTMPPacket));
    RTMPPacket_Alloc(p , iFramePacketLen);
    int i = 0;
    //I frame
    if (type == 0x05){
        p->m_body[i++] = 0x17;
    }else{
        // p , b frame.
        p->m_body[i++] = 0x27;
    }

    p->m_body[i++] = 0x01;
    p->m_body[i++] = 0x00;
    p->m_body[i++] = 0x00;
    p->m_body[i++] = 0x00;

    p->m_body[i++] = (len >>24) & 0xff;
    p->m_body[i++] = (len >>16) & 0xff;
    p->m_body[i++] = (len >>8) & 0xff;
    p->m_body[i++] = len & 0xff;
    memcpy(&p->m_body[i] , bytes , len);

    //配置rtmp
    p->m_packetType = RTMP_PACKET_TYPE_VIDEO;
    p->m_nBodySize = iFramePacketLen;
    //channel自定义
    p->m_nChannel = 0x04;
    //采用相对时间戳
    p->m_hasAbsTimestamp = 0;
    //对于I帧，它的时间等于编码出来的相对时间戳
    p->m_nTimeStamp = timems;
    //给服务器做参考的
    p->m_headerType = RTMP_PACKET_SIZE_LARGE;
    p->m_nInfoField2 = rtmp->m_stream_id;
    return p;
}

/**
 *
 * @param bytes 每帧的数据
 * @param len  每帧的长度
 * @param timems 每帧编码出来的相对时间戳 ，单位ms , 对于I帧，它是编码出来的相对时间戳，对于sps/pps帧，它可以为0
 * @return
 */
int16_t sendVideo(int8_t *bytes , int16_t len , long timems) {
    int8_t type = bytes[4] & 0x1f;
    int r = 0;
    // sps , pps
    if (type == 0x7){
        prepareVideo(bytes , len);
        LOGI("prepare sps , pps success");
    }else if (type == 0x5){
        // i frame find , send two packet
        RTMPPacket * packet = createSPSPPSPacket();
        r = sendPacket(packet);
        if (r == TRUE){
            LOGI("send sps , pps success");
        }else{
            LOGE("send sps , pps success");
        }

    }
    //i , b , p frame
    RTMPPacket* iPacket = createVideoPacket(type , bytes , len , timems);
    r = sendPacket(iPacket);
    if (r == TRUE){
        if (type == 0x05){
            LOGI("send I frame success");
        }else{
            LOGI("send other frame success");
        }
    }else{
        LOGE("send video frame failed");
    }

    return r;
}


RTMPPacket *createAudioPacket(int8_t *bytes, jint len, jlong timestamp) {
    // 2个字节表示 音频解码信息
    int bodySize = len + 2;
    RTMPPacket *p = (RTMPPacket *)malloc(sizeof (RTMPPacket));
    RTMPPacket_Alloc(p , bodySize);
    int i = 0;
    p->m_body[i++] = 0xAF;
    p->m_body[i++] = 0x01;
    memcpy(&p->m_body[i++] , bytes , len);

//配置rtmp
    p->m_packetType = RTMP_PACKET_TYPE_AUDIO;
    p->m_nBodySize = bodySize;
    //channel自定义
    p->m_nChannel = 0x02;
    //采用相对时间戳
    p->m_hasAbsTimestamp = 0;
    //对于I帧，它的时间等于编码出来的相对时间戳
    p->m_nTimeStamp = timestamp;
    //给服务器做参考的
    p->m_headerType = RTMP_PACKET_SIZE_LARGE;
    p->m_nInfoField2 = rtmp->m_stream_id;
    return p;
}

int sendAudio(int8_t *bytes , jint len, jlong timestamp) {
    int r;
    RTMPPacket * packet = createAudioPacket(bytes , len , timestamp);
    r = sendPacket(packet);
    return r;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_hch_bilibili_PackageSender_sendData(JNIEnv *env, jobject thiz, jbyteArray data, jint len, jlong timestamp,
                                             jint type) {
    LOGI("start send package , type = %d" , type);
    int ret;
    jbyte * bytes = env->GetByteArrayElements(data ,NULL);
    switch (type) {
        case 0:
            ret = sendVideo(bytes , len , timestamp);
            break;
        case 1:
            ret = sendAudio(bytes , len , timestamp);
            break;
        case 2:

            break;
    }

    env->ReleaseByteArrayElements(data , bytes , 0);
    return ret;
}
