package com.iflytek.demo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechEvent;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.ui.RecognizerDialog;
import com.iflytek.cloud.ui.RecognizerDialogListener;
import com.iflytek.cloud.util.ResourceUtil;
import com.iflytek.mscv5plusdemo.IatDemo;
import com.iflytek.mscv5plusdemo.R;
import com.iflytek.speech.setting.IatSettings;
import com.iflytek.speech.util.JsonParser;

import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechEvent;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.ui.RecognizerDialog;
import com.iflytek.cloud.ui.RecognizerDialogListener;
import com.iflytek.cloud.util.ResourceUtil;
import com.iflytek.mscv5plusdemo.IatDemo;
import com.iflytek.mscv5plusdemo.R;
import com.iflytek.speech.setting.IatSettings;
import com.iflytek.speech.util.JsonParser;

import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

/**
 * main方法，无ui版
 */
public class MainActivity extends Activity implements OnClickListener {
    private static String TAG = "IatDemo";
    private Toast mToast;    // 弹出框，提示信息用
    private SpeechRecognizer mIat;    // 无ui对象
    private RecognizerDialog mIatDialog;    // 有ui对象
    private EditText mResultText;    // 听写结果内容显示区域
    private SharedPreferences mSharedPreferences;
    private String mEngineType = "local";
    private StringBuffer lastResult = new StringBuffer("");

    //    private OutputStream outputStream;
    private boolean speak_finished = false;

    private String host = "10.10.56.122";
    int port = 50007;
    private Socket socket;
    private long freq = 0;    // onEndOfSpeech()中重新调用监听的次数

    @SuppressLint("ShowToast")
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 设置标题栏（无标题）
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.main);
        requestPermissions();
        // 初始化识别无UI识别对象
        // 使用SpeechRecognizer对象，可根据回调消息自定义界面；
        mIat = SpeechRecognizer.createRecognizer(this, mInitListener);

//        // 初始化听写Dialog，如果只使用有UI听写功能，无需创建SpeechRecognizer
//        // 使用UI听写功能，请根据sdk文件目录下的notice.txt,放置布局文件和图片资源
//        mIatDialog = new RecognizerDialog(this,mInitListener);
        mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
        mSharedPreferences = getSharedPreferences(IatSettings.PREFER_NAME, Activity.MODE_PRIVATE);


        MainActivity.SimpleAdapter listitemAdapter = new MainActivity.SimpleAdapter();
//        ((ListView)findViewById(R.id.listview_main)).setAdapter(listitemAdapter);    // 显示功能列表页面
        System.out.println("==================主页==================");
        // 自动按下 “立刻体验语音听写” 按钮，“立刻体验语音停歇” 为第 0 个按钮
        onClick(listitemAdapter.getView(0, null, null));
        System.out.println("==================听写页面==================");
