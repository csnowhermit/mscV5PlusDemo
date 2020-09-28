package com.iflytek.nio;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
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
import com.iflytek.driver.MsgPacket;
import com.iflytek.mscv5plusdemo.R;
import com.iflytek.speech.util.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

/**
 * bio socket服务端
 */
public class SocketServer extends Activity implements View.OnClickListener {
    private static String TAG = "nio.BIOSocketServer2Semantics";
    private static String daotai_id = "center01";    //导台ID，标识不同朝向的
    private SpeechRecognizer mIat;    // 无ui对象

    // 连接语义端的
    private String host = "192.168.0.27";
    private int port = 50007;    // 语义端ip及端口
    private Socket socket;    // 子线程中开启，与语义端通信
    private OutputStream outputStream;

    // 在本地接受后端“开始听写”信号的
    private int listenedPort = 50008;
    private InetSocketAddress localAddress;    // 服务器地址

    // 开启监听
    public void startListening() {
        mIat.startListening(mRecognizerListener);
        System.out.println("================== 开始听写 ==================");
        // 通过socket发送到语义端
        MsgPacket msgPacket = new MsgPacket(daotai_id, "开始听写", System.currentTimeMillis(), "onBeginOfSpeech");
        send2Semantics(msgPacket);
    }

    public void action(){
        if (!mIat.isListening()) {    // 如果当前没有监听中，则开启监听
            startListening();
        }else{
            stopListening();
            startListening();
        }
    }

    // 停止监听
    public void stopListening() {
        if (mIat.isListening()) {
            mIat.stopListening();
        }
        System.out.println("================== 停止听写 ==================");
        // 通过socket发送到语义端
        MsgPacket msgPacket = new MsgPacket(daotai_id, "停止听写", System.currentTimeMillis(), "onEndOfSpeech");
        send2Semantics(msgPacket);
//        closeConn();    // 停止听写后关闭连接
    }

    // 发送到语义端
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
                    this.socket.setTcpNoDelay(true);    // 关闭Nagle算法，避免粘包，即不管数据包多小，都要发出去（在交互性高的应用中用）
                    this.outputStream = this.socket.getOutputStream();
                    System.out.println(host + ":" + port + " have connected");
                    if (this.outputStream != null) {
                        break;
                    }
                }
                System.out.println(host + ":" + port + " connected, retry: " + n);
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, String.valueOf(e));
                System.out.println(n);
                Thread.sleep(2 * 1000);    // 掉线自动重连，延时2s
                n++;
            }
        }

        try {
            MsgPacket msgPacket = new MsgPacket(daotai_id, socket.toString(), System.currentTimeMillis(), "conn");
            outputStream.write(JSON.toJSONString(msgPacket).getBytes("utf-8"));
            outputStream.flush();
            System.out.println(String.format("%s %s %s", msgPacket.getMsgCalled(), System.currentTimeMillis(), JSON.toJSONString(msgPacket)));
            Log.d(TAG, msgPacket.getMsgCalled() + System.currentTimeMillis() + JSON.toJSONString(msgPacket));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 关闭连接
    public void closeConn() {
        try {
            // 发送到语义端
            String msg = "客户端已主动断开连接";
            MsgPacket msgPacket = new MsgPacket(daotai_id, msg, System.currentTimeMillis(), "onCloseConn");
            outputStream.write(JSON.toJSONString(msgPacket).getBytes("utf-8"));
            outputStream.flush();

            if (this.outputStream != null) {
                this.outputStream.close();
            }
            if (this.socket != null) {
                this.socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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

    // 听写监听器
    private RecognizerListener mRecognizerListener = new RecognizerListener() {

        @Override
        public void onBeginOfSpeech() {
            // 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
            Log.d(TAG, "onBeginOfSpeech");
            System.out.println(TAG + "onBeginOfSpeech");

            // 发送到语义端
            MsgPacket msgPacket = new MsgPacket(daotai_id, TAG + " 开始听写", System.currentTimeMillis(), "onBeginOfSpeech");
            send2Semantics(msgPacket);
        }

        @Override
        public void onError(SpeechError error) {
            // 错误码：10118(您没有说话)，可能是录音机权限被禁，需要提示用户打开应用的录音权限。
            // 错误码：23008(本地引擎错误)，离线语音识别最长支持20s，超时则报该错

            Log.d(TAG, error.getPlainDescription(true));
            String errorinfo = TAG + ", " + error.getErrorCode() + ", " + error.getErrorDescription();    // 测试阶段，将报错信息也发送到语义端

            System.out.println("==============================================");
            System.out.println("onError , errorinfo: " + errorinfo + ", length: " + errorinfo.length());
            System.out.println("==============================================");

            // 发送到语义端
            MsgPacket msgPacket = new MsgPacket(daotai_id, errorinfo, System.currentTimeMillis(), "onError");
            send2Semantics(msgPacket);
            if (!"10118".equals(String.valueOf(error.getErrorCode()))){
                stopListening();    // 停止监听
            }
        }

        @Override
        public void onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
            Log.d(TAG, "onEndOfSpeech");
            System.out.println(TAG + "onEndOfSpeech");

            // 发送到语义端
            MsgPacket msgPacket = new MsgPacket(daotai_id, TAG + " 停止听写", System.currentTimeMillis(), "onEndOfSpeech");
            send2Semantics(msgPacket);
            stopListening();
        }

        @Override
        public void onResult(RecognizerResult results, boolean isLast) {
            String json = results.getResultString();
            String text = JsonParser.parseIatResult(json);    // 当前分片识别结果，非json

            // 分片，一块一块发送
            System.out.println("当前内容：" + text);
            System.out.println("-----------------------------------------------");
            // 发送到语义端
            MsgPacket msgPacket = new MsgPacket(daotai_id, text, System.currentTimeMillis(), "onResult");
            send2Semantics(msgPacket);

            if (isLast) {
                //TODO 最后的结果
            }
        }

        @Override
        public void onVolumeChanged(int volume, byte[] data) {
            String volumeChangedInfo = String.format("%s, 当前音量大小：%d, 返回音频数据：%d", TAG, volume, data.length);
            System.out.println(volumeChangedInfo);

            // 发送到语义端
            MsgPacket msgPacket = new MsgPacket(daotai_id, volumeChangedInfo, System.currentTimeMillis(), "onVolumeChanged");
            send2Semantics(msgPacket);
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
            // 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
            // 若使用本地能力，会话id为null
            Log.d(TAG, "onEvent enter");
            //以下代码用于调试，如果出现问题可以将sid提供给讯飞开发者，用于问题定位排查
            if (eventType == SpeechEvent.EVENT_SESSION_ID) {
                String sid = obj.getString(SpeechEvent.KEY_EVENT_AUDIO_URL);
                Log.d(TAG, "sid==" + sid);
            } else if (eventType == SpeechEvent.EVENT_SESSION_END) {
                Log.d(TAG, "SpeechEvent.EVENT_SESSION_END: " + SpeechEvent.EVENT_SESSION_END);
                if (mIat.isListening()) {
                    mIat.stopListening();
                }
                System.out.println("================== onEvent SpeechEvent.EVENT_SESSION_END 停止听写 ==================");
                // 通过socket发送到语义端
                MsgPacket msgPacket = new MsgPacket(daotai_id, "SpeechEvent.EVENT_SESSION_END 停止听写", System.currentTimeMillis(), "onEndOfSpeech");
                send2Semantics(msgPacket);
//                closeConn();    // 停止听写后关闭连接
            }
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestPermissions();    // 请求权限

        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectDiskReads().detectDiskWrites().detectNetwork()
                .penaltyLog().build());
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects().detectLeakedClosableObjects()
                .penaltyLog().penaltyDeath().build());


        SpeechUtility.createUtility(SocketServer.this, "appid=" + getString(R.string.app_id));
        mIat = SpeechRecognizer.createRecognizer(this, mInitListener);

        mIat.setParameter(SpeechConstant.CLOUD_GRAMMAR, null);
        mIat.setParameter(SpeechConstant.SUBJECT, null);
        mIat.setParameter(SpeechConstant.RESULT_TYPE, "json");
        mIat.setParameter(SpeechConstant.ENGINE_TYPE, "local");
//        System.out.println("++++getResourcePath():" + getResourcePath());
        mIat.setParameter(ResourceUtil.ASR_RES_PATH, getResourcePath());    // 添加本地资源
        mIat.setParameter(SpeechConstant.LANGUAGE, "zh_cn");    //设置输入语言
        mIat.setParameter(SpeechConstant.ACCENT, "mandarin");    //设置结果返回语言，mandarin为普通话
        mIat.setParameter(SpeechConstant.VAD_BOS, "4000");    //前端点检测
        mIat.setParameter(SpeechConstant.VAD_EOS, "1000");    //后端点检测。原为1000，改为4000原因：避免因乘客说话间停顿
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

        try {
            start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.N)
    public void start() throws IOException {
        ServerSocket server = new ServerSocket(listenedPort);

        // server将一直等待连接的到来
        System.out.println("server将一直等待连接的到来");
        Socket socket = server.accept();
        // 建立好连接后，从socket中获取输入流，并建立缓冲区进行读取
        InputStream inputStream = socket.getInputStream();
        byte[] bytes = new byte[1024];
        int len;
        while (true){
            len = inputStream.read(bytes);
            if (len != -1){
                String s = new String(bytes, 0, len,"UTF-8");
                System.out.println("get message from client: " + s);
                if ("startIAT".equals(s)){
                    action();
                }
            }


        }

    }

    private void requestPermissions(){
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int permission = ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE);
                if(permission!= PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,new String[] {
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.LOCATION_HARDWARE,Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.WRITE_SETTINGS,Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.RECORD_AUDIO,Manifest.permission.READ_CONTACTS},0x0010);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
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
