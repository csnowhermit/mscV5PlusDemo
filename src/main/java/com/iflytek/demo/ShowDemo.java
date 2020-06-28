package com.iflytek.demo;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.Toast;

public class ShowDemo extends Activity implements View.OnClickListener {
    private Toast mToast;
//    private String host = "10.10.56.122";
//    private int port = 50007;

    private void showTip(final String str) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mToast.setText(str);
                mToast.show();
            }
        });
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
//        showTip(host);
//        showTip(String.valueOf(port));
        Connect2Network connect2Network = new Connect2Network();
        Thread myThread = new Thread(connect2Network);
        myThread.start();    // 开启线程，连接语义端

    }

    @Override
    public void onClick(View v) {

    }


}
