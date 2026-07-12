package com.pwb.backend.modules.voice_tag.service;

import com.pwb.backend.modules.voice_tag.dto.request.CreateVoiceTagRequest;
import com.pwb.backend.modules.voice_tag.dto.response.VoiceTagPreviewResponse;
import com.pwb.backend.modules.voice_tag.dto.response.VoiceTagResponse;

import java.util.List;
import java.util.UUID;

public interface VoiceTagService {

    VoiceTagResponse create(UUID ownerId, CreateVoiceTagRequest request);

    VoiceTagPreviewResponse generatePreviewUrl(UUID ownerId, UUID tagId);

    List<VoiceTagResponse> list(UUID ownerId);

    void setDefault(UUID ownerId, UUID tagId);

    void softDelete(UUID ownerId, UUID tagId);

    void restore(UUID ownerId, UUID tagId);
}
