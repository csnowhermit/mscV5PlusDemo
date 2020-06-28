package com.iflytek.demo;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;

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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

/**
 * Iat，无ui版
 * 20200604 create
 * Iat识别完之后发送到语义端
 */
public class IatActivity2Semantics extends Activity implements View.OnClickListener {
    private static String TAG = "demo.IatActivity2Semantics";
    private SpeechRecognizer mIat;    // 无ui对象
    private long freq = 0;    //手动重调的次数
    private static StringBuffer lastResult = new StringBuffer("");
    private String host = "192.168.120.133";
    private int port = 50007;
    private Socket socket;    // 子线程中开启，与语义端通信
    private OutputStream outputStream = null;
    private boolean isError = false;    // 是否出错，运行到onError()方法视为出错。默认为false，没有出错
    private long n = 0;    // 计数，发送了多少次
    private Thread socketThread = null;    // 子线程，做与语义端的通信
    private boolean nowLast = false;    // 一个识别周期是否到了最后，通过isLast控制

    private InitListener mInitListener = new InitListener() {
        @Override
        public void onInit(int code) {
            Log.d(TAG, "SpeechRecognizer init() code = " + code);
            if (code != ErrorCode.SUCCESS) {
                Log.e(TAG, "初始化失败，错误码：" + code);
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
        }

        @Override
        public void onError(SpeechError error) {
            // 错误码：10118(您没有说话)，可能是录音机权限被禁，需要提示用户打开应用的录音权限。
            Log.d(TAG, error.getPlainDescription(true));
            isError = true;    //true，代表出错了
            lastResult.append(error.getErrorCode() + ", " + error.getErrorDescription());    // 测试阶段，将报错信息也发送到语义端

            System.out.println("==============================================");
            if (lastResult.length() == 0) {
                System.out.println("onError  " + isError + ", lastRestlt: null" + ", length: " + lastResult.length());
            }else{
                System.out.println("onError  " + isError + ", lastRestlt: " + lastResult + ", length: " + lastResult.length());
            }
            System.out.println("==============================================");

            Message message = Message.obtain();
            message.what = 1;
            message.obj = lastResult.toString();

            socketHandler.sendMessage(message); //发送消息

//            lastResult.delete(0, lastResult.length());    // 发送结束后清空字符串
        }

        @Override
        public void onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech");
            System.out.println(TAG + " onEndOfSpeech");
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            int ret = mIat.startListening(mRecognizerListener);    // 无ui模式启动
            freq += 1;
            if (ret != ErrorCode.SUCCESS) {
                System.out.println("听写失败，错误码：" + ret);
            } else {
                System.out.println(TAG + " 开始识别，并设置监听器 " + freq);
            }
        }


        @Override
        public void onResult(RecognizerResult results, boolean isLast) {
            String text = JsonParser.parseIatResult(results.getResultString());

            lastResult.append(text);
            System.out.println("当前内容：" + lastResult.toString());
            System.out.println("+++++++++++++++++++++++++++++++++++++++++++++++");

            if (isLast) {
                nowLast = true;
                Log.d(TAG, "说话内容：" + lastResult.toString());
                System.out.println("===============================================");
                System.out.println(TAG + " 说话内容：" + lastResult.toString());    // lastResult能拿到最后的识别结果

                // 发送到语义端
                Message message = Message.obtain();
                message.what = 1;
                message.obj = lastResult.toString();

                socketHandler.sendMessage(message); //发送消息
            }
        }

        @Override
        public void onVolumeChanged(int volume, byte[] data) {
//            Log.d(TAG, "当前音量大小：" + volume + "，返回音频数据：" + data.length);
            System.out.println(TAG + " 当前音量大小：" + volume + "，返回音频数据：" + data.length);
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
            // 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
            // 若使用本地能力，会话id为null
            if (SpeechEvent.EVENT_SESSION_ID == eventType) {
                String sid = obj.getString(SpeechEvent.KEY_EVENT_AUDIO_URL);
                Log.d(TAG, "session id =" + sid);
            }
        }
    };

