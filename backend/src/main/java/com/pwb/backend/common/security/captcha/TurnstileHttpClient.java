package com.pwb.backend.common.security.captcha;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class TurnstileHttpClient {

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public TurnstileVerificationResponse verify(String token, String remoteIp, TurnstileProperties props) throws Exception {
        Map<String, String> form = new HashMap<>();
        form.put("secret", props.getSecretKey());
        form.put("response", token);
        if (remoteIp != null && !remoteIp.isBlank()) {
            form.put("remoteip", remoteIp);
        }
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (body.length() > 0) {
                body.append('&');
            }
            body.append(urlEncode(entry.getKey())).append('=').append(urlEncode(entry.getValue()));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(props.getVerifyUrl()))
                .timeout(Duration.ofMillis(props.getTimeoutMillis()))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Turnstile verification returned status " + response.statusCode());
        }
        JsonNode json = objectMapper.readTree(response.body());
        boolean success = json.path("success").asBoolean(false);
        List<String> errors = Collections.emptyList();
        JsonNode errorCodes = json.get("error-codes");
        if (errorCodes != null && errorCodes.isArray()) {
            List<String> collected = new ArrayList<>();
            for (JsonNode node : errorCodes) {
                collected.add(node.asText());
            }
            errors = collected;
        }
        return new TurnstileVerificationResponse(success, errors);
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
