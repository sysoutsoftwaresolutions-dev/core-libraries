package com.core.workflow.client;

import com.core.common.client.WebClientHelper;
import com.core.common.exception.CoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class WebClientIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private WebClient.Builder webClientBuilder;

    private WebClientHelper helper;

    @BeforeEach
    public void setup() {
        WebClient localClient = webClientBuilder.baseUrl("http://localhost:" + port).build();
        helper = new WebClientHelper(localClient);
    }

    @Test
    public void testGetSuccess() throws Exception {
        Map<?, ?> response = helper.get("/test-downstream/success", Map.class);
        assertNotNull(response);
        assertEquals("ok", response.get("status"));
    }

    @Test
    public void testPostSuccess() throws Exception {
        Map<String, String> request = Map.of("input", "hello");
        Map<?, ?> response = helper.post("/test-downstream/echo", request, Map.class);
        assertNotNull(response);
        assertEquals("hello", response.get("echo"));
    }

    @Test
    public void testDownstreamClientError() {
        CoreException ex = assertThrows(CoreException.class, () -> {
            helper.get("/test-downstream/error-400", Map.class);
        });
        assertEquals("DOWNSTREAM_CLIENT_ERROR", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("400"));
    }

    @Test
    public void testDownstreamServerError() {
        CoreException ex = assertThrows(CoreException.class, () -> {
            helper.get("/test-downstream/error-500", Map.class);
        });
        assertEquals("DOWNSTREAM_SERVER_ERROR", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("500"));
    }

    @Test
    public void testDownstreamTimeout() {
        // Our configured read/write and response timeout in CoreCommonAutoConfiguration is 10s.
        // We delay for 12 seconds to ensure a read timeout triggers.
        CoreException ex = assertThrows(CoreException.class, () -> {
            helper.get("/test-downstream/delay?ms=12000", Map.class);
        });
        assertEquals("DOWNSTREAM_SERVICE_ERROR", ex.getErrorCode());
    }
}
