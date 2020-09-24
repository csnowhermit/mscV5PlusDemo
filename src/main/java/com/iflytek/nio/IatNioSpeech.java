package com.iflytek.nio;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

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
import com.iflytek.cloud.ui.RecognizerDialog;
import com.iflytek.cloud.ui.RecognizerDialogListener;
import com.iflytek.cloud.util.ResourceUtil;
import com.iflytek.driver.MsgPacket;
import com.iflytek.mscv5plusdemo.R;
import com.iflytek.speech.setting.IatSettings;
import com.iflytek.speech.util.JsonParser;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * iat nio对象
 */
public class IatNioSpeech extends Activity implements View.OnClickListener {
    private static String TAG = "nio.IatNioSpeech2Semantics";
    private static String daotai_id = "center01";    //导台ID，标识不同朝向的
    private SpeechRecognizer mIat;    // 无ui对象
    private RecognizerDialog mIatDialog;    // 语音听写ui对象
    private EditText mResultText;    // 听写结果内容
    private HashMap<String, String> mIatResults = new LinkedHashMap<String, String>();    // 用HashMap存储听写结果
    private Toast mToast;
    private SharedPreferences mSharedPreferences;
    private String mEngineType = "local";    // 默认引擎设置为本地

    private String host = "192.168.0.27";
    private int port = 50007;    // 语义端ip及端口
    private Socket socket;    // 子线程中开启，与语义端通信
    private OutputStream outputStream;

