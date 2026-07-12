package com.pwb.backend.modules.voice_tag.service;

import com.google.cloud.texttospeech.v1.ListVoicesRequest;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.Voice;
import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.modules.voice_tag.config.VoiceTagProperties;
import com.pwb.backend.modules.voice_tag.exception.VoiceTagErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class VoiceTagVoiceWhitelistService {

    private static final String CACHE_KEY_PREFIX = "gcp:tts:voices_cache:";

    private final ObjectProvider<TextToSpeechClient> textToSpeechClientProvider;
    private final StringRedisTemplate stringRedisTemplate;
    private final VoiceTagProperties properties;

    public VoiceTagVoiceWhitelistService(
            ObjectProvider<TextToSpeechClient> textToSpeechClientProvider,
            StringRedisTemplate stringRedisTemplate,
            VoiceTagProperties properties) {
        this.textToSpeechClientProvider = textToSpeechClientProvider;
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
    }

    public void validate(String voiceName, String languageCode) {
        if (voiceName == null || voiceName.isBlank()) {
            throw new BusinessException(VoiceTagErrorCode.INVALID_VOICE_NAME);
        }
        Set<String> allowed = loadVoicesForLanguage(languageCode);
        if (!allowed.contains(voiceName)) {
            throw new BusinessException(VoiceTagErrorCode.INVALID_VOICE_NAME);
        }
    }

    private Set<String> loadVoicesForLanguage(String languageCode) {
        String cacheKey = CACHE_KEY_PREFIX + (languageCode == null ? "" : languageCode.toLowerCase());
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        Set<String> result = new HashSet<>();
        if (cached != null && !cached.isBlank()) {
            for (String voice : cached.split(",")) {
                if (!voice.isBlank()) {
                    result.add(voice);
                }
            }
            if (!result.isEmpty()) {
                return result;
            }
        }
        result.addAll(fetchFromGcp(languageCode));
        cache(cacheKey, result);
        return result;
    }

    private Set<String> fetchFromGcp(String languageCode) {
        TextToSpeechClient client = textToSpeechClientProvider.getIfAvailable();
        if (client == null) {
            log.warn("GCP_TTS_CLIENT_UNAVAILABLE language={}", languageCode);
            throw new BusinessException(VoiceTagErrorCode.TTS_SERVICE_FAILED,
                    "Google Cloud Text-to-Speech client is not available");
        }
        try {
            ListVoicesRequest.Builder builder = ListVoicesRequest.newBuilder();
            if (languageCode != null && !languageCode.isBlank()) {
                builder.setLanguageCode(languageCode);
            }
            List<Voice> voices = client.listVoices(builder.build()).getVoicesList();
            Set<String> names = new HashSet<>();
            for (Voice voice : voices) {
                names.add(voice.getName());
            }
            log.info("GCP_TTS_VOICES_FETCHED language={} count={}", languageCode, names.size());
            return names;
        } catch (Exception ex) {
            log.warn("GCP_TTS_VOICE_FETCH_FAILED language={} reason={}", languageCode, ex.getMessage());
            throw new BusinessException(VoiceTagErrorCode.TTS_SERVICE_FAILED,
                    "Failed to load voice whitelist");
        }
    }

    private void cache(String cacheKey, Collection<String> voices) {
        if (voices.isEmpty()) {
            return;
        }
        String payload = String.join(",", voices);
        stringRedisTemplate.opsForValue().set(
                cacheKey, payload, Duration.ofSeconds(properties.getVoiceListCacheTtlSeconds()));
    }
}
