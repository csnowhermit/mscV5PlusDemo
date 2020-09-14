package com.iflytek.driver;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;

import com.alibaba.fastjson.JSON;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechEvent;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.SpeechUtility;
import com.iflytek.cloud.util.ResourceUtil;
import com.iflytek.mscv5plusdemo.R;
import com.iflytek.speech.util.JsonParser;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * Iat，无ui版
 * 20200604 create
 * Iat识别完之后发送到语义端
 */
public class IatActivitySocketInMain extends Activity implements View.OnClickListener {
    private static String TAG = "driver.IatActivity2Semantics";
    private static String daotai_id = "center01";    //导台ID，标识不同朝向的
    private SpeechRecognizer mIat;    // 无ui对象
    private long freq = 0;    //手动重调的次数
    private static StringBuffer lastResult = new StringBuffer("");
    private String host = "192.168.0.27";
    private int port = 50007;
    private Socket socket;    // 子线程中开启，与语义端通信
    private OutputStream outputStream;
    private boolean isError = false;    // 是否出错，运行到onError()方法视为出错。默认为false，没有出错
//    private long n = 0;    // 计数，发送了多少次

    private Handler socketHandler = null;

    private InitListener mInitListener = new InitListener() {
        @Override
        public void onInit(int code) {
            Log.d(TAG, "SpeechRecognizer init() code = " + code);

            // 发送到语义端
            MsgPacket msgPacket = new MsgPacket(daotai_id, TAG + " SpeechRecognizer init() code = " + code, System.currentTimeMillis(), "mInitListener-onInit");
            send2Semantics(msgPacket);
            if (code != ErrorCode.SUCCESS) {
                Log.e(TAG, "初始化失败，错误码：" + code);

                // 发送到语义端
                msgPacket = new MsgPacket(daotai_id, TAG + " 初始化失败，错误码：" + code, System.currentTimeMillis(), "mInitListener-onInit-not-ErrorCode.SUCCESS");
                send2Semantics(msgPacket);
            }
        }
    };