    /**
     * 初始化Layout。
     */
    private void initLayout() {
        findViewById(R.id.iat_recognize).setOnClickListener(this);    // "开始" 按钮
        findViewById(R.id.iat_recognize_stream).setOnClickListener(this);
        findViewById(R.id.iat_upload_contacts).setOnClickListener(this);
        findViewById(R.id.iat_upload_userwords).setOnClickListener(this);
        findViewById(R.id.iat_stop).setOnClickListener(this);
        findViewById(R.id.iat_cancel).setOnClickListener(this);
        findViewById(R.id.image_iat_set).setOnClickListener(this);


        //选择云端or本地
        RadioGroup group = (RadioGroup) this.findViewById(R.id.iat_radioGroup);
        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.iat_radioCloud) {
                    findViewById(R.id.iat_upload_contacts).setEnabled(true);
                    findViewById(R.id.iat_upload_userwords).setEnabled(true);
                    mEngineType = SpeechConstant.TYPE_CLOUD;
                } else if (checkedId == R.id.iat_radioLocal) {
                    //离线听写不支持联系人/热词上传
                    findViewById(R.id.iat_upload_contacts).setEnabled(false);
                    findViewById(R.id.iat_upload_userwords).setEnabled(false);
                    mEngineType = SpeechConstant.TYPE_LOCAL;
                }
            }
        });
    }

    // 开启监听
    public void startListening() {
        mIat.startListening(mRecognizerListener);
        System.out.println("================== 开始听写 ==================");
        // 通过socket发送到语义端
        MsgPacket msgPacket = new MsgPacket(daotai_id, "开始听写", System.currentTimeMillis(), "onBeginOfSpeech");
        send2Semantics(msgPacket);
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
        closeConn();    // 停止听写后关闭连接
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
            System.out.println(msgPacket.getMsgCalled() + System.currentTimeMillis() + JSON.toJSONString(msgPacket));
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

    /**
     * 初始化监听器。
     */
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

    /**
     * 听写监听器。
     */
    private RecognizerListener mRecognizerListener = new RecognizerListener() {

        @Override
        public void onBeginOfSpeech() {
            // 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
            showTip("开始说话");

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
            showTip(error.getPlainDescription(true));
            // 错误码：10118(您没有说话)，可能是录音机权限被禁，需要提示用户打开应用的录音权限。

            Log.d(TAG, error.getPlainDescription(true));
            String errorinfo = TAG + ", " + error.getErrorCode() + ", " + error.getErrorDescription();    // 测试阶段，将报错信息也发送到语义端

            System.out.println("==============================================");
            if (errorinfo.length() == 0) {
                System.out.println("onError , errorinfo: null" + ", length: " + errorinfo.length());
            } else {
                System.out.println("onError , errorinfo: " + errorinfo + ", length: " + errorinfo.length());
            }
            System.out.println("==============================================");

            // 发送到语义端
            MsgPacket msgPacket = new MsgPacket(daotai_id, errorinfo, System.currentTimeMillis(), "onError");
            send2Semantics(msgPacket);

            if (!"10118".equals(String.valueOf(error.getErrorCode()))) {
                stopListening();
            }

        }

        @Override
        public void onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
            showTip("结束说话");
            stopListening();
        }

        @Override
        public void onResult(RecognizerResult results, boolean isLast) {

            String text = JsonParser.parseIatResult(results.getResultString());    // 当前分片识别结果，非json
            mResultText.append(text);
            mResultText.setSelection(mResultText.length());

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
            showTip("当前正在说话，音量大小：" + volume);
            Log.d(TAG, "返回音频数据：" + data.length);

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
                closeConn();    // 停止听写后关闭连接
            }
        }
    };

    /**
     * 听写UI监听器
     */
    private RecognizerDialogListener mRecognizerDialogListener = new RecognizerDialogListener() {
        public void onResult(RecognizerResult results, boolean isLast) {
            Log.d(TAG, "recognizer result：" + results.getResultString());

            String text = JsonParser.parseIatResult(results.getResultString());
            mResultText.append(text);
            mResultText.setSelection(mResultText.length());

        }

        /**
         * 识别回调错误.
         */
        public void onError(SpeechError error) {
            showTip(error.getPlainDescription(true));
        }
    };

    private void showTip(final String str) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mToast.setText(str);
                mToast.show();
            }
        });
    }

    /**
     * 参数设置
     *
     * @return
     */
    public void setParam() {
        // 清空参数
        mIat.setParameter(SpeechConstant.PARAMS, null);
        String lag = mSharedPreferences.getString("iat_language_preference", "mandarin");
        // 设置引擎
        mIat.setParameter(SpeechConstant.ENGINE_TYPE, mEngineType);
        // 设置返回结果格式
        mIat.setParameter(SpeechConstant.RESULT_TYPE, "json");

        //mIat.setParameter(MscKeys.REQUEST_AUDIO_URL,"true");

        //	this.mTranslateEnable = mSharedPreferences.getBoolean( this.getString(R.string.pref_key_translate), false );
        if (mEngineType.equals(SpeechConstant.TYPE_LOCAL)) {
            // 设置本地识别资源
            mIat.setParameter(ResourceUtil.ASR_RES_PATH, getResourcePath());
        }
        // 在线听写支持多种小语种，若想了解请下载在线听写能力，参看其speechDemo
        if (lag.equals("en_us")) {
            // 设置语言
            mIat.setParameter(SpeechConstant.LANGUAGE, "en_us");
            mIat.setParameter(SpeechConstant.ACCENT, null);

            // 设置语言
            mIat.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
            // 设置语言区域
            mIat.setParameter(SpeechConstant.ACCENT, lag);
        }

        // 设置语音前端点:静音超时时间，即用户多长时间不说话则当做超时处理
        mIat.setParameter(SpeechConstant.VAD_BOS, mSharedPreferences.getString("iat_vadbos_preference", "4000"));

        // 设置语音后端点:后端点静音检测时间，即用户停止说话多长时间内即认为不再输入， 自动停止录音
        mIat.setParameter(SpeechConstant.VAD_EOS, mSharedPreferences.getString("iat_vadeos_preference", "1000"));

        // 设置标点符号,设置为"0"返回结果无标点,设置为"1"返回结果有标点
        mIat.setParameter(SpeechConstant.ASR_PTT, mSharedPreferences.getString("iat_punc_preference", "1"));

        // 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
        mIat.setParameter(SpeechConstant.AUDIO_FORMAT, "wav");
        mIat.setParameter(SpeechConstant.ASR_AUDIO_PATH, Environment.getExternalStorageDirectory() + "/msc/iat.wav");
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

    @SuppressLint("ShowToast")
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.iatdemo);
        initLayout();
        // 初始化识别无UI识别对象
        // 使用SpeechRecognizer对象，可根据回调消息自定义界面；
        mIat = SpeechRecognizer.createRecognizer(this, mInitListener);

        // 初始化听写Dialog，如果只使用有UI听写功能，无需创建SpeechRecognizer
        // 使用UI听写功能，请根据sdk文件目录下的notice.txt,放置布局文件和图片资源
        mIatDialog = new RecognizerDialog(this, mInitListener);

        mSharedPreferences = getSharedPreferences(IatSettings.PREFER_NAME, Activity.MODE_PRIVATE);
        mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
        mResultText = ((EditText) findViewById(R.id.iat_text));

        // onClick()中的内容，直接自动点击“开始听写”按钮
        mResultText.setText(null);    // 清空显示内容
        mIatResults.clear();
        // 设置参数
        setParam();
        boolean isShowDialog = mSharedPreferences.getBoolean(getString(R.string.pref_key_iat_show), true);
        if (isShowDialog) {
            // 显示听写对话框
            mIatDialog.setListener(mRecognizerDialogListener);
            mIatDialog.show();
            showTip(getString(R.string.text_begin));
        } else {
            // 不显示听写对话框
            int ret = mIat.startListening(mRecognizerListener);
            if (ret != ErrorCode.SUCCESS) {
                showTip("听写失败,错误码：" + ret + ",请点击网址https://www.xfyun.cn/document/error-code查询解决方案");
            } else {
                showTip(getString(R.string.text_begin));
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (null != mIat) {
            // 退出时释放连接
            mIat.cancel();
            mIat.destroy();
        }
    }


    @Override
    public void onClick(View v) {
//        mResultText.setText(null);// 清空显示内容
//        mIatResults.clear();
//        // 设置参数
//        setParam();
//        boolean isShowDialog = mSharedPreferences.getBoolean(getString(R.string.pref_key_iat_show), true);
//        if (isShowDialog) {
//            // 显示听写对话框
//            mIatDialog.setListener(mRecognizerDialogListener);
//            mIatDialog.show();
//            showTip(getString(R.string.text_begin));
//        } else {
//            // 不显示听写对话框
//            int ret = mIat.startListening(mRecognizerListener);
//            if (ret != ErrorCode.SUCCESS) {
//                showTip("听写失败,错误码：" + ret+",请点击网址https://www.xfyun.cn/document/error-code查询解决方案");
//            } else {
//                showTip(getString(R.string.text_begin));
//            }
//        }
    }
}