    private Handler socketHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            switch (msg.what)
            {
                case -1:
//                    Toast.makeText(IatActivity.this, msg.obj.toString(), Toast.LENGTH_SHORT).show();
                    break;
                case 1:
//                    Toast.makeText(IatActivity.this, msg.obj.toString(), Toast.LENGTH_SHORT).show();
                    try {
//                        if (socket == null){
//                            socket = conn(host, port);    // 这里不能new Socket(),android.os.NetworkOnMainThreadException
//                            outputStream = socket.getOutputStream();
//                        }
//                        outputStream.write(lastResult.toString().getBytes("utf-8"));    //主线程里不能执行，否则报错android.os.NetworkOnMainThreadException
//                        System.out.println(System.currentTimeMillis() + " " + lastResult.toString());
//                        outputStream.flush();    // 清空缓冲区
//                        n += 1;
//                        lastResult.delete(0, lastResult.length());    // 发送结束后清空字符串
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
            }
            return false;
        }
    });


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SpeechUtility.createUtility(IatActivity2Semantics.this, "appid="+getString(R.string.app_id));
        mIat = SpeechRecognizer.createRecognizer(this, mInitListener);

        mIat.setParameter(SpeechConstant.CLOUD_GRAMMAR, null);
        mIat.setParameter(SpeechConstant.SUBJECT, null);
        mIat.setParameter(SpeechConstant.RESULT_TYPE, "json");
        mIat.setParameter(SpeechConstant.ENGINE_TYPE, "local");
        mIat.setParameter(ResourceUtil.ASR_RES_PATH, getResourcePath());    // 添加本地资源
        mIat.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
        mIat.setParameter(SpeechConstant.ACCENT, "mandarin");
        mIat.setParameter(SpeechConstant.VAD_BOS, "4000");
        mIat.setParameter(SpeechConstant.VAD_EOS, "1000");    //原为1000，改为4000原因：避免因乘客说话间停顿
        mIat.setParameter(SpeechConstant.ASR_PTT,"1");

        System.out.println(host);
        System.out.println(port);


        socketThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {    // 最外层循环控制万一断线自动重连
                    try {
                        socket = conn(host, port);
                        socket.setTcpNoDelay(true);    // 关闭Nagle算法，即不管数据包多小，都要发出去（在交互性高的应用中用）
                        if (socket != null) {
                            outputStream = socket.getOutputStream();

                            if (outputStream != null){
                                System.out.println("++++++++++++++++++++ socket: " + socket);
                                System.out.println("++++++++++++++++++++ outputStream: " + outputStream);
                                break;
                            }
                        }

                        // 用子线程进行发送
                        while (true){
                            if (nowLast == true){
                                synchronized (this){
                                    outputStream.write(lastResult.toString().getBytes("utf-8"));    //主线程里不能执行，否则报错android.os.NetworkOnMainThreadException
                                    System.out.println(System.currentTimeMillis() + " " + lastResult.toString());
                                    outputStream.flush();    // 清空缓冲区
                                    n += 1;
                                    lastResult.delete(0, lastResult.length());    // 发送结束后清空字符串
                                    System.out.println("*************************@Override run nowLast == true*************************");
                                }
                                nowLast = false;
                            }
                        }

                    } catch (InterruptedException | IOException e) {
                        e.printStackTrace();
                        try {
                            if (socket != null){
                                socket.close();
                            }
                            if (outputStream != null){
                                outputStream.close();
                            }
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                        continue;
                    }
                }
            }
        });
        socketThread.start();    // 启动子线程

        try {
            Thread.sleep(5000);    // 等网络ok了再开始听写
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

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

    private Socket conn(String host, int port) throws InterruptedException {
        Socket socket = null;
        int n = 0;    // 重试次数

        while (true) {
            try {
                socket = new Socket(host, port);
                System.out.println(host + ":" + port + " connected, retry: " + n);
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println(n * 2 * 1000);
                Thread.sleep(n * 2 * 1000);
                n++;
            }
            if (socket != null){
                return socket;
            }
        }
//        return socket;
    }
}