    // 读写监听器: mIat.startListening(mRecognizerListener);时用
    private RecognizerListener mRecognizerListener = new RecognizerListener() {
        @Override
        public void onBeginOfSpeech() {
            // 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
            Log.d(TAG, "onBeginOfSpeech: " + freq);
            System.out.println(TAG + "onBeginOfSpeech: " + freq);
            isError = false;

            // 发送到语义端
            MsgPacket msgPacket = new MsgPacket(daotai_id, TAG + " onBeginOfSpeech", System.currentTimeMillis(), "onBeginOfSpeech");
            send2Semantics(msgPacket);
        }

        @Override
        public void onError(SpeechError error) {
            // 错误码：10118(您没有说话)，可能是录音机权限被禁，需要提示用户打开应用的录音权限。
            // 错误码：23008（本地引擎错误），离线语音识别最长支持20s，超时则报该错
            Log.d(TAG, error.getPlainDescription(true));
            isError = true;    //true，代表出错了
            lastResult.append(error.getErrorCode() + ", " + error.getErrorDescription());    // 测试阶段，将报错信息也发送到语义端

            System.out.println("==============================================");
            if (lastResult.length() == 0) {
                System.out.println("onError  " + isError + ", lastRestlt: null" + ", length: " + lastResult.length());
            } else {
                System.out.println("onError  " + isError + ", lastRestlt: " + lastResult + ", length: " + lastResult.length());
            }
            System.out.println("==============================================");

            // 发送到语义端
            MsgPacket msgPacket = new MsgPacket(daotai_id, lastResult.toString(), System.currentTimeMillis(), "onError");
            send2Semantics(msgPacket);
            lastResult.delete(0, lastResult.length());

            // 遇到23008（本地引擎错误）错误，重启mIat
            if ("23008".equals(String.valueOf(error.getErrorCode()))) {
                mIat.stopListening();
                mIat.startListening(mRecognizerListener);

                System.out.println("23008错误，已重启：mIat.startListening(mRecognizerListener)");
                Log.d(TAG, "23008错误，已重启：mIat.startListening(mRecognizerListener)");
                lastResult.append("23008错误，已重启：mIat.startListening(mRecognizerListener)");
                MsgPacket msgPacket1 = new MsgPacket(daotai_id, lastResult.toString(), System.currentTimeMillis(), "onError");
                send2Semantics(msgPacket1);
                lastResult.delete(0, lastResult.length());
            }
        }

        @Override
        public void onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech");
            System.out.println(TAG + " onEndOfSpeech");

            Log.d(TAG, "onEndOfSpeech：" + lastResult.toString());
            System.out.println(TAG + " onEndOfSpeech：" + lastResult.toString());    // lastResult能拿到最后的识别结果

            // 发送到语义端
            MsgPacket msgPacket1 = new MsgPacket(daotai_id, lastResult.toString(), System.currentTimeMillis(), "onResult");
            send2Semantics(msgPacket1);
            lastResult.delete(0, lastResult.length());


            try {
                Thread.sleep(5000);    // 结束当前识别后不要立马重连
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            int ret = mIat.startListening(mRecognizerListener);    // 无ui模式启动
            freq += 1;
            if (ret != ErrorCode.SUCCESS) {
                System.out.println("听写失败，错误码：" + ret);

                // 发送到语义端
                MsgPacket msgPacket = new MsgPacket(daotai_id, TAG + " 听写失败，错误码：" + ret, System.currentTimeMillis(), "onEndOfSpeech-restartListening");
                send2Semantics(msgPacket);
            } else {
                System.out.println(TAG + " 开始识别，并设置监听器 " + freq);

                // 发送到语义端
                MsgPacket msgPacket = new MsgPacket(daotai_id, TAG + " 开始识别，并设置监听器" + freq, System.currentTimeMillis(), "onEndOfSpeech-restartListening");
                send2Semantics(msgPacket);
            }
        }


        @Override
        public void onResult(RecognizerResult results, boolean isLast) {
            String text = JsonParser.parseIatResult(results.getResultString());

            lastResult.append(text);
            System.out.println("当前内容：" + lastResult.toString());
            System.out.println("+++++++++++++++++++++++++++++++++++++++++++++++");

            if (isLast) {
                Log.d(TAG, "说话内容：" + lastResult.toString());
                System.out.println("===============================================");
                System.out.println(TAG + " 说话内容：" + lastResult.toString());    // lastResult能拿到最后的识别结果

                // 发送到语义端
                MsgPacket msgPacket = new MsgPacket(daotai_id, lastResult.toString(), System.currentTimeMillis(), "onResult");
                send2Semantics(msgPacket);
                lastResult.delete(0, lastResult.length());
            }
        }

        @Override
        public void onVolumeChanged(int volume, byte[] data) {
            Log.d(TAG, "当前音量大小：" + volume + "，返回音频数据：" + data.length);
            System.out.println(TAG + " 当前音量大小：" + volume + "，返回音频数据：" + data.length);

            StringBuffer sb = new StringBuffer();
            sb.append(TAG).append(", 当前音量大小：" + volume + "，返回音频数据：" + data.length);
            // 发送到语义端
            MsgPacket msgPacket = new MsgPacket(daotai_id, sb.toString(), System.currentTimeMillis(), "onVolumeChanged");
            send2Semantics(msgPacket);
            lastResult.delete(0, sb.length());
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
            // 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
            // 若使用本地能力，会话id为null
            if (SpeechEvent.EVENT_SESSION_ID == eventType) {
                String sid = obj.getString(SpeechEvent.KEY_EVENT_AUDIO_URL);
                Log.d(TAG, "session id =" + sid);

                // 发送到语义端
                MsgPacket msgPacket = new MsgPacket(daotai_id, TAG + " session id =" + sid, System.currentTimeMillis(), "onEvent");
                send2Semantics(msgPacket);
            }
        }
    };


