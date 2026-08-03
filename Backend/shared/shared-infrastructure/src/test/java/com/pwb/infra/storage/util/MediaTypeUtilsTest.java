package com.pwb.infra.storage.util;

import com.pwb.infra.storage.exception.StorageException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MediaTypeUtils — detect MIME type and validate storage key")
class MediaTypeUtilsTest {

    @Test
    @DisplayName("should_detect_id3v2_tag_as_mpeg")
    void should_detect_id3v2_tag_as_mpeg() {
        byte[] head = {'I', 'D', '3', 0x04, 0x00};

        String mime = MediaTypeUtils.detectFromBytes(head);

        assertThat(mime).isEqualTo("audio/mpeg");
    }

    @Test
    @DisplayName("should_detect_mpeg_frame_sync_as_mpeg")
    void should_detect_mpeg_frame_sync_as_mpeg() {
        byte[] head = {(byte) 0xFF, (byte) 0xFB, (byte) 0x90, 0x00};

        String mime = MediaTypeUtils.detectFromBytes(head);

        assertThat(mime).isEqualTo("audio/mpeg");
    }

    @Test
    @DisplayName("should_detect_riff_wav_header")
    void should_detect_riff_wav_header() {
        byte[] head = {'R', 'I', 'F', 'F', 0x00, 0x00, 0x00, 0x00, 'W', 'A', 'V', 'E'};

        String mime = MediaTypeUtils.detectFromBytes(head);

        assertThat(mime).isEqualTo("audio/wav");
    }

    @Test
    @DisplayName("should_detect_flac_signature")
    void should_detect_flac_signature() {
        byte[] head = {'f', 'L', 'a', 'C', 0x00};

        String mime = MediaTypeUtils.detectFromBytes(head);

        assertThat(mime).isEqualTo("audio/flac");
    }

    @Test
    @DisplayName("should_detect_ogg_signature")
    void should_detect_ogg_signature() {
        byte[] head = {'O', 'g', 'g', 'S', 0x00};

        String mime = MediaTypeUtils.detectFromBytes(head);

        assertThat(mime).isEqualTo("audio/ogg");
    }

    @Test
    @DisplayName("should_detect_matroska_signature")
    void should_detect_matroska_signature() {
        byte[] head = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3};

        String mime = MediaTypeUtils.detectFromBytes(head);

        assertThat(mime).isEqualTo("video/x-matroska");
    }

    @Test
    @DisplayName("should_default_to_octet_stream_when_unknown")
    void should_default_to_octet_stream_when_unknown() {
        byte[] head = {'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

        String mime = MediaTypeUtils.detectFromBytes(head);

        assertThat(mime).isEqualTo("application/octet-stream");
    }

    @Test
    @DisplayName("should_default_to_octet_stream_when_input_null")
    void should_default_to_octet_stream_when_input_null() {
        String mime = MediaTypeUtils.detectFromBytes(null);

        assertThat(mime).isEqualTo("application/octet-stream");
    }

    @Test
    @DisplayName("should_default_to_octet_stream_when_input_too_short")
    void should_default_to_octet_stream_when_input_too_short() {
        byte[] head = {0x01, 0x02};

        String mime = MediaTypeUtils.detectFromBytes(head);

        assertThat(mime).isEqualTo("application/octet-stream");
    }

    @Test
    @DisplayName("should_accept_valid_key")
    void should_accept_valid_key() {
        MediaTypeUtils.validateKey("audio/2026/07/sample.mp3");
    }

    @Test
    @DisplayName("should_reject_null_key")
    void should_reject_null_key() {
        assertThatThrownBy(() -> MediaTypeUtils.validateKey(null))
                .isInstanceOf(StorageException.class);
    }

    @Test
    @DisplayName("should_reject_blank_key")
    void should_reject_blank_key() {
        assertThatThrownBy(() -> MediaTypeUtils.validateKey("   "))
                .isInstanceOf(StorageException.class);
    }

    @Test
    @DisplayName("should_reject_key_longer_than_max")
    void should_reject_key_longer_than_max() {
        StringBuilder longKey = new StringBuilder();
        for (int i = 0; i < 1025; i++) {
            longKey.append('a');
        }

        assertThatThrownBy(() -> MediaTypeUtils.validateKey(longKey.toString()))
                .isInstanceOf(StorageException.class);
    }

    @Test
    @DisplayName("should_reject_key_with_double_dot")
    void should_reject_key_with_double_dot() {
        assertThatThrownBy(() -> MediaTypeUtils.validateKey("foo/../bar.mp3"))
                .isInstanceOf(StorageException.class);
    }

    @Test
    @DisplayName("should_reject_key_with_invalid_characters")
    void should_reject_key_with_invalid_characters() {
        assertThatThrownBy(() -> MediaTypeUtils.validateKey("foo bar.mp3"))
                .isInstanceOf(StorageException.class);
    }

    @Test
    @DisplayName("should_reject_key_with_question_mark")
    void should_reject_key_with_question_mark() {
        assertThatThrownBy(() -> MediaTypeUtils.validateKey("foo?bar.mp3"))
                .isInstanceOf(StorageException.class);
    }
}