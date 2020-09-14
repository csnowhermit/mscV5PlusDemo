package com.iflytek.driver;

/**
 * 导台语音端-->语义端发送数据包
 */
public class MsgPacket {
    private String daotaiID;    // 导台ID
    private String sentences;     // 语音识别结果
    private long timestamp;     // 发送时的时间戳
    private String msgCalled;    // 触发者

    public MsgPacket() {
    }

    public MsgPacket(String daotaiID, String sentences, long timestamp, String msgCalled) {
        this.daotaiID = daotaiID;
        this.sentences = sentences;
        this.timestamp = timestamp;
        this.msgCalled = msgCalled;
    }

    public String getDaotaiID() {
        return daotaiID;
    }

    public void setDaotaiID(String daotaiID) {
        this.daotaiID = daotaiID;
    }

    public String getSentences() {
        return sentences;
    }

    public void setSentences(String sentences) {
        this.sentences = sentences;
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
