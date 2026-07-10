package com.pwb.backend.audio.internal.helper;

import com.google.cloud.texttospeech.v1.AudioConfig;
import com.google.cloud.texttospeech.v1.AudioEncoding;
import com.google.cloud.texttospeech.v1.ListVoicesRequest;
import com.google.cloud.texttospeech.v1.ListVoicesResponse;
import com.google.cloud.texttospeech.v1.SsmlVoiceGender;
import com.google.cloud.texttospeech.v1.SynthesisInput;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechRequest;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechResponse;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.TextToSpeechSettings;
import com.google.cloud.texttospeech.v1.Voice;
import com.google.cloud.texttospeech.v1.VoiceSelectionParams;
import com.pwb.backend.audio.internal.config.AudioProperties;
import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class GcpTtsClient {

  private static final String VOICES_CACHE_KEY = "gcp:tts:voices_cache";

  private final AudioProperties audioProperties;
  private final StringRedisTemplate redisTemplate;

  private volatile TextToSpeechClient client;

  @PostConstruct
  void init() {
    try {
      TextToSpeechSettings.Builder settingsBuilder = TextToSpeechSettings.newBuilder();
      String credsPath = audioProperties.getTts().getCredentialsPath();
      if (credsPath != null && !credsPath.isBlank()) {
        settingsBuilder.setCredentialsProvider(() -> {
          try {
            com.google.auth.oauth2.GoogleCredentials creds =
                com.google.auth.oauth2.GoogleCredentials.fromStream(
                    new java.io.FileInputStream(credsPath));
            return creds.createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));
          } catch (IOException ex) {
            throw new IllegalStateException("Cannot load GCP credentials from " + credsPath, ex);
          }
        });
      }
      this.client = TextToSpeechClient.create(settingsBuilder.build());
      log.info("GCP TTS client initialized (credentialsPath={})",
          credsPath.isBlank() ? "<ADC>" : credsPath);
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to initialize Google Cloud TTS client", ex);
    }
  }

  @PreDestroy
  void destroy() {
    if (client != null) {
      try {
        client.close();
      } catch (Exception ex) {
        log.warn("Failed to close TTS client: {}", ex.getMessage());
      }
    }
  }

  public byte[] synthesize(String ssml, String languageCode, String voiceName) {
    try {
      SynthesisInput input = SynthesisInput.newBuilder()
          .setSsml(ssml)
          .build();
      VoiceSelectionParams voice = VoiceSelectionParams.newBuilder()
          .setLanguageCode(languageCode)
          .setName(voiceName)
          .build();
      AudioConfig audioConfig = AudioConfig.newBuilder()
          .setAudioEncoding(AudioEncoding.MP3)
          .setSampleRateHertz(24000)
          .build();
      SynthesizeSpeechRequest request = SynthesizeSpeechRequest.newBuilder()
          .setInput(input)
          .setVoice(voice)
          .setAudioConfig(audioConfig)
          .build();
      SynthesizeSpeechResponse response = client.synthesizeSpeech(request);
      return response.getAudioContent().toByteArray();
    } catch (Exception ex) {
      log.error("GCP TTS synthesize failed: voice={}, lang={}", voiceName, languageCode);
      throw new BusinessException(ErrorCode.TTS_SERVICE_FAILED, "TTS synthesis failed");
    }
  }

  public boolean isVoiceAllowed(String voiceName) {
    Set<String> allowed = loadAllowedVoices();
    return allowed.contains(voiceName);
  }

  private Set<String> loadAllowedVoices() {
    String cached = redisTemplate.opsForValue().get(VOICES_CACHE_KEY);
    if (cached != null && !cached.isBlank()) {
      return new HashSet<>(List.of(cached.split("\\|")));
    }
    Set<String> voices = fetchVoicesFromApi();
    try {
      String payload = voices.stream().sorted().collect(Collectors.joining("|"));
      redisTemplate.opsForValue().set(
          VOICES_CACHE_KEY,
          payload,
          Duration.ofSeconds(audioProperties.getTts().getVoicesCacheTtlSeconds()));
    } catch (Exception ex) {
      log.warn("Failed to cache voices list: {}", ex.getMessage());
    }
    return voices;
  }

  private Set<String> fetchVoicesFromApi() {
    try {
      ListVoicesRequest request = ListVoicesRequest.newBuilder().build();
      ListVoicesResponse response = client.listVoices(request);
      return response.getVoicesList().stream()
          .map(Voice::getName)
          .collect(Collectors.toCollection(HashSet::new));
    } catch (Exception ex) {
      log.error("GCP TTS listVoices failed: {}", ex.getMessage());
      return new HashSet<>(List.of(
          "vi-VN-Standard-A", "vi-VN-Standard-B", "vi-VN-Standard-C", "vi-VN-Standard-D",
          "vi-VN-Neural2-A", "vi-VN-Neural2-D",
          "vi-VN-Wavenet-A", "vi-VN-Wavenet-B", "vi-VN-Wavenet-C", "vi-VN-Wavenet-D",
          "en-US-Standard-A", "en-US-Standard-B", "en-US-Standard-C", "en-US-Standard-D",
          "en-US-Neural2-A", "en-US-Neural2-C", "en-US-Neural2-D",
          "en-US-Wavenet-A", "en-US-Wavenet-B", "en-US-Wavenet-C", "en-US-Wavenet-D"
      ));
    }
  }

  public boolean looksLikeMp3(byte[] data) {
    if (data == null || data.length < 4) return false;
    if ((data[0] & 0xFF) == 0x49 && (data[1] & 0xFF) == 0x44 && (data[2] & 0xFF) == 0x33) {
      return true;
    }
    if ((data[0] & 0xFF) == 0xFF
        && ((data[1] & 0xFF) == 0xFB || (data[1] & 0xFF) == 0xE3 || (data[1] & 0xFF) == 0xF3)) {
      return true;
    }
    return false;
  }

  public void healthCheck() {
    if (client == null) {
      throw new IllegalStateException("GCP TTS client is not initialized");
    }
  }

  @SuppressWarnings("unused")
  private SsmlVoiceGender anyGender() {
    return SsmlVoiceGender.NEUTRAL;
  }

  @SuppressWarnings("unused")
  private long secondsToMillis(int seconds) {
    return TimeUnit.SECONDS.toMillis(seconds);
  }
}
