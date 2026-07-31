package com.pwb.audio.application.facade;

import com.pwb.audio.application.command.*;
import com.pwb.audio.application.view.PresignedUrlView;
import com.pwb.audio.application.view.SongTagConfigView;
import com.pwb.audio.application.view.SongView;
import com.pwb.audio.application.view.VoiceTagView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AudioFacade {

    SongView uploadSong(UploadSongCommand command);

    SongView uploadSongMultipart(UploadSongMultipartCommand command);

    SongView getSong(UUID userId, UUID songId);

    Page<SongView> listSongs(UUID userId, Pageable pageable);

    SongView updateSong(UpdateSongCommand command);

    void deleteSong(DeleteSongCommand command);

    SongTagConfigView configureVoiceTag(UUID userId, ConfigureVoiceTagCommand command);

    SongTagConfigView getVoiceTagConfig(UUID userId, UUID songId);

    void removeVoiceTagConfig(UUID userId, UUID songId);

    SongView triggerProcessing(UUID userId, UUID songId);

    PresignedUrlView getStreamPresignedUrl(UUID userId, UUID songId, long expirationSeconds);

    VoiceTagView createVoiceTagTts(UUID userId, String name, String text, String languageCode);

    VoiceTagView updateVoiceTag(UpdateVoiceTagCommand command);

    void deleteVoiceTag(DeleteVoiceTagCommand command);

    Page<VoiceTagView> listVoiceTags(UUID userId, Pageable pageable);

    PresignedUrlView getVoiceTagAudioUrl(UUID userId, UUID voiceTagId, long expirationSeconds);
}
