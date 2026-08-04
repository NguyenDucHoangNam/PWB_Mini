package com.pwb.audio.application.usecase.impl;

import com.pwb.audio.application.exception.AudioBusinessException;
import com.pwb.audio.application.exception.AudioErrorCode;
import com.pwb.audio.application.support.StorageCleaner;
import com.pwb.audio.application.view.TtsPreview;
import com.pwb.audio.domain.enums.TtsVoiceGender;
import com.pwb.audio.domain.repository.SongTagConfigRepository;
import com.pwb.audio.domain.repository.VoiceTagRepository;
import com.pwb.audio.domain.service.StoragePort;
import com.pwb.audio.domain.service.TextToSpeechPort;
import com.pwb.audio.domain.service.TtsRequest;
import com.pwb.audio.domain.service.TtsResult;
import com.pwb.audio.domain.service.TtsVoice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("VoiceTagUseCaseImpl – choosing a voice and previewing it")
class VoiceTagUseCaseImplTest {

    private static final String VI = "vi-VN";
    private static final TtsVoice VI_FEMALE = new TtsVoice("vi-VN-Wavenet-A", VI, TtsVoiceGender.FEMALE);
    private static final byte[] AUDIO = {1, 2, 3, 4};

    private TextToSpeechPort textToSpeechPort;
    private VoiceTagRepository voiceTagRepository;
    private StoragePort storagePort;
    private VoiceTagUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        textToSpeechPort = mock(TextToSpeechPort.class);
        voiceTagRepository = mock(VoiceTagRepository.class);
        storagePort = mock(StoragePort.class);

        useCase = new VoiceTagUseCaseImpl(
                voiceTagRepository,
                mock(SongTagConfigRepository.class),
                storagePort,
                mock(StorageCleaner.class),
                textToSpeechPort
        );

        when(textToSpeechPort.availableVoices(VI)).thenReturn(List.of(VI_FEMALE));
    }

    @Nested
    @DisplayName("which voices are accepted")
    class VoiceValidation {

        @Test
        @DisplayName("rejects a voice that is not offered for the requested language")
        void rejectsForeignVoice() {
            assertThatThrownBy(() -> useCase.previewVoiceTagTts("xin chào", VI, "en-US-Neural2-D"))
                    .isInstanceOf(AudioBusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AudioErrorCode.TTS_VOICE_NOT_SUPPORTED);

            verify(textToSpeechPort, never()).synthesize(any());
        }

        @Test
        @DisplayName("a blank voice is not a rejection — it means the provider default")
        void blankVoiceFallsThrough() {
            when(textToSpeechPort.synthesize(any()))
                    .thenReturn(new TtsResult(AUDIO, 3, "audio/mpeg"));

            useCase.previewVoiceTagTts("xin chào", VI, null);

            ArgumentCaptor<TtsRequest> captor = ArgumentCaptor.forClass(TtsRequest.class);
            verify(textToSpeechPort).synthesize(captor.capture());
            assertThat(captor.getValue().voiceName()).isNull();
        }

        @Test
        @DisplayName("passes an offered voice through to the synthesiser")
        void acceptsOfferedVoice() {
            when(textToSpeechPort.synthesize(any()))
                    .thenReturn(new TtsResult(AUDIO, 3, "audio/mpeg"));

            useCase.previewVoiceTagTts("xin chào", VI, VI_FEMALE.name());

            ArgumentCaptor<TtsRequest> captor = ArgumentCaptor.forClass(TtsRequest.class);
            verify(textToSpeechPort).synthesize(captor.capture());
            assertThat(captor.getValue().voiceName()).isEqualTo(VI_FEMALE.name());
            assertThat(captor.getValue().languageCode()).isEqualTo(VI);
        }
    }

    @Nested
    @DisplayName("what a preview leaves behind")
    class PreviewSideEffects {

        @Test
        @DisplayName("returns the audio without touching storage or the database")
        void previewPersistsNothing() {
            when(textToSpeechPort.synthesize(any()))
                    .thenReturn(new TtsResult(AUDIO, 7, "audio/mpeg"));

            TtsPreview preview = useCase.previewVoiceTagTts("xin chào", VI, VI_FEMALE.name());

            assertThat(preview.audioBytes()).isEqualTo(AUDIO);
            assertThat(preview.contentType()).isEqualTo("audio/mpeg");
            assertThat(preview.durationSeconds()).isEqualTo(7);

            verifyNoInteractions(storagePort);
            verifyNoInteractions(voiceTagRepository);
        }
    }
}
