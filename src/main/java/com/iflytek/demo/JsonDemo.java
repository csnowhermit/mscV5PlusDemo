package com.iflytek.demo;

import com.iflytek.speech.util.JsonParser;

/**
 * 讯飞json解析
 */
public class JsonDemo {
    public static void main(String[] args) {
        String jsonStr = "{\n" +
                "      \"sn\":1,\n" +
                "      \"ls\":true,\n" +
                "      \"bg\":0,\n" +
                "      \"ed\":0,\n" +
                "      \"ws\":[{\n" +
                "          \"bg\":0,\n" +
                "          \"cw\":[{\n" +
                "              \"w\":\"我市里。\",\n" +
                "              \"sc\":0\n" +
                "            }],\n" +
                "          \"slot\":\"WFST\"\n" +
                "        }],\n" +
                "      \"sc\":0\n" +
                "    }";
        String result = JsonParser.parseIatResult(jsonStr);
        System.out.println(result);
    }

}
