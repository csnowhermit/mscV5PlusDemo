package com.iflytek.driver;

import android.telephony.PhoneStateListener;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;

/**
 * 创建socket线程
 */
public class SocketThread extends Thread {
    private String host;
    private int port;
    private Socket socket = null;
    private OutputStream outputStream = null;
    private String msg;

    public SocketThread(String host, int port){
        this.host = host;
        this.port = port;
    }

    public SocketThread(String host, int port, String msg){
        this.host = host;
        this.port = port;
        this.msg = msg;
    }

    public SocketThread(String msg){
        this.msg = msg;
    }

    public void say(String msg) throws IOException {
        System.out.println("SocketThread.say: " + msg);
        outputStream.write(msg.getBytes("utf-8"));
        System.out.println(System.currentTimeMillis() + " " + msg);
        outputStream.flush();
    }

    private Socket conn(String host, int port) throws InterruptedException {
        int n = 0;    // 重试次数

        while (true) {
            try {
                this.socket = new Socket(this.host, this.port);
                this.socket.setTcpNoDelay(true);    // 关闭Nagle算法，即不管数据包多小，都要发出去（在交互性高的应用中用）
                this.outputStream = this.socket.getOutputStream();

                System.out.println(host + ":" + port + " connected, retry: " + n);
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println(n * 2 * 1000);
                Thread.sleep(n * 2 * 1000);
                n++;
            }
        }
    }

    @Override
    public void run() {
        try {
            if (this.socket == null || this.outputStream == null) {
                conn(host, port);
            }

            say(msg);
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
    }
}
