package com.iflytek.driver;

/**
 * 导台语音端-->语义端发送数据包
 */
public class MsgPacket {
    private String daotaiID;    // 导台ID
    private String message;     // 语音识别结果
    private long timestamp;     // 发送时的时间戳
    private String msgCalled;    // 触发者

    public MsgPacket() {
    }

    public MsgPacket(String daotaiID, String message, long timestamp, String msgCalled) {
        this.daotaiID = daotaiID;
        this.message = message;
        this.timestamp = timestamp;
        this.msgCalled = msgCalled;
    }

    public String getDaotaiID() {
        return daotaiID;
    }

    public void setDaotaiID(String daotaiID) {
        this.daotaiID = daotaiID;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getMsgCalled() {
        return msgCalled;
    }

    public void setMsgCalled(String msgCalled) {
        this.msgCalled = msgCalled;
    }
}