//        // 此时，在 “讯飞听写示例” 页面
//        startIat();    // 自动开始语音听写识别

        setparam();    // 1.先设置param
        System.out.println(host + "---->" + port);

        int ret = mIat.startListening(mRecognizerListener);    // 无ui模式启动
        if (ret != ErrorCode.SUCCESS) {
            showTip("听写失败,错误码：" + ret + ",请点击网址https://www.xfyun.cn/document/error-code查询解决方案");
        } else {
//                    showTip(getString(R.string.text_begin));
            showTip("开始识别，并设置监听器");
        }

        // 至此，启动了一次mIat的监听
        // 识别的重启：在onEndOfSpeech()中重新设置监听
    }

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


    private InitListener mInitListener = new InitListener() {
        @Override
        public void onInit(int code) {
            Log.d(TAG, "SpeechRecognizer init() code = " + code);
            if (code != ErrorCode.SUCCESS) {
//                showTip("初始化失败，错误码：" + code+",请点击网址https://www.xfyun.cn/document/error-code查询解决方案");
                // 这里添加打印日志，初始化失败的日志
                Log.e(TAG, "初始化失败，错误码：" + code + ",请点击网址https://www.xfyun.cn/document/error-code查询解决方案");
            }
        }
    };


    public void setparam() {
        // 清空参数
//        mIat.setParameter(SpeechConstant.PARAMS, null);
        System.out.println("mSharedPreferences: " + mSharedPreferences);
        String lag = mSharedPreferences.getString("iat_language_preference", "mandarin");
        System.out.println("lag: " + lag);
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
        mIat.setParameter(SpeechConstant.VAD_EOS, mSharedPreferences.getString("iat_vadeos_preference", "4000"));

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

    // 读写监听器: mIat.startListening(mRecognizerListener);时用
    private RecognizerListener mRecognizerListener = new RecognizerListener() {
        @Override
        public void onBeginOfSpeech() {
            // 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
            showTip("onBeginOfSpeech: " + freq);
            Log.d(TAG, "onBeginOfSpeech: " + freq);
            System.out.println(TAG + "onBeginOfSpeech: " + freq);
            speak_finished = false;
        }

        @Override
        public void onError(SpeechError error) {
            // Tips：
            // 错误码：10118(您没有说话)，可能是录音机权限被禁，需要提示用户打开应用的录音权限。
            showTip(error.getPlainDescription(true));
            Log.d(TAG, error.getPlainDescription(true));
        }

        @Override
        public void onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
            showTip("onEndOfSpeech");
            Log.d(TAG, "onEndOfSpeech");
            System.out.println(TAG + " onEndOfSpeech");
//            speak_finished = true;    // 表示话已经说完了
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            int ret = mIat.startListening(mRecognizerListener);    // 无ui模式启动
            freq += 1;
            if (ret != ErrorCode.SUCCESS) {
                showTip("听写失败,错误码：" + ret + ",请点击网址https://www.xfyun.cn/document/error-code查询解决方案");
            } else {
                showTip("开始识别，并设置监听器 " + freq);
                System.out.println(TAG + " 开始识别，并设置监听器 " + freq);
            }
        }


        @Override
        public void onResult(RecognizerResult results, boolean isLast) {
            String text = JsonParser.parseIatResult(results.getResultString());
//            mResultText.append(text);
//            mResultText.setSelection(mResultText.length());
            lastResult.append(text);

            if (isLast) {
                //TODO 最后的结果
                // 将lastResult传到语义识别端
                Log.d(TAG, "说话内容：" + lastResult.toString());
                System.out.println("===============================================");
                System.out.println(TAG + " 说话内容：" + lastResult.toString());
                lastResult.delete(0, lastResult.length());    // 输出结果后清空字符串
                // 发送到语义识别端
//                try {
//                    outputStream.write(lastResult.toString().getBytes("utf-8"));
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
            }
        }

        @Override
        public void onVolumeChanged(int volume, byte[] data) {
//            showTip("当前正在说话，音量大小：" + volume);
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

    // onCreate()中直接调用，直接进入 “讯飞听写示例” 页面
    @Override
    public void onClick(View view) {
        Intent intent = null;
        intent = new Intent(this, IatDemo.class);    // 语音听写

        if (intent != null) {
            startActivity(intent);
        }
    }

    private void showTip(final String str) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mToast.setText(str);
                mToast.show();
            }
        });
    }

    // 请求权限
    private void requestPermissions() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int permission = ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE);
                if (permission != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.LOCATION_HARDWARE, Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.WRITE_SETTINGS, Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS}, 0x0010);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //Menu 列表，onCreate()中调用
    String items[] = {"立刻体验语音听写", "立刻体验语法识别", "立刻体验语义理解", "立刻体验语音合成", "立即体验增强版语音合成", "立刻体验语音唤醒", "立刻体验声纹密码"};

    private class SimpleAdapter extends BaseAdapter {
        public View getView(int position, View convertView, ViewGroup parent) {
            if (null == convertView) {
                LayoutInflater factory = LayoutInflater.from(MainActivity.this);
                View mView = factory.inflate(R.layout.list_items, null);
                convertView = mView;
            }
            Button btn = (Button) convertView.findViewById(R.id.btn);
            btn.setOnClickListener(MainActivity.this);
            btn.setTag(position);
            btn.setText(items[position]);
            return convertView;
        }

        @Override
        public int getCount() {
            return items.length;
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }
    }

}

