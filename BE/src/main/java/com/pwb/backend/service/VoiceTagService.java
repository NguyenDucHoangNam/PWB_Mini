package com.pwb.backend.service;

import com.pwb.backend.dto.request.CreateVoiceTagRequest;
import com.pwb.backend.dto.response.PreviewResponse;
import com.pwb.backend.dto.response.VoiceTagResponse;
import com.pwb.backend.dto.response.VoiceWhitelistResponse;

import java.util.List;
import java.util.UUID;

public interface VoiceTagService {

    List<VoiceTagResponse> listForOwner(UUID ownerId);

    VoiceWhitelistResponse getWhitelist(String languageCode);

    VoiceTagResponse create(UUID ownerId, String actor, CreateVoiceTagRequest request);

    VoiceTagResponse setDefault(UUID ownerId, String actor, UUID tagId);

    PreviewResponse generatePreview(UUID ownerId, UUID tagId);

    void delete(UUID ownerId, UUID tagId);
}
