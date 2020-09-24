package com.iflytek.nio;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.view.View;

import com.iflytek.cloud.SpeechUtility;
import com.iflytek.mscv5plusdemo.R;

public class IAT_Test_JNIO extends Activity implements View.OnClickListener {
    @Override
    public void onClick(View v) { }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SpeechUtility.createUtility(IAT_Test_JNIO.this, "appid=" + getString(R.string.app_id));

        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectDiskReads().detectDiskWrites().detectNetwork()
                .penaltyLog().build());
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects().detectLeakedClosableObjects()
                .penaltyLog().penaltyDeath().build());

//        int port = 50008;
//
//        TcpNioServer tcpNioServer = new TcpNioServer(port);
//        tcpNioServer.start();

    }
}
