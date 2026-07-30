package com.pwb.audio.application.command;

import com.pwb.audio.domain.model.vo.AudioFormat;

import java.util.UUID;

public record UploadSongMultipartCommand(
        UUID userId,
        String title,
        String artist,
        String album,
        String originalS3Key,
        Long fileSizeBytes,
        Integer durationSeconds,
        AudioFormat format
) {
}