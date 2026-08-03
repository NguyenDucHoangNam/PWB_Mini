package com.pwb.audio.application.usecase;

import com.pwb.audio.application.command.DeleteVoiceTagCommand;
import com.pwb.audio.application.command.UpdateVoiceTagCommand;
import com.pwb.audio.application.view.AudioUrlView;
import com.pwb.audio.application.view.VoiceTagView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.util.UUID;

public interface VoiceTagUseCase {

    VoiceTagView createVoiceTagTts(UUID userId, String name, String text, String languageCode);

    VoiceTagView updateVoiceTag(UpdateVoiceTagCommand command);

    void deleteVoiceTag(DeleteVoiceTagCommand command);

    Page<VoiceTagView> listVoiceTags(UUID userId, Pageable pageable);

    AudioUrlView getVoiceTagAudioUrl(UUID userId, UUID voiceTagId, Duration expiration);
}
