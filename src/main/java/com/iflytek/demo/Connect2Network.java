package com.iflytek.demo;

import android.app.Activity;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.SocketException;
import java.util.Scanner;

/**
 * 开启一个线程，进行网络连接
 */
public class Connect2Network extends Activity implements Runnable {

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
            if (socket != null) {
                break;
            }
        }
        return socket;
    }

    @Override
    public void run() {
        String host = "192.168.120.133";
        int port = 50007;

        Socket socket = null;

        OutputStream outputStream = null;
        Scanner scanner = new Scanner(System.in);
        int n = 0;

        while (true) {    // 最外层循环控制万一断线自动重连
            try {
                socket = conn(host, port);
                socket.setTcpNoDelay(true);    // 关闭Nagle算法，即不管数据包多小，都要发出去（在交互性高的应用中用）
                outputStream = socket.getOutputStream();
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }

            while (true) {
                System.out.print("Please Enter: ");
//                String s = scanner.nextLine();
                String s = "上哪坐车啊" + n;
                try {
                    outputStream.write(s.getBytes("utf-8"));    // 如果因客户端掉线而报错，则会跳出最内层循环，catch中关掉socket和outputStream后重新进行外层循环
                    System.out.println(System.currentTimeMillis() + " " + s);
                    outputStream.flush();    // 清空缓冲区
                    n += 1;
                    Thread.sleep(500);
                } catch (IOException e) {
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
                    continue;    //跳出内层循环
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
