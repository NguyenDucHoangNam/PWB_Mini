package com.pwb.voice.api;

import com.pwb.voice.api.dto.request.CreateTtsVoiceTagRequest;
import com.pwb.voice.api.dto.request.UpdateVoiceTagRequest;
import com.pwb.voice.api.dto.response.VoiceTagResponse;
import com.pwb.voice.api.enums.VoiceTagType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.net.URL;
import java.time.Duration;
import java.util.UUID;

public interface VoiceTagFacade {

    VoiceTagResponse createTtsTag(UUID userId, CreateTtsVoiceTagRequest request);

    Page<VoiceTagResponse> listTags(UUID userId, VoiceTagType type, Pageable pageable);

    VoiceTagResponse getTag(UUID userId, UUID tagId);

    VoiceTagResponse updateTag(UUID userId, UUID tagId, UpdateVoiceTagRequest request);

    void deleteTag(UUID userId, UUID tagId);

    URL getAudioPresignedUrl(UUID userId, UUID tagId, Duration expiration);
}