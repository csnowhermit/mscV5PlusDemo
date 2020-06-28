package com.iflytek.demo;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

public class SocketThread implements Runnable {
    private OutputStream outputStream;
    private String data;
    private String host = "192.168.120.133";
    private int port = 50007;

    public SocketThread(OutputStream outputStream, String data) throws IOException {
        if (outputStream == null){
            this.outputStream = new Socket(host, port).getOutputStream();
        }
        this.outputStream = outputStream;
        this.data = data;
    }

    @Override
    public void run() {
        try {
            outputStream.write(data.getBytes("utf-8"));
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
