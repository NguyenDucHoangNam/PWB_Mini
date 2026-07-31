package com.pwb.audio.application.facade;

import com.pwb.audio.application.command.*;
import com.pwb.audio.application.usecase.SongUseCase;
import com.pwb.audio.application.usecase.VoiceTagUseCase;
import com.pwb.audio.application.view.PresignedUrlView;
import com.pwb.audio.application.view.SongTagConfigView;
import com.pwb.audio.application.view.SongView;
import com.pwb.audio.application.view.VoiceTagView;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AudioFacadeImpl implements AudioFacade {

    private final SongUseCase songUseCase;
    private final VoiceTagUseCase voiceTagUseCase;

    @Override
    public SongView uploadSong(UploadSongCommand command) {
        return songUseCase.uploadSong(command);
    }

    @Override
    public SongView uploadSongMultipart(UploadSongMultipartCommand command) {
        return songUseCase.uploadSongMultipart(command);
    }

    @Override
    public SongView getSong(UUID userId, UUID songId) {
        return songUseCase.getSong(userId, songId);
    }

    @Override
    public Page<SongView> listSongs(UUID userId, Pageable pageable) {
        return songUseCase.listSongs(userId, pageable);
    }

    @Override
    public SongView updateSong(UpdateSongCommand command) {
        return songUseCase.updateSong(command);
    }

    @Override
    public void deleteSong(DeleteSongCommand command) {
        songUseCase.deleteSong(command);
    }

    @Override
    public SongTagConfigView configureVoiceTag(UUID userId, ConfigureVoiceTagCommand command) {
        return songUseCase.configureVoiceTag(userId, command);
    }

    @Override
    public SongView triggerProcessing(UUID userId, UUID songId) {
        return songUseCase.triggerProcessing(userId, songId);
    }

    @Override
    public PresignedUrlView getStreamPresignedUrl(UUID userId, UUID songId, long expirationSeconds) {
        return songUseCase.getStreamPresignedUrl(userId, songId, expirationSeconds);
    }

    @Override
    public VoiceTagView createVoiceTag(CreateVoiceTagCommand command) {
        return voiceTagUseCase.createVoiceTag(command);
    }

    @Override
    public VoiceTagView getVoiceTag(UUID userId, UUID voiceTagId) {
        return voiceTagUseCase.getVoiceTag(userId, voiceTagId);
    }

    @Override
    public VoiceTagView updateVoiceTag(UpdateVoiceTagCommand command) {
        return voiceTagUseCase.updateVoiceTag(command);
    }

    @Override
    public void deleteVoiceTag(DeleteVoiceTagCommand command) {
        voiceTagUseCase.deleteVoiceTag(command);
    }

    @Override
    public VoiceTagView markVoiceTagDefault(UUID userId, UUID voiceTagId) {
        return voiceTagUseCase.markDefault(userId, voiceTagId);
    }

    @Override
    public Page<VoiceTagView> listVoiceTags(UUID userId, Pageable pageable) {
        return voiceTagUseCase.listVoiceTags(userId, pageable);
    }
}
