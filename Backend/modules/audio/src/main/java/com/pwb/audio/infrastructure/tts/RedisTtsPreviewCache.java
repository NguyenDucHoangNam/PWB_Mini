package com.pwb.audio.infrastructure.tts;

import com.pwb.audio.domain.service.TtsPreviewCache;
import com.pwb.audio.domain.service.TtsRequest;
import com.pwb.audio.domain.service.TtsResult;
import com.pwb.audio.infrastructure.tts.properties.TtsPreviewCacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisTtsPreviewCache implements TtsPreviewCache {

    private static final String KEY_PREFIX = "audio:tts:preview:";

    /** Cannot occur in a phrase, a language tag or a voice name, which is the whole point — see keyFor. */
    private static final char KEY_FIELD_SEPARATOR = '\0';

    private static final String VALUE_FIELD_SEPARATOR = "|";
    private static final int VALUE_FIELD_COUNT = 3;

    private final StringRedisTemplate redis;
    private final TtsPreviewCacheProperties properties;

    @Override
    public Optional<TtsResult> find(TtsRequest request) {
        if (!properties.isEnabled() || request == null) {
            return Optional.empty();
        }
        try {
            String encoded = redis.opsForValue().get(keyFor(request));
            return encoded == null ? Optional.empty() : decode(encoded);
        } catch (Exception ex) {
            log.warn("Redis unavailable for TTS preview lookup, synthesising instead: reason={}", ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(TtsRequest request, TtsResult result) {
        if (!properties.isEnabled() || request == null || result == null || result.audioBytes() == null) {
            return;
        }
        if (result.audioBytes().length > properties.getMaxBytes()) {
            log.debug("TTS preview too large to cache: bytes={} max={}",
                    result.audioBytes().length, properties.getMaxBytes());
            return;
        }
        try {
            redis.opsForValue().set(keyFor(request), encode(result), properties.getTtl());
        } catch (Exception ex) {
            log.warn("Redis unavailable for TTS preview store, continuing uncached: reason={}", ex.getMessage());
        }
    }

    /**
     * The phrase is hashed rather than written into the key, for two reasons: the key stays one fixed
     * length whatever the user typed, and the text does not sit in the clear in a Redis key listing.
     *
     * <p>The three parts are joined with a separator rather than concatenated, because concatenation lets
     * a different split of the same characters collide — language {@code "a"} with voice {@code "bc"}
     * against language {@code "ab"} with voice {@code "c"} — and a collision here would hand the caller
     * audio in a voice they did not ask for.
     */
    private String keyFor(TtsRequest request) {
        String material = nullSafe(request.text())
                + KEY_FIELD_SEPARATOR + nullSafe(request.languageCode())
                + KEY_FIELD_SEPARATOR + nullSafe(request.voiceName());
        return KEY_PREFIX + sha256Hex(material);
    }

    /**
     * {@code contentType|durationSeconds|base64Audio}. Both metadata fields are optional and are written
     * as empty when absent, so the field count is constant and decoding stays positional. Neither a MIME
     * type nor base64 can contain the separator, so the split is unambiguous.
     */
    private String encode(TtsResult result) {
        return nullSafe(result.contentType())
                + VALUE_FIELD_SEPARATOR
                + (result.durationSeconds() == null ? "" : result.durationSeconds())
                + VALUE_FIELD_SEPARATOR
                + Base64.getEncoder().encodeToString(result.audioBytes());
    }

    private Optional<TtsResult> decode(String encoded) {
        String[] parts = encoded.split("\\" + VALUE_FIELD_SEPARATOR, VALUE_FIELD_COUNT);
        if (parts.length < VALUE_FIELD_COUNT) {
            log.warn("Discarding malformed TTS preview cache entry");
            return Optional.empty();
        }
        try {
            byte[] audio = Base64.getDecoder().decode(parts[2]);
            Integer duration = parts[1].isEmpty() ? null : Integer.valueOf(parts[1]);
            String contentType = parts[0].isEmpty() ? null : parts[0];
            return Optional.of(new TtsResult(audio, duration, contentType));
        } catch (IllegalArgumentException ex) {
            // A truncated or foreign value. Reported as a miss so the caller resynthesises, rather than
            // letting a half-decoded clip reach the browser dressed as a real preview.
            log.warn("Discarding undecodable TTS preview cache entry: reason={}", ex.getMessage());
            return Optional.empty();
        }
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
