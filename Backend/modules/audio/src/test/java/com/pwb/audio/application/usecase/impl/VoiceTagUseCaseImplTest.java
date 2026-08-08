package com.pwb.audio.application.usecase.impl;

import com.pwb.audio.application.exception.AudioBusinessException;
import com.pwb.audio.application.exception.AudioErrorCode;
import com.pwb.audio.application.command.VoiceTagAudioUpload;
import com.pwb.audio.application.support.StorageCleaner;
import com.pwb.audio.application.view.TtsPreview;
import com.pwb.audio.application.view.VoiceTagView;
import com.pwb.audio.domain.enums.TtsVoiceGender;
import com.pwb.audio.domain.enums.VoiceTagType;
import com.pwb.audio.domain.repository.SongTagConfigRepository;
import com.pwb.audio.domain.repository.VoiceTagRepository;
import com.pwb.audio.domain.service.StoragePort;
import com.pwb.audio.domain.service.TextToSpeechPort;
import com.pwb.audio.domain.service.TtsRequest;
import com.pwb.audio.domain.service.TtsResult;
import com.pwb.audio.domain.service.TtsVoice;
import com.pwb.audio.infrastructure.audio.AudioProbeService;
import com.pwb.audio.infrastructure.audio.properties.VoiceTagUploadProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

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
    private static final UUID USER_ID = UUID.randomUUID();

    private TextToSpeechPort textToSpeechPort;
    private VoiceTagRepository voiceTagRepository;
    private StoragePort storagePort;
    private AudioProbeService audioProbe;
    private VoiceTagUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        textToSpeechPort = mock(TextToSpeechPort.class);
        voiceTagRepository = mock(VoiceTagRepository.class);
        storagePort = mock(StoragePort.class);
        audioProbe = mock(AudioProbeService.class);

        useCase = new VoiceTagUseCaseImpl(
                voiceTagRepository,
                mock(SongTagConfigRepository.class),
                storagePort,
                mock(StorageCleaner.class),
                textToSpeechPort,
                audioProbe,
                new VoiceTagUploadProperties()
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

    @Nested
    @DisplayName("how long an uploaded clip may be")
    class UploadedClipLength {

        private VoiceTagAudioUpload clip() {
            return new VoiceTagAudioUpload("tag.mp3", "audio/mpeg", AUDIO);
        }

        private void probeReturns(double seconds) {
            when(audioProbe.probeExactDurationFromBytes(any(), any())).thenReturn(seconds);
        }

        @Test
        @DisplayName("rejects a clip past the limit before anything is stored")
        void rejectsOverLongClip() {
            probeReturns(12.5);

            assertThatThrownBy(() -> useCase.createVoiceTagUpload(USER_ID, "my tag", clip()))
                    .isInstanceOf(AudioBusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AudioErrorCode.VOICE_TAG_TOO_LONG);

            verifyNoInteractions(storagePort);
            verify(voiceTagRepository, never()).save(any());
        }

        /**
         * The duration column holds whole seconds. Truncating instead would let a clip a shade over the
         * limit through, which is the whole reason the probe reports an unrounded value.
         */
        @Test
        @DisplayName("a clip a shade over the limit is still over the limit")
        void rejectsFractionallyOverLongClip() {
            probeReturns(10.4);

            assertThatThrownBy(() -> useCase.createVoiceTagUpload(USER_ID, "my tag", clip()))
                    .isInstanceOf(AudioBusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AudioErrorCode.VOICE_TAG_TOO_LONG);
        }

        @Test
        @DisplayName("stores a clip inside the limit, rounding its length up")
        void acceptsClipWithinLimit() {
            probeReturns(6.2);
            when(voiceTagRepository.existsByUserIdAndName(USER_ID, "my tag")).thenReturn(false);
            when(storagePort.uploadBytes(any(), any(), any())).thenAnswer(call -> call.getArgument(0));
            when(voiceTagRepository.save(any())).thenAnswer(call -> call.getArgument(0));

            VoiceTagView view = useCase.createVoiceTagUpload(USER_ID, "my tag", clip());

            assertThat(view.tagType()).isEqualTo(VoiceTagType.UPLOADED);
            assertThat(view.durationSeconds()).isEqualTo(7);
            // Nothing was synthesised, so there is no source text or language to carry.
            assertThat(view.sourceText()).isNull();
            assertThat(view.languageCode()).isNull();
        }

        @Test
        @DisplayName("refuses a file whose extension is not an audio format we accept")
        void rejectsUnsupportedExtension() {
            VoiceTagAudioUpload notAudio = new VoiceTagAudioUpload("tag.txt", "text/plain", AUDIO);

            assertThatThrownBy(() -> useCase.createVoiceTagUpload(USER_ID, "my tag", notAudio))
                    .isInstanceOf(AudioBusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AudioErrorCode.UNSUPPORTED_FORMAT);

            verifyNoInteractions(audioProbe);
            verifyNoInteractions(storagePort);
        }
    }
}
