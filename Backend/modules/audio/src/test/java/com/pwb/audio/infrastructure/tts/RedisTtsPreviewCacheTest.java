package com.pwb.audio.infrastructure.tts;

import com.pwb.audio.domain.service.TtsRequest;
import com.pwb.audio.domain.service.TtsResult;
import com.pwb.audio.infrastructure.tts.properties.TtsPreviewCacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RedisTtsPreviewCache – keeping a billed synthesis out of the next request")
class RedisTtsPreviewCacheTest {

    private static final byte[] AUDIO = {1, 2, 3, 4, 5};
    private static final TtsRequest REQUEST = new TtsRequest("xin chào", "vi-VN", "vi-VN-Wavenet-A");

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private TtsPreviewCacheProperties properties;
    private RedisTtsPreviewCache cache;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        properties = new TtsPreviewCacheProperties();
        cache = new RedisTtsPreviewCache(redis, properties);
    }

    private String storedValue() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(anyString(), captor.capture(), any(Duration.class));
        return captor.getValue();
    }

    @Nested
    @DisplayName("round trip")
    class RoundTrip {

        @Test
        @DisplayName("what was stored comes back byte for byte")
        void survivesEncoding() {
            cache.put(REQUEST, new TtsResult(AUDIO, 7, "audio/mpeg"));
            String encoded = storedValue();
            when(valueOps.get(anyString())).thenReturn(encoded);

            Optional<TtsResult> found = cache.find(REQUEST);

            assertThat(found).isPresent();
            assertThat(found.get().audioBytes()).isEqualTo(AUDIO);
            assertThat(found.get().durationSeconds()).isEqualTo(7);
            assertThat(found.get().contentType()).isEqualTo("audio/mpeg");
        }

        @Test
        @DisplayName("absent metadata stays absent instead of becoming a zero")
        void survivesNullMetadata() {
            cache.put(REQUEST, new TtsResult(AUDIO, null, null));
            String encoded = storedValue();
            when(valueOps.get(anyString())).thenReturn(encoded);

            Optional<TtsResult> found = cache.find(REQUEST);

            assertThat(found).isPresent();
            assertThat(found.get().audioBytes()).isEqualTo(AUDIO);
            assertThat(found.get().durationSeconds()).isNull();
            assertThat(found.get().contentType()).isNull();
        }

        @Test
        @DisplayName("the configured TTL is what gets written")
        void appliesConfiguredTtl() {
            properties.setTtl(Duration.ofMinutes(3));

            cache.put(REQUEST, new TtsResult(AUDIO, 7, "audio/mpeg"));

            verify(valueOps).set(anyString(), anyString(), org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(3)));
        }
    }

    @Nested
    @DisplayName("which requests share an entry")
    class KeyIdentity {

        private String keyFor(TtsRequest request) {
            cache.put(request, new TtsResult(AUDIO, 1, "audio/mpeg"));
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(valueOps, org.mockito.Mockito.atLeastOnce())
                    .set(captor.capture(), anyString(), any(Duration.class));
            return captor.getValue();
        }

        @Test
        @DisplayName("a different voice is a different entry")
        void voiceChangesTheKey() {
            String first = keyFor(REQUEST);
            String second = keyFor(new TtsRequest("xin chào", "vi-VN", "vi-VN-Wavenet-B"));

            assertThat(first).isNotEqualTo(second);
        }

        @Test
        @DisplayName("the same phrase, language and voice reuse one entry")
        void identicalRequestsAgree() {
            String first = keyFor(REQUEST);
            String second = keyFor(new TtsRequest("xin chào", "vi-VN", "vi-VN-Wavenet-A"));

            assertThat(first).isEqualTo(second);
        }

        @Test
        @DisplayName("the phrase is hashed, so the key never carries what the user typed")
        void keyDoesNotLeakText() {
            assertThat(keyFor(REQUEST)).doesNotContain("xin chào");
        }

        /**
         * Without a separator, language {@code "vi-VN"} + voice {@code "A"} and language {@code "vi-VN A"}
         * + voice {@code ""} would hash the same, and one of the two would be served audio in a voice it
         * never asked for.
         */
        @Test
        @DisplayName("a shifted split of the same characters is not the same entry")
        void resistsFieldShifting() {
            String first = keyFor(new TtsRequest("hello", "vi-VN", "A"));
            String second = keyFor(new TtsRequest("hello", "vi-VN A", ""));

            assertThat(first).isNotEqualTo(second);
        }
    }

    @Nested
    @DisplayName("when Redis or the entry cannot be trusted")
    class FailsOpen {

        @Test
        @DisplayName("an unreachable Redis reads as a miss, not an error")
        void lookupFailureIsAMiss() {
            when(valueOps.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));

            assertThat(cache.find(REQUEST)).isEmpty();
        }

        @Test
        @DisplayName("a failed write is swallowed so the caller still gets its audio")
        void storeFailureIsSwallowed() {
            org.mockito.Mockito.doThrow(new RedisConnectionFailureException("down"))
                    .when(valueOps).set(anyString(), anyString(), any(Duration.class));

            cache.put(REQUEST, new TtsResult(AUDIO, 7, "audio/mpeg"));
        }

        @Test
        @DisplayName("a corrupt entry is discarded rather than half-decoded")
        void malformedEntryIsAMiss() {
            when(valueOps.get(anyString())).thenReturn("audio/mpeg|7|!!!not-base64!!!");

            assertThat(cache.find(REQUEST)).isEmpty();
        }

        @Test
        @DisplayName("an entry with too few fields is discarded")
        void truncatedEntryIsAMiss() {
            when(valueOps.get(anyString())).thenReturn("audio/mpeg|7");

            assertThat(cache.find(REQUEST)).isEmpty();
        }
    }

    @Nested
    @DisplayName("what is refused entry")
    class Refusals {

        @Test
        @DisplayName("audio past the size ceiling is returned but not stored")
        void oversizedPayloadIsNotStored() {
            properties.setMaxBytes(4);

            cache.put(REQUEST, new TtsResult(AUDIO, 7, "audio/mpeg"));

            verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("disabling the cache stops both halves, not just the write")
        void disabledCacheDoesNothing() {
            properties.setEnabled(false);

            cache.put(REQUEST, new TtsResult(AUDIO, 7, "audio/mpeg"));

            assertThat(cache.find(REQUEST)).isEmpty();
            verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
            verify(valueOps, never()).get(anyString());
        }
    }
}
