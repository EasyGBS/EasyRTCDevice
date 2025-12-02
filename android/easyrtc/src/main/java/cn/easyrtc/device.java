package cn.easyrtc;

public class device {

    static {
        System.loadLibrary("EasyRTCDevice");
    }

    public native int release();

    public native int create(int version, String serverAddr, int serverPort, String password,
                             String localId, int channelNum, int userptr);

    public native int SetChannelInfo(int channelId, String channelIDStr, int videoCodecID, int audioCodecID);

    public native int Hangup(String peerUUID);

    // decline: 1为拒绝, 0为接受
    public native int PassiveCallResponse(String peerUUID, int decline);

    public native int SendVideoFrame(int channelId, byte[] framedata, int framesize, int keyframe, int pts);

    public native int SendAudioFrame(int channelId, byte[] framedata, int framesize, int keyframe, int pts);

    public native int SendMetadata(int channelId, String msg, int size);

    //    dataType, codecID,，data, len
    // void* userptr, const char* peerUUID, EASYRTC_DATA_TYPE_ENUM_E dataType, int codecID, int isBinary,
    // char* cbData, int cbSize, int keyframe, unsigned long long pts
    public static int OnEasyRTCDataCallback(int userptr,
                                            String peerUUID,
                                            int dataType,
                                            int codecID,
                                            int isBinary,
                                            byte[] cbData,
                                            int cbSize,
                                            int keyframe,
                                            int pts) {
        return EasyRTCSdk.handleDataCallback(userptr, peerUUID, dataType, codecID, isBinary,
                cbData, cbSize, keyframe, pts);
    }

    public static int OnEasyRTCDeviceCallback(int code, String data, int length) {
        return EasyRTCSdk.handleDeviceCallback(code, data, length);
    }

    public static int OnEasyRTCOnlineUserCallback(String data, int length) {
//        Log.d("OnEasyRTCOnlineUserCallback", "data: " + data);
        return EasyRTCSdk.handleOnlineUserCallback(data, length);
    }

}
