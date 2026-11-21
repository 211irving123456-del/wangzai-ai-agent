package com.example.ai_pet_agent.device;

import java.io.IOException;
import java.net.http.HttpTimeoutException;

public class Esp32ClientDemo {
    public static void main(String[] agrs) {
        Esp32Client esp32 = new Esp32Client(
            "http://localhost:8080/mock-esp32/expression"
        );

        try{
            DeviceResult result =
                    esp32.showExpression("happy");
            System.out.println(
                "HTTP状态码:" + result.httpStatus()
            );
            System.out.println(
                "服务器回复:" + result.responseBody()
            );

            if(result.httpOk()){
                System.out.println("HTTP请求成功");
            }else{
                System.out.println("设备接口返回错误状态");
            }
        }catch(IllegalArgumentException e){
            System.out.println("参数错误"+e.getMessage());
        }catch(HttpTimeoutException e){
            System.out.println("设备请求超时"+e.getMessage());
        }catch(IOException e){
            System.out.println("网络通信失败");
            System.out.println(
                "异常类型：" + e.getClass().getSimpleName()
            );
            System.out.println(
                "异常信息：" + e.getMessage()
            );
            e.printStackTrace();
        }catch(InterruptedException e){
            Thread.currentThread().interrupt();
            System.out.println("请求被中断");
        }
    }

}