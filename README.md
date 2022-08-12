# 讯飞离线语音改造（导台语音识别端-跑安卓板）
  导台项目：https://github.com/xbder/daotai-semantics
  
自动循环录循环识别程序入口：com.iflytek.driver.IatActivitySocketInMain

收到信号后启动录程序入口：com.iflytek.nio.IatNioSpeech（主线程跑mIat，子线程开启tcp nio监听接受指令）

​	信号：startIAT-->开始听写

​	信号：stopIAT-->停止听写

在Android studio中打开，在本地的目录：D:/workspace/xunfei_offline/mscV5PlusDemo/
