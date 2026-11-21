package com.example.ai_pet_agent.device;

public record DeviceResult(
    boolean httpOk,
    int httpStatus,
    String responseBody
){

}