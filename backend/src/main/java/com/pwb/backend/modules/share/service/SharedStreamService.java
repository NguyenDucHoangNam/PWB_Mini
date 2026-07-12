package com.pwb.backend.modules.share.service;

import com.pwb.backend.modules.share.dto.response.SharedThreadResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public interface SharedStreamService {

    SharedThreadResponse loadSharedThread(UUID shareToken, HttpServletRequest request);

    String issueSessionCookie(UUID shareToken, UUID demoId, HttpServletRequest request);
}
