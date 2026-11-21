package com.example.ai_pet_agent.service;

import com.example.ai_pet_agent.dto.HealthResponse;
import org.springframework.stereotype.Service;

@Service
public class HealthService{

    public HealthResponse getStatus(){
        return new HealthResponse(
            "ok",
            "My java agent is ready!"
        );
    }
}