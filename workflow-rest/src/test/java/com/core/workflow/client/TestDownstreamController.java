package com.core.workflow.client;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Mock downstream REST controller used for testing WebClientHelper loopback operations.
 */
@RestController
@RequestMapping("/test-downstream")
public class TestDownstreamController {

    @GetMapping("/success")
    public Map<String, String> getSuccess() {
        return Map.of("status", "ok");
    }

    @PostMapping("/echo")
    public Map<String, String> postEcho(@RequestBody Map<String, String> body) {
        return Map.of("echo", body.getOrDefault("input", ""));
    }

    @GetMapping("/error-400")
    public ResponseEntity<?> get400() {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Bad parameters"));
    }

    @GetMapping("/error-500")
    public ResponseEntity<?> get500() {
        return ResponseEntity.status(500)
                .body(Map.of("error", "Internal Server Error"));
    }

    @GetMapping("/delay")
    public Map<String, String> getDelay(@RequestParam("ms") long ms) throws InterruptedException {
        Thread.sleep(ms);
        return Map.of("status", "delayed");
    }
}
