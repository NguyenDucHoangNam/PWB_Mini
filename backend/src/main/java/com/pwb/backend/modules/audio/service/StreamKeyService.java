package com.pwb.backend.modules.audio.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.UUID;

public interface StreamKeyService {

    byte[] loadKey(UUID shareToken, HttpServletRequest request, HttpServletResponse response);
}
