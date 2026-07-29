package com.pwb.audio.application.usecase;

import com.pwb.audio.application.command.*;
import com.pwb.audio.application.view.PresignedUrlView;
import com.pwb.audio.application.view.VoiceTagView;

import java.util.UUID;

public interface VoiceTagUseCase {

    VoiceTagView createVoiceTag(CreateVoiceTagCommand command);

    VoiceTagView updateVoiceTag(UpdateVoiceTagCommand command);

    void deleteVoiceTag(DeleteVoiceTagCommand command);

    VoiceTagView markDefault(UUID userId, UUID voiceTagId);

    PresignedUrlView getPresignedUploadUrl(UUID userId, String filename, long expirationSeconds);
}
