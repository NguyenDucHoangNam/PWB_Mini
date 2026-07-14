package com.pwb.backend.modules.audio.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.exception.CommonErrorCode;
import com.pwb.backend.modules.audio.dto.response.DemoListItemResponse;
import com.pwb.backend.modules.audio.dto.response.DemoStatusResponse;
import com.pwb.backend.modules.audio.entity.Demo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DemoMapper {

    private final ObjectMapper objectMapper;

    public DemoStatusResponse toStatusResponse(Demo demo, String hlsPlaylistUrl) {
        return new DemoStatusResponse(
                demo.getId(),
                demo.getStatus(),
                demo.getTitle(),
                demo.getDuration() != null ? demo.getDuration() : BigDecimal.ZERO,
                demo.getSampleRate(),
                demo.getFormat(),
                parseWaveform(demo.getWaveformData()),
                hlsPlaylistUrl,
                demo.getErrorMessage());
    }

    public DemoListItemResponse toListItemResponse(Demo demo) {
        return new DemoListItemResponse(
                demo.getId(),
                demo.getTitle(),
                demo.getStatus(),
                demo.getFileSize(),
                demo.getDuration(),
                demo.getSampleRate(),
                demo.getFormat(),
                demo.getVoiceTagId(),
                demo.getVoiceTagOwnerId(),
                demo.getVoiceTagTextContent(),
                demo.getVoiceTagLanguageCode(),
                demo.getVoiceTagVoiceName(),
                demo.getCreatedAt(),
                demo.getUpdatedAt(),
                demo.getErrorMessage());
    }

    private List<Double> parseWaveform(String waveformJson) {
        if (waveformJson == null || waveformJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(waveformJson, new TypeReference<List<Double>>() {
            });
        } catch (JsonProcessingException ex) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "Failed to parse waveform JSON: " + ex.getMessage(),
                    ex);
        }
    }
}