package com.iflytek.nio;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
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
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 主线程中跑mIat
 * 开启子线程接受“startIAT”消息：TcpNioServer，收到startIAT消息后转为消息代码1，发送到主线程
 */
public class IatNioSpeech extends Activity implements View.OnClickListener {
    private static final long serialVersionUID = 1L;

    private static String TAG = "offlineIAT";
    private static String daotai_id = "center01";    //导台ID，标识不同朝向的
    private String host = "192.168.0.27";
    private int port = 50007;
    private Socket socket;    //与语义端通信
    private OutputStream outputStream;    // 网络输出流

    private SpeechRecognizer mIat;    // 语音听写对象
    Handler mainHandler = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // StrictMode线程策略
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectDiskReads().detectDiskWrites().detectNetwork()
                .penaltyLog().build());    // .penaltyLog()，当触发违规条件时，记日志
        // StrictMode虚拟机策略
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects().detectLeakedClosableObjects()
                .penaltyLog().penaltyDeath().build());


        SpeechUtility.createUtility(IatNioSpeech.this, "appid=" + getString(R.string.app_id));
        mIat = SpeechRecognizer.createRecognizer(this, mInitListener);

        mIat.setParameter(SpeechConstant.SAMPLE_RATE, "16000");    //设置采样率
        mIat.setParameter(SpeechConstant.CLOUD_GRAMMAR, null);
        mIat.setParameter(SpeechConstant.SUBJECT, null);
        mIat.setParameter(SpeechConstant.RESULT_TYPE, "json");
        mIat.setParameter(SpeechConstant.ENGINE_TYPE, "local");
//        System.out.println("++++getResourcePath():" + getResourcePath());
        mIat.setParameter(ResourceUtil.ASR_RES_PATH, getResourcePath());    // 添加本地资源
        mIat.setParameter(SpeechConstant.LANGUAGE, "zh_cn");    //设置输入语言
        mIat.setParameter(SpeechConstant.ACCENT, "mandarin");    //设置结果返回语言，mandarin为普通话
        mIat.setParameter(SpeechConstant.VAD_BOS, "4000");    //前端点检测
        mIat.setParameter(SpeechConstant.VAD_EOS, "4000");    //后端点检测。原为1000，改为4000原因：避免因乘客说话间停顿
        mIat.setParameter(SpeechConstant.ASR_PTT, "1");

//        System.out.println("~~~~SpeechConstant.SAMPLE_RATE:" + SpeechConstant.SAMPLE_RATE + mIat.getParameter(SpeechConstant.SAMPLE_RATE));

