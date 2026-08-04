package com.pwb.audio.application.usecase;

import com.pwb.audio.application.command.DeleteVoiceTagCommand;
import com.pwb.audio.application.command.UpdateVoiceTagCommand;
import com.pwb.audio.application.view.AudioUrlView;
import com.pwb.audio.application.view.TtsPreview;
import com.pwb.audio.application.view.VoiceTagView;
import com.pwb.audio.domain.service.TtsVoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

public interface VoiceTagUseCase {

    VoiceTagView createVoiceTagTts(UUID userId, String name, String text, String languageCode, String voiceName);

    /**
     * Synthesises audio and hands it straight back without persisting anything, so a user can hear a
     * voice before committing to it. Nothing reaches storage or the database.
     */
    TtsPreview previewVoiceTagTts(String text, String languageCode, String voiceName);

    /** The voices a user may choose from, across every supported language. */
    List<TtsVoice> listAvailableVoices();

    VoiceTagView updateVoiceTag(UpdateVoiceTagCommand command);

    void deleteVoiceTag(DeleteVoiceTagCommand command);

    Page<VoiceTagView> listVoiceTags(UUID userId, Pageable pageable);

    AudioUrlView getVoiceTagAudioUrl(UUID userId, UUID voiceTagId, Duration expiration);
}