    /**
     * 发送到语义端
     *
     * @param msgPacket
     */
    public void send2Semantics(MsgPacket msgPacket) {
        try {
            outputStream.write(JSON.toJSONString(msgPacket).getBytes("utf-8"));
            outputStream.flush();
            System.out.println(msgPacket.getMsgCalled() + System.currentTimeMillis() + JSON.toJSONString(msgPacket));
            Log.d(TAG, msgPacket.getMsgCalled() + System.currentTimeMillis() + JSON.toJSONString(msgPacket));
        } catch (SocketException e) {    // 报java.net.SocketException: Connection reset错，说明服务端掉线，这时客户端应自动重连
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
                if (socket != null) {
                    socket.close();
                }
                conn(host, port);

            } catch (InterruptedException ex) {
                ex.printStackTrace();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void conn(String host, int port) throws InterruptedException {
        int n = 0;    // 重试次数

        while (true) {
            try {
                this.socket = new Socket(this.host, this.port);
                if (this.socket != null) {
                    this.socket.setTcpNoDelay(true);    // 关闭Nagle算法，即不管数据包多小，都要发出去（在交互性高的应用中用）
                    this.outputStream = this.socket.getOutputStream();
                    System.out.println(host + ":" + port + " have connected");
                    if (this.outputStream != null) {
                        break;
                    }
                }
                System.out.println(host + ":" + port + " connected, retry: " + n);
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println(n);
                Thread.sleep(2 * 1000);
                n++;
            }
        }

        try {
            MsgPacket msgPacket = new MsgPacket(daotai_id, socket.toString(), System.currentTimeMillis(), "conn");
            outputStream.write(JSON.toJSONString(msgPacket).getBytes("utf-8"));
            outputStream.flush();
            System.out.println(msgPacket.getMsgCalled() + System.currentTimeMillis() + JSON.toJSONString(msgPacket));
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectDiskReads().detectDiskWrites().detectNetwork()
                .penaltyLog().build());
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects().detectLeakedClosableObjects()
                .penaltyLog().penaltyDeath().build());


        SpeechUtility.createUtility(IatActivitySocketInMain.this, "appid=" + getString(R.string.app_id));
//        SpeechUtility.createUtility(IatActivitySocketInMain.this, "appid=5ef7fe15");    // 这里直接写上app_id
        mIat = SpeechRecognizer.createRecognizer(this, mInitListener);

        mIat.setParameter(SpeechConstant.CLOUD_GRAMMAR, null);
        mIat.setParameter(SpeechConstant.SUBJECT, null);
        mIat.setParameter(SpeechConstant.RESULT_TYPE, "json");
        mIat.setParameter(SpeechConstant.ENGINE_TYPE, "local");
//        System.out.println("++++getResourcePath():" + getResourcePath());
        mIat.setParameter(ResourceUtil.ASR_RES_PATH, getResourcePath());    // 添加本地资源
        mIat.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
        mIat.setParameter(SpeechConstant.ACCENT, "mandarin");
        mIat.setParameter(SpeechConstant.VAD_BOS, "4000");    //前端点检测
        mIat.setParameter(SpeechConstant.VAD_EOS, "2000");    //后端点检测。原为1000，改为4000原因：避免因乘客说话间停顿
        mIat.setParameter(SpeechConstant.ASR_PTT, "1");

        System.out.println(host);
        System.out.println(port);
        Log.d(TAG, "host = " + host);
        Log.d(TAG, "port = " + port);

        try {
            conn(host, port);
            Thread.sleep(2000);    // 等网络ok了再开始听写
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("已连接至语义端");
        Log.d(TAG, "已连接至语义端");

        mIat.startListening(mRecognizerListener);
//        onClick(null);
    }

    @Override
    public void onClick(View v) {
        mIat.startListening(mRecognizerListener);
    }


    private String getResourcePath() {
        StringBuffer tempBuffer = new StringBuffer();
        //识别通用资源
        tempBuffer.append(ResourceUtil.generateResourcePath(this, ResourceUtil.RESOURCE_TYPE.assets, "iat/common.jet"));
        tempBuffer.append(";");
        tempBuffer.append(ResourceUtil.generateResourcePath(this, ResourceUtil.RESOURCE_TYPE.assets, "iat/sms_16k.jet"));
        //识别8k资源-使用8k的时候请解开注释
        return tempBuffer.toString();
    }


}
