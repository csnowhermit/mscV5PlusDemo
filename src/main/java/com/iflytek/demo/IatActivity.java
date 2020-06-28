package com.iflytek.demo;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;

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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;


/**
 * Iat，无ui版
 * 20200602 create
 * Note: 启动即识别，不调用onClick()方法。循环流程没问题了（20200602调通）
 */
public class IatActivity extends Activity implements OnClickListener {
    private static String TAG = "IatActivity";
    private SpeechRecognizer mIat;    // 无ui对象
    private long freq = 0;    //手动重调的次数
    private StringBuffer lastResult = new StringBuffer("");
    private String host = "192.168.120.133";
    private int port = 50007;
    private boolean isError = false;    // 是否出错，运行到onError()方法视为出错。默认为false，没有出错

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
            System.out.println("==============================================");
            if (lastResult.length() == 0) {
                System.out.println("onError  " + isError + ", lastRestlt: null" + ", length: " + lastResult.length());
            }else{
                System.out.println("onError  " + isError + ", lastRestlt: " + lastResult + ", length: " + lastResult.length());
            }
            System.out.println("==============================================");
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
                Log.d(TAG, "说话内容：" + lastResult.toString());
                System.out.println("===============================================");
                System.out.println(TAG + " 说话内容：" + lastResult.toString());    // lastResult能拿到最后的识别结果

                lastResult.delete(0, lastResult.length());    // 输出结果后清空字符串
                // 发送到语义识别端

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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SpeechUtility.createUtility(IatActivity.this, "appid="+getString(R.string.app_id));
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
