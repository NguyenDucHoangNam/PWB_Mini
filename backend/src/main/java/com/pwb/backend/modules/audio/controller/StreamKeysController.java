package com.pwb.backend.modules.audio.controller;

import com.pwb.backend.modules.audio.service.StreamKeyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/stream/keys")
@RequiredArgsConstructor
public class StreamKeysController {

    private final StreamKeyService streamKeyService;

    @GetMapping("/{shareToken}")
    public ResponseEntity<byte[]> getDecryptionKey(
            @PathVariable UUID shareToken,
            HttpServletRequest request,
            HttpServletResponse response) {
        byte[] keyBytes = streamKeyService.loadKey(shareToken, request, response);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentLength(keyBytes.length);
        return new ResponseEntity<>(keyBytes, headers, HttpStatus.OK);
    }
}