//        try {
//            conn(host, port);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//        System.out.println("已连接至语义端");

        Thread thread = new Thread(new TcpNioServer());
        thread.start();    // 打开子线程

        mainHandler = new Handler(){    // 接受子线程发来的消息，并进行处理
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                int sign = msg.arg1;
                if (sign == 1) {
                    System.out.println(String.format("收到信号 %d，开启监听", sign));
                    action();
                } else if (sign == 2){
                    System.out.println(String.format("收到信号 %d，停止监听", sign));
                    stopListening();    // 停止监听
                }
                else {
                    System.out.println(String.format("信号错误：%d", sign));
                }
            }
        };

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


    // 连接到语义端服务
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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 关闭连接
    public void closeConn(){
        try {
//            // 发送到语义端
//            String msg = "客户端已主动断开连接";
//            MsgPacket msgPacket = new MsgPacket(daotai_id, msg, System.currentTimeMillis(), "onCloseConn");
//            outputStream.write(JSON.toJSONString(msgPacket).getBytes("utf-8"));
//            outputStream.flush();

            if (this.outputStream != null) {
                this.outputStream.close();
            }
            if (this.socket != null){
                this.socket.close();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 发送到语义端
     *
     * @param msgPacket
     */
    public void send2Semantics(MsgPacket msgPacket) {
        try {
            conn(host, port);

            outputStream.write(JSON.toJSONString(msgPacket).getBytes("utf-8"));
            outputStream.flush();
            System.out.println(String.format("%s %s %s", msgPacket.getMsgCalled(), System.currentTimeMillis(), JSON.toJSONString(msgPacket)));

            closeConn();
        } catch (SocketException e) {    // 报java.net.SocketException: Connection reset错，说明服务端掉线，这时客户端应自动重连
//            try {
//                if (outputStream != null) {
//                    outputStream.close();
//                }
//                if (socket != null) {
//                    socket.close();
//                }
//                conn(host, port);
//
////                // 重连后
////                outputStream.write(JSON.toJSONString(msgPacket).getBytes("utf-8"));
////                outputStream.flush();
////                System.out.println(msgPacket.getMsgCalled() + System.currentTimeMillis() + JSON.toJSONString(msgPacket));
////                Log.d(TAG, msgPacket.getMsgCalled() + System.currentTimeMillis() + JSON.toJSONString(msgPacket));
//
//            } catch (InterruptedException ex) {
//                ex.printStackTrace();
//            } catch (IOException ex) {
//                ex.printStackTrace();
//            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void action() {
        if (!mIat.isListening()) {    // 如果当前没有监听中，则开启监听
            startListening();
        }else{
            stopListening();
            startListening();
        }
    }

    // 开启监听
    public void startListening(){
        mIat.startListening(mRecognizerListener);
        System.out.println("================== 开始听写 ==================");
        // 通过socket发送到语义端
        MsgPacket msgPacket = new MsgPacket(daotai_id, "开始听写", System.currentTimeMillis(), "onBeginOfSpeech");
        send2Semantics(msgPacket);
    }

    // 停止监听
    public void stopListening(){
        if (mIat.isListening()) {
            mIat.stopListening();
        }
        System.out.println("================== 停止听写 ==================");
        // 通过socket发送到语义端
        MsgPacket msgPacket = new MsgPacket(daotai_id, "停止听写", System.currentTimeMillis(), "onEndOfSpeech");
        send2Semantics(msgPacket);
    }

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
            String errorinfo = TAG + " " + error.getErrorCode() + " " + error.getErrorDescription();    // 测试阶段，将报错信息也发送到语义端

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

//            // 发送到语义端
//            MsgPacket msgPacket = new MsgPacket(daotai_id, volumeChangedInfo, System.currentTimeMillis(), "onVolumeChanged");
//            send2Semantics(msgPacket);
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

    public SpeechRecognizer getmIat() {
        return mIat;
    }

    public RecognizerListener getRecognizerListener() {
        return mRecognizerListener;
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

    @Override
    public void onClick(View v) {

    }

    // 嵌套子线程，用于接受后端发来的指令：startIAT、stopIAT
    class TcpNioServer extends Thread{
        // 在本地接受后端“开始听写”信号的
        private int listenedPort = 50008;
        private InetSocketAddress localAddress;    // 服务器地址

        @RequiresApi(api = Build.VERSION_CODES.N)
        @Override
        public void run() {
            super.run();
            this.localAddress = new InetSocketAddress(listenedPort);

            Charset charset = Charset.forName("UTF-8");

            ServerSocketChannel serverSocketChannel = null;
            Selector selector = null;
            Random random = new Random();

            try {
                serverSocketChannel = ServerSocketChannel.open();    //打开服务端通道
                serverSocketChannel.configureBlocking(false);    //设置为非阻塞
                selector = Selector.open();    //打开选择器

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    serverSocketChannel.bind(localAddress, 100);    //绑定服务器地址，最多可连接100个客户端
                }
                serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);    //将选择器注册到通道，对accept事件感兴趣

                System.out.println("TcpNioServer started successful");
            } catch (IOException e) {
                System.out.println("TcpNioServer started failed: " + e.getMessage());
            }

            try {
                while (!Thread.currentThread().isInterrupted()) {
                    int n = selector.select();    //从选择器中取，如果没有这步，则没有结果
                    if (n == 0) {    //0代表没有任何事件进来
                        continue;
                    }

                    Set<SelectionKey> selectionKeySet = selector.selectedKeys();
                    Iterator<SelectionKey> iterator = (Iterator<SelectionKey>) selectionKeySet.iterator();
                    SelectionKey selectionKey = null;

                    while (iterator.hasNext()) {
                        selectionKey = iterator.next();
                        iterator.remove();    //移除，以免重复取出

                        try {
                            //处理客户端的连接
                            if (selectionKey.isAcceptable()) {
                                SocketChannel socketChannel = serverSocketChannel.accept();   //等到客户端的channel
                                socketChannel.configureBlocking(false);    //设置为非阻塞

                                int interestOps = SelectionKey.OP_READ;    //对读事件感兴趣
                                socketChannel.register(selector, interestOps, new Buffers(256, 256));
                                System.out.println("Server端：accept from: " + socketChannel.getRemoteAddress());
                            }

                            //处理可读消息
                            if (selectionKey.isReadable()) {
                                //先准备好缓冲区
                                Buffers buffers = (Buffers) selectionKey.attachment();
                                ByteBuffer readBuffer = buffers.getReadBuffer();
                                ByteBuffer writeBuffer = buffers.getWriteBuffer();

                                SocketChannel socketChannel = (SocketChannel) selectionKey.channel();

                                socketChannel.read(readBuffer);    //将数据从底层缓冲区读到自定义缓冲区中
                                readBuffer.flip();

                                CharBuffer charBuffer = charset.decode(readBuffer);    //解码接收到的数据
                                String sign = new String(charBuffer.array()).trim();
                                System.out.println("Server端：接收到客户端的消息：" + sign);
                                if ("startIAT".equals(sign)){
                                    Message msg = Message.obtain();
                                    msg.arg1 = 1;    // startIAT对应的信号为1
                                    mainHandler.sendMessage(msg);
                                } else if ("stopIAT".equals(sign)){
                                    Message msg = Message.obtain();
                                    msg.arg1 = 2;    // stopIAT对应的信号为2
                                    mainHandler.sendMessage(msg);
                                } else{

                                }

                                readBuffer.rewind();    //重置缓冲区指针位置

                                //准备给客户端返回数据
                                writeBuffer.put("Echo from Server: ".getBytes("UTF-8"));
                                writeBuffer.put(readBuffer);    //将客户端发来的数据原样返回
                                writeBuffer.put("\n".getBytes("UTF-8"));

                                readBuffer.clear();    //清空读缓冲区
                                selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);    //为通道添加写事件
                            }

                            //处理可写消息
                            if (selectionKey.isWritable()) {
                                Buffers buffers = (Buffers) selectionKey.attachment();
                                ByteBuffer writeBuffer = buffers.getWriteBuffer();
                                writeBuffer.flip();

                                SocketChannel socketChannel = (SocketChannel) selectionKey.channel();

                                int len = 0;
                                while (writeBuffer.hasRemaining()) {
                                    len = socketChannel.write(writeBuffer);
                                    if (len == 0) {    //说明底层socket写缓冲区已写满
                                        break;
                                    }
                                }

                                writeBuffer.compact();

                                if (len != 0) {    //说明数据已全部写入到底层的socket写缓冲区
                                    selectionKey.interestOps(selectionKey.interestOps() & (~SelectionKey.OP_WRITE));    //取消通道的写事件
                                }
                            }
                        } catch (IOException e) {
                            System.out.println("Server encounter client error: " + e.getMessage());
                            selectionKey.cancel();
                            selectionKey.channel().close();
                        }
                    }
                    Thread.sleep(random.nextInt(500));
                }
            } catch (InterruptedException e) {
                System.out.println("serverThread is interrupted: " + e.getMessage());
            } catch (IOException e) {
                System.out.println("serverThread selecotr error: " + e.getMessage());
            } finally {
                try {
                    selector.close();
                } catch (IOException e) {
                    System.out.println("selector close failed");
                } finally {
                    System.out.println("server closed successful");
                }
            }
        }
    }
}

