package com.example.ai_pet_agent.controller;

import com.example.ai_pet_agent.dto.ChatRequest;
import com.example.ai_pet_agent.dto.ChatResponse;
import com.example.ai_pet_agent.service.ChatService;
import com.example.ai_pet_agent.dto.ChatResponse;
import com.example.ai_pet_agent.llm.LlmService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import com.example.ai_pet_agent.service.AgentService;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ChatControllerTest{
    @Test
    void testChatReturnsLlmReply(){
        AgentService agentService = mock(AgentService.class);
        ChatService chatService = new ChatService(agentService);

        when(agentService.chat("你好"))
                .thenReturn("你好，我是旺仔！");


        ChatResponse response = chatService.chat("你好");


        assertEquals("success",response.status());
        assertEquals("你好，我是旺仔！",response.reply());

        verify(agentService).chat("你好");
    }

   /* @Test
    void testBlankMessage(){
        ChatService chatService = new ChatService();
        ChatController controller = new ChatController(chatService);

        ChatRequest request = new ChatRequest("  ");

        ResponseEntity<ChatResponse> response =
                controller.chat(request);

        assertEquals(400,response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("error",response.getBody().status());
    }

    @Test
    void testInvalidMessageDoesNotCallService(){
        ChatService chatService = mock(ChatService.class);
        ChatController controller = new ChatController(chatService);
        ChatRequest request = new ChatRequest ("");
        ResponseEntity<ChatResponse> response = controller.chat(request);
        assertEquals(400,response.getStatusCode().value());
        verifyNoInteractions(chatService);

    }*/

   /* @Test
    void testMockNormalMessage(){
        ChatService chatService = mock(ChatService.class);
        when(chatService.chat("你好"))
            .thenReturn(new ChatResponse("模拟回复", "success"));
        ChatController controller = new ChatController(chatService);
        ChatRequest request = new ChatRequest("你好");
        ResponseEntity<ChatResponse> response =
                controller.chat(request);
        assertEquals(200,response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("模拟回复",response.getBody().reply());
        verify(chatService).chat("你好");
    }*/
}