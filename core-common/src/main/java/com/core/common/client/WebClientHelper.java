package com.core.common.client;

import com.core.common.context.TenantContext;
import com.core.common.exception.CoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

/**
 * Helper to call downstream services using WebClient with standard exception mapping.
 * Automatically propagates X-Tenant-ID and Authorization headers downstream.
 */
public class WebClientHelper {

    private static final Logger log = LoggerFactory.getLogger(WebClientHelper.class);
    private final WebClient webClient;

    public WebClientHelper(WebClient webClient) {
        this.webClient = webClient;
    }

    public <T> T get(String url, Class<T> responseType) throws CoreException {
        return get(url, Map.of(), responseType);
    }

    public <T> T get(String url, Map<String, String> headers, Class<T> responseType) throws CoreException {
        try {
            log.info("[WebClientHelper] GET request to URL: {}", url);
            return webClient.get()
                    .uri(url)
                    .headers(httpHeaders -> addPropagationHeaders(httpHeaders, headers))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(responseType)
                    .block();
        } catch (WebClientResponseException e) {
            throw handleException(url, "GET", e);
        } catch (Exception e) {
            throw new CoreException("Unexpected error calling downstream service: " + e.getMessage(), "DOWNSTREAM_SERVICE_ERROR", e);
        }
    }

    public <T> T post(String url, Object body, Class<T> responseType) throws CoreException {
        return post(url, body, Map.of(), responseType);
    }

    public <T> T post(String url, Object body, Map<String, String> headers, Class<T> responseType) throws CoreException {
        try {
            log.info("[WebClientHelper] POST request to URL: {}", url);
            return webClient.post()
                    .uri(url)
                    .headers(httpHeaders -> addPropagationHeaders(httpHeaders, headers))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(responseType)
                    .block();
        } catch (WebClientResponseException e) {
            throw handleException(url, "POST", e);
        } catch (Exception e) {
            throw new CoreException("Unexpected error calling downstream service: " + e.getMessage(), "DOWNSTREAM_SERVICE_ERROR", e);
        }
    }

    public <T> T put(String url, Object body, Class<T> responseType) throws CoreException {
        try {
            log.info("[WebClientHelper] PUT request to URL: {}", url);
            return webClient.put()
                    .uri(url)
                    .headers(httpHeaders -> addPropagationHeaders(httpHeaders, Map.of()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(responseType)
                    .block();
        } catch (WebClientResponseException e) {
            throw handleException(url, "PUT", e);
        } catch (Exception e) {
            throw new CoreException("Unexpected error calling downstream service: " + e.getMessage(), "DOWNSTREAM_SERVICE_ERROR", e);
        }
    }

    public <T> T delete(String url, Class<T> responseType) throws CoreException {
        try {
            log.info("[WebClientHelper] DELETE request to URL: {}", url);
            return webClient.delete()
                    .uri(url)
                    .headers(httpHeaders -> addPropagationHeaders(httpHeaders, Map.of()))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(responseType)
                    .block();
        } catch (WebClientResponseException e) {
            throw handleException(url, "DELETE", e);
        } catch (Exception e) {
            throw new CoreException("Unexpected error calling downstream service: " + e.getMessage(), "DOWNSTREAM_SERVICE_ERROR", e);
        }
    }

    private void addPropagationHeaders(HttpHeaders httpHeaders, Map<String, String> customHeaders) {
        if (customHeaders != null) {
            customHeaders.forEach(httpHeaders::add);
        }

        // 1. Propagate X-Tenant-ID from TenantContext
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId != null && !httpHeaders.containsKey("X-Tenant-ID")) {
            httpHeaders.add("X-Tenant-ID", tenantId);
            log.debug("[WebClientHelper] Propagated X-Tenant-ID header: {}", tenantId);
        }

        // 2. Propagate Authorization token from current HttpServletRequest
        if (!httpHeaders.containsKey("Authorization")) {
            try {
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    String authHeader = attributes.getRequest().getHeader("Authorization");
                    if (authHeader != null && !authHeader.isBlank()) {
                        httpHeaders.add("Authorization", authHeader);
                        log.debug("[WebClientHelper] Propagated Authorization bearer header");
                    }
                }
            } catch (Exception e) {
                log.debug("[WebClientHelper] Could not propagate Authorization header: {}", e.getMessage());
            }
        }
    }

    private CoreException handleException(String url, String method, WebClientResponseException e) {
        HttpStatusCode status = e.getStatusCode();
        String responseBody = e.getResponseBodyAsString();
        log.error("[WebClientHelper] Downstream call failed: {} {} -> Status: {}, Response: {}", 
                method, url, status, responseBody);
        
        String errorCode = status.is4xxClientError() ? "DOWNSTREAM_CLIENT_ERROR" : "DOWNSTREAM_SERVER_ERROR";
        return new CoreException(
                String.format("Downstream call %s %s failed with status %s. Response: %s", 
                        method, url, status, responseBody),
                errorCode,
                e
        );
    }
}
