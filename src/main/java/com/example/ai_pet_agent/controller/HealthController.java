package com.example.ai_pet_agent.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.ai_pet_agent.service.HealthService;
import com.example.ai_pet_agent.dto.HealthResponse;

@RestController
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService){
        this.healthService = healthService;
    }


    @GetMapping("/api/health")
    public HealthResponse health(){
        return healthService.getStatus();
    }
}