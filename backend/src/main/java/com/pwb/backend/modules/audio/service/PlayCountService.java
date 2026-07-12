package com.pwb.backend.modules.audio.service;

import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public interface PlayCountService {

    void recordPlay(UUID shareToken, HttpServletRequest request);
}
