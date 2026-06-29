package com.voluntary.chat.server.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 本地模式健康检查控制器
 * 客户包通过此端点检测内嵌后端是否就绪
 */
@RestController
@RequestMapping("/api/local")
public class LocalController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "timestamp", LocalDateTime.now().toString(),
                "mode", "local"
        ));
    }
}
