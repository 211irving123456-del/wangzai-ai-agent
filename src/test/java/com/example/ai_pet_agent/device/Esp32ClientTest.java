package com.example.ai_pet_agent.device;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class Esp32ClientTest{
    @Test
    void testInvalidAction(){
        HttpClient httpClient = mock(HttpClient.class);

        Esp32Client esp32 = new Esp32Client(
            "http://localhost:8080/mock-esp32/expression",
            httpClient
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> esp32.showExpression("dance")
        );

        verifyNoInteractions(httpClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testHttp500() throws Exception{
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse =
                mock(HttpResponse.class);
        when(mockResponse.statusCode())
                .thenReturn(500);
        when(mockResponse.body())
                .thenReturn("{\"status\":\"error\"}");
        when(httpClient.send(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers
                .<HttpResponse.BodyHandler<String>>any()
        )).thenReturn(mockResponse);
        Esp32Client esp32 = new Esp32Client(
                "http://localhost:8080/mock-esp32/expression",
                httpClient
        );
        DeviceResult result = esp32.showExpression("happy");
        assertEquals(500,result.httpStatus());
        assertFalse(result.httpOk());
        assertEquals(
            "{\"status\":\"error\"}",
            result.responseBody()
        );

    }

    @Test
    void testRequestTimeout() throws Exception{
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers
                .<HttpResponse.BodyHandler<String>>any()
        )).thenThrow(
            new HttpTimeoutException("模型设备响应超时")
        );
        Esp32Client esp32 = new Esp32Client(
            "http://localhost:8080/mock-esp32/expression",
            httpClient
        );
        HttpTimeoutException exception = assertThrows(
            HttpTimeoutException.class,
            () -> esp32.showExpression("happy")
        );
        assertEquals(
            "模型设备响应超时",
            exception.getMessage()
        );
        verify(httpClient).send(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers
                .<HttpResponse.BodyHandler<String>>any()
        );
    }
}