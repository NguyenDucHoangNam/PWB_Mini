package com.pwb.audio.application.usecase;

import com.pwb.audio.application.command.*;
import com.pwb.audio.application.view.PresignedUrlView;
import com.pwb.audio.application.view.VoiceTagView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface VoiceTagUseCase {

    VoiceTagView createVoiceTag(CreateVoiceTagCommand command);

    VoiceTagView getVoiceTag(UUID userId, UUID voiceTagId);

    VoiceTagView updateVoiceTag(UpdateVoiceTagCommand command);

    void deleteVoiceTag(DeleteVoiceTagCommand command);

    VoiceTagView markDefault(UUID userId, UUID voiceTagId);

    Page<VoiceTagView> listVoiceTags(UUID userId, Pageable pageable);
}
