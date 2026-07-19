package com.pwb.voice.core.service;

import com.pwb.voice.api.dto.request.CreateTtsVoiceTagRequest;
import com.pwb.voice.api.dto.request.UpdateVoiceTagRequest;
import com.pwb.voice.api.dto.request.UploadVoiceTagRequest;
import com.pwb.voice.core.model.VoiceTag;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface VoiceTagService {

    VoiceTag createTtsTag(UUID userId, CreateTtsVoiceTagRequest request);

    VoiceTag uploadTag(UUID userId, MultipartFile file, UploadVoiceTagRequest request);

    VoiceTag updateTag(UUID userId, UUID tagId, UpdateVoiceTagRequest request);

    void deleteTag(UUID userId, UUID tagId);
}