package com.example.ai_pet_agent.controller;

import java.util.Map;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MockEsp32Controller {
    private static final Set<String> ALLOWED_ACTIONS =
            Set.of("happy","sad","sleep");

    @PostMapping("/mock-esp32/expression")
    public ResponseEntity<Map<String,String>> setExpression(
            @RequestBody Map<String,String> command) {

                String action = command.get("action");

                if(action == null || !ALLOWED_ACTIONS.contains(action)){

                    return ResponseEntity.badRequest().body(
                        Map.of(
                            "status","error",
                            "message","Unsupported action"
                        )
                    );
                }
                System.out.println("模拟esp32收到动作：" + action);

                return ResponseEntity.ok(
                    Map.of(
                        "status","success",
                        "action",action
                    )
                );
            }
}