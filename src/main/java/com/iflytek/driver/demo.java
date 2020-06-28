package com.iflytek.driver;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.IOException;

public class demo extends Activity {
    private SocketThread socketThread = null;
    private StringBuffer lastResult = new StringBuffer("Hello World-");

    private Handler socketHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            switch (msg.what)
            {
                case -1:
                    break;
                case 1:
                    try {
                        System.out.println(lastResult.toString() + System.currentTimeMillis());;
                        socketThread = new SocketThread("192.168.120.133", 50007, lastResult.toString());
                        socketThread.run();
                        socketThread.say(lastResult.toString());
                        lastResult.delete(0, lastResult.length());
                    } catch (IOException e) {
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

//        if (socketThread == null){
//            socketThread = new SocketThread("192.168.120.133", 50007);
//            socketThread.start();
//        }
        Message message = Message.obtain();

        for (int i = 0; i < 10; i++) {
            // 发送到语义端

            message.what = 1;
            message.obj = lastResult.append(i);

            socketHandler.sendMessage(message); //发送消息
            lastResult.delete(0, lastResult.length());
        }


    }
}
