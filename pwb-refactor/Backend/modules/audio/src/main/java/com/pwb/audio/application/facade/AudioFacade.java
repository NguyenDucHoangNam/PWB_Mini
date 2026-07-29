package com.pwb.audio.application.facade;

import com.pwb.audio.application.command.*;
import com.pwb.audio.application.view.PresignedUrlView;
import com.pwb.audio.application.view.SongTagConfigView;
import com.pwb.audio.application.view.SongView;
import com.pwb.audio.application.view.VoiceTagView;

import java.util.UUID;

public interface AudioFacade {

    SongView uploadSong(UploadSongCommand command);

    SongView getSong(UUID userId, UUID songId);

    SongView updateSong(UpdateSongCommand command);

    void deleteSong(DeleteSongCommand command);

    SongTagConfigView configureVoiceTag(UUID userId, ConfigureVoiceTagCommand command);

    SongView triggerProcessing(UUID userId, UUID songId);

    PresignedUrlView getStreamPresignedUrl(UUID userId, UUID songId, long expirationSeconds);

    VoiceTagView createVoiceTag(CreateVoiceTagCommand command);

    VoiceTagView updateVoiceTag(UpdateVoiceTagCommand command);

    void deleteVoiceTag(DeleteVoiceTagCommand command);

    VoiceTagView markVoiceTagDefault(UUID userId, UUID voiceTagId);

    PresignedUrlView getVoiceTagUploadUrl(UUID userId, String filename, long expirationSeconds);
}
