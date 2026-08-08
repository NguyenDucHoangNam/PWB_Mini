package com.pwb.audio.application.usecase.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.audio.application.command.CreateSongCommand;
import com.pwb.audio.application.exception.AudioBusinessException;
import com.pwb.audio.application.exception.AudioErrorCode;
import com.pwb.audio.application.support.SongViewFactory;
import com.pwb.audio.application.support.StorageCleaner;
import com.pwb.audio.domain.model.Song;
import com.pwb.audio.domain.repository.SongRepository;
import com.pwb.audio.domain.repository.SongTagConfigRepository;
import com.pwb.audio.domain.repository.VoiceTagRepository;
import com.pwb.audio.domain.service.StoragePort;
import com.pwb.audio.domain.service.StoredObject;
import com.pwb.audio.infrastructure.audio.properties.AudioUploadProperties;
import com.pwb.infra.outbox.api.OutboxEnqueueHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SongUseCaseImpl – registering an uploaded song")
class SongUseCaseImplTest {

    private static final long MAX_FILE_SIZE = 200L * 1024 * 1024;
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private SongRepository songRepository;
    private StoragePort storagePort;
    private SongUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        songRepository = mock(SongRepository.class);
        storagePort = mock(StoragePort.class);

        AudioUploadProperties uploadProperties = new AudioUploadProperties();
        uploadProperties.setMaxFileSizeBytes(MAX_FILE_SIZE);

        useCase = new SongUseCaseImpl(
                songRepository,
                mock(VoiceTagRepository.class),
                mock(SongTagConfigRepository.class),
                storagePort,
                mock(StorageCleaner.class),
                uploadProperties,
                mock(OutboxEnqueueHelper.class),
                new ObjectMapper(),
                new SongViewFactory(mock(SongTagConfigRepository.class))
        );
    }

    private static String keyOwnedBy(UUID userId) {
        return "audio/originals/" + userId + "/" + UUID.randomUUID() + ".mp3";
    }

    private static CreateSongCommand command(String storageKey) {
        return new CreateSongCommand(USER_ID, "My Track", storageKey, 240, "mp3", null);
    }

    private void storageHolds(String storageKey, long sizeBytes) {
        when(storagePort.findMetadata(storageKey))
                .thenReturn(Optional.of(new StoredObject(storageKey, sizeBytes, "audio/mpeg")));
    }

    @Nested
    @DisplayName("ownership of the storage key")
    class Ownership {

        @Test
        void rejects_a_key_belonging_to_another_user() {
            String foreignKey = keyOwnedBy(OTHER_USER_ID);

            assertThatThrownBy(() -> useCase.createSong(command(foreignKey)))
                    .isInstanceOf(AudioBusinessException.class)
                    .extracting(ex -> ((AudioBusinessException) ex).getErrorCode())
                    .isEqualTo(AudioErrorCode.UNAUTHORIZED_ACCESS);

            verify(songRepository, never()).save(any());
        }

        @Test
        void rejects_a_key_that_escapes_the_prefix_by_traversal() {
            String traversalKey = "audio/originals/" + USER_ID + "/../" + OTHER_USER_ID + "/stolen.mp3";

            assertThatThrownBy(() -> useCase.createSong(command(traversalKey)))
                    .isInstanceOf(AudioBusinessException.class)
                    .extracting(ex -> ((AudioBusinessException) ex).getErrorCode())
                    .isEqualTo(AudioErrorCode.UNAUTHORIZED_ACCESS);
        }

        @Test
        void never_asks_storage_about_a_key_it_already_rejected() {
            useCaseRejects(keyOwnedBy(OTHER_USER_ID));

            verify(storagePort, never()).findMetadata(any());
        }

        private void useCaseRejects(String storageKey) {
            assertThatThrownBy(() -> useCase.createSong(command(storageKey)))
                    .isInstanceOf(AudioBusinessException.class);
        }
    }

    @Nested
    @DisplayName("what storage actually holds")
    class UploadedFile {

        @Test
        void rejects_a_key_with_nothing_uploaded_to_it() {
            String storageKey = keyOwnedBy(USER_ID);
            when(storagePort.findMetadata(storageKey)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.createSong(command(storageKey)))
                    .isInstanceOf(AudioBusinessException.class)
                    .extracting(ex -> ((AudioBusinessException) ex).getErrorCode())
                    .isEqualTo(AudioErrorCode.UPLOAD_NOT_FOUND);

            verify(songRepository, never()).save(any());
        }

        @Test
        void rejects_a_file_over_the_configured_limit() {
            String storageKey = keyOwnedBy(USER_ID);
            storageHolds(storageKey, MAX_FILE_SIZE + 1);

            assertThatThrownBy(() -> useCase.createSong(command(storageKey)))
                    .isInstanceOf(AudioBusinessException.class)
                    .extracting(ex -> ((AudioBusinessException) ex).getErrorCode())
                    .isEqualTo(AudioErrorCode.FILE_TOO_LARGE);
        }

        @Test
        void rejects_an_empty_file() {
            String storageKey = keyOwnedBy(USER_ID);
            storageHolds(storageKey, 0);

            assertThatThrownBy(() -> useCase.createSong(command(storageKey)))
                    .isInstanceOf(AudioBusinessException.class)
                    .extracting(ex -> ((AudioBusinessException) ex).getErrorCode())
                    .isEqualTo(AudioErrorCode.FILE_EMPTY);
        }

        @Test
        void records_the_size_reported_by_storage_rather_than_anything_the_client_sent() {
            String storageKey = keyOwnedBy(USER_ID);
            long actualSize = 7_654_321L;
            storageHolds(storageKey, actualSize);
            when(songRepository.save(any())).thenAnswer(call -> call.getArgument(0));

            useCase.createSong(command(storageKey));

            ArgumentCaptor<Song> saved = ArgumentCaptor.forClass(Song.class);
            verify(songRepository).save(saved.capture());
            assertThat(saved.getValue().getFileSizeBytes()).isEqualTo(actualSize);
        }
    }
}
