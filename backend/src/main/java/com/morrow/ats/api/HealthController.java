package com.morrow.ats.api;

import com.morrow.ats.service.LmStudioClient;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final LmStudioClient lmStudioClient;

    public HealthController(LmStudioClient lmStudioClient) {
        this.lmStudioClient = lmStudioClient;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "llmEnabled", lmStudioClient.isEnabled());
    }
}