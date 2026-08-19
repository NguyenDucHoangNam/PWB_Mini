package com.pwb.audio.application.usecase.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.audio.application.command.CreateSongCommand;
import com.pwb.audio.application.exception.AudioBusinessException;
import com.pwb.audio.application.exception.AudioErrorCode;
import com.pwb.audio.application.support.SongViewFactory;
import com.pwb.audio.application.support.StorageCleaner;
import com.pwb.audio.application.view.UploadUrlView;
import com.pwb.audio.domain.model.Song;
import com.pwb.audio.domain.service.PresignedUrl;
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
import org.springframework.dao.DataIntegrityViolationException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
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
    private StorageCleaner storageCleaner;
    private SongUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        songRepository = mock(SongRepository.class);
        storagePort = mock(StoragePort.class);
        storageCleaner = mock(StorageCleaner.class);

        AudioUploadProperties uploadProperties = new AudioUploadProperties();
        uploadProperties.setMaxFileSizeBytes(MAX_FILE_SIZE);

        useCase = new SongUseCaseImpl(
                songRepository,
                mock(VoiceTagRepository.class),
                mock(SongTagConfigRepository.class),
                storagePort,
                storageCleaner,
                uploadProperties,
                mock(OutboxEnqueueHelper.class),
                new ObjectMapper(),
                new SongViewFactory(mock(SongTagConfigRepository.class))
        );
    }

    /** A staged upload key — where a presigned PUT puts the file, and the only shape createSong accepts. */
    private static String keyOwnedBy(UUID userId) {
        return "audio/staging/" + userId + "/" + UUID.randomUUID() + ".mp3";
    }

    /** Where {@code createSong} promotes that upload to. */
    private static String promoted(String stagingKey) {
        return "audio/originals/" + stagingKey.substring("audio/staging/".length());
    }

    private static CreateSongCommand command(String storageKey) {
        return new CreateSongCommand(USER_ID, "My Track", storageKey, 240, "mp3", null);
    }

    /** An ID3v2 header — what the first bytes of a real MP3 look like. */
    private static final byte[] MP3_MAGIC = {'I', 'D', '3', 0x03, 0x00, 0x00, 0x00, 0x00};

    private void storageHolds(String storageKey, long sizeBytes) {
        storageHolds(storageKey, sizeBytes, MP3_MAGIC);
    }

    private void storageHolds(String storageKey, long sizeBytes, byte[] head) {
        when(storagePort.findMetadata(storageKey))
                .thenReturn(Optional.of(new StoredObject(storageKey, sizeBytes, "audio/mpeg")));
        when(storagePort.readHead(eq(storageKey), anyInt())).thenReturn(head);
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
            String traversalKey = "audio/staging/" + USER_ID + "/../" + OTHER_USER_ID + "/stolen.mp3";

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

    /**
     * Nothing earlier in the flow has looked at the file. The key's extension and the declared format both
     * come from the client, and a song without a voice tag is never decoded server-side, so without this
     * check arbitrary bytes could be registered as a song and served back to listeners.
     */
    @Nested
    @DisplayName("what the bytes actually are")
    class Contents {

        @Test
        void rejects_a_file_whose_contents_are_not_audio() {
            String storageKey = keyOwnedBy(USER_ID);
            storageHolds(storageKey, 4_096, "<html><script>".getBytes(StandardCharsets.US_ASCII));

            assertThatThrownBy(() -> useCase.createSong(command(storageKey)))
                    .isInstanceOf(AudioBusinessException.class)
                    .extracting(ex -> ((AudioBusinessException) ex).getErrorCode())
                    .isEqualTo(AudioErrorCode.INVALID_AUDIO_FILE);

            verify(songRepository, never()).save(any());
        }

        @Test
        void accepts_a_file_that_really_is_audio() {
            String storageKey = keyOwnedBy(USER_ID);
            storageHolds(storageKey, 4_096);
            when(songRepository.save(any())).thenAnswer(call -> call.getArgument(0));

            useCase.createSong(command(storageKey));

            verify(songRepository).save(any());
        }
    }

    @Nested
    @DisplayName("one stored object backs one song")
    class SingleClaim {

        @Test
        void rejects_a_key_some_other_song_already_claims() {
            String storageKey = keyOwnedBy(USER_ID);
            storageHolds(storageKey, 4_096);
            when(songRepository.existsByOriginalS3Key(promoted(storageKey))).thenReturn(true);

            assertThatThrownBy(() -> useCase.createSong(command(storageKey)))
                    .isInstanceOf(AudioBusinessException.class)
                    .extracting(ex -> ((AudioBusinessException) ex).getErrorCode())
                    .isEqualTo(AudioErrorCode.UPLOAD_ALREADY_REGISTERED);

            verify(songRepository, never()).save(any());
        }

        @Test
        void reports_a_conflict_when_the_unique_index_is_what_catches_the_duplicate() {
            String storageKey = keyOwnedBy(USER_ID);
            storageHolds(storageKey, 4_096);
            when(songRepository.save(any())).thenThrow(new DataIntegrityViolationException("ux_..."));

            assertThatThrownBy(() -> useCase.createSong(command(storageKey)))
                    .isInstanceOf(AudioBusinessException.class)
                    .extracting(ex -> ((AudioBusinessException) ex).getErrorCode())
                    .isEqualTo(AudioErrorCode.UPLOAD_ALREADY_REGISTERED);
        }
    }

    /**
     * Registering promotes the object out of the staging prefix. That move is the whole reason an expiry
     * rule can be put on staging at all: once nothing a song references lives there, whatever remains is
     * an upload nobody registered, which no code path can otherwise find.
     */
    @Nested
    @DisplayName("promoting a staged upload")
    class Promotion {

        @Test
        void copies_the_object_out_of_staging_and_stores_the_new_key() {
            String stagingKey = keyOwnedBy(USER_ID);
            storageHolds(stagingKey, 4_096);
            when(songRepository.save(any())).thenAnswer(call -> call.getArgument(0));

            useCase.createSong(command(stagingKey));

            verify(storagePort).copy(stagingKey, promoted(stagingKey));

            ArgumentCaptor<Song> saved = ArgumentCaptor.forClass(Song.class);
            verify(songRepository).save(saved.capture());
            assertThat(saved.getValue().getOriginalS3Key()).isEqualTo(promoted(stagingKey));
        }

        @Test
        void drops_the_staged_copy_only_after_the_row_commits() {
            String stagingKey = keyOwnedBy(USER_ID);
            storageHolds(stagingKey, 4_096);
            when(songRepository.save(any())).thenAnswer(call -> call.getArgument(0));

            useCase.createSong(command(stagingKey));

            // After commit, not immediately: a rollback has to leave the upload where the client put it.
            verify(storageCleaner).deleteAfterCommit(stagingKey);
            verify(storageCleaner, never()).deleteNow(stagingKey);
        }

        @Test
        void reclaims_the_copy_when_the_write_fails() {
            String stagingKey = keyOwnedBy(USER_ID);
            storageHolds(stagingKey, 4_096);
            when(songRepository.save(any())).thenThrow(new IllegalStateException("boom"));

            assertThatThrownBy(() -> useCase.createSong(command(stagingKey)))
                    .isInstanceOf(IllegalStateException.class);

            // The copy sits outside staging, so no lifecycle rule would ever reach it.
            verify(storageCleaner).deleteNow(promoted(stagingKey));
            verify(storageCleaner, never()).deleteAfterCommit(any());
        }

        /**
         * Losing the unique-index race means another registration now owns that key, and the object there
         * is its live audio. Deleting it would take a stranger's song offline.
         */
        @Test
        void leaves_the_copy_alone_when_another_registration_won_the_race() {
            String stagingKey = keyOwnedBy(USER_ID);
            storageHolds(stagingKey, 4_096);
            when(songRepository.save(any())).thenThrow(new DataIntegrityViolationException("ux_..."));

            assertThatThrownBy(() -> useCase.createSong(command(stagingKey)))
                    .isInstanceOf(AudioBusinessException.class);

            verify(storageCleaner, never()).deleteNow(any());
        }

        @Test
        void never_copies_anything_it_has_already_rejected() {
            storageHolds(keyOwnedBy(USER_ID), 4_096);

            assertThatThrownBy(() -> useCase.createSong(command(keyOwnedBy(OTHER_USER_ID))))
                    .isInstanceOf(AudioBusinessException.class);

            verify(storagePort, never()).copy(any(), any());
        }
    }

    /**
     * The size and content type are signed into the URL, so storage refuses anything else. That is the
     * only point at which an upload can be bounded: afterwards the bytes are already transferred and paid
     * for, and rejecting them only decides what gets registered, not what gets stored.
     */
    @Nested
    @DisplayName("issuing an upload URL")
    class UploadUrl {

        @Test
        void refuses_to_issue_one_for_a_file_over_the_limit() {
            assertThatThrownBy(() -> useCase.createUploadUrl(USER_ID, "mp3", MAX_FILE_SIZE + 1))
                    .isInstanceOf(AudioBusinessException.class)
                    .extracting(ex -> ((AudioBusinessException) ex).getErrorCode())
                    .isEqualTo(AudioErrorCode.FILE_TOO_LARGE);

            verify(storagePort, never()).presignUpload(any(), any(), anyLong(), any());
        }

        @Test
        void signs_the_declared_size_and_the_format_s_content_type_into_the_url() throws Exception {
            when(storagePort.presignUpload(any(), any(), anyLong(), any()))
                    .thenReturn(new PresignedUrl(URI.create("https://s3/put").toURL(), Instant.now()));

            UploadUrlView view = useCase.createUploadUrl(USER_ID, "wav", 4_096);

            verify(storagePort).presignUpload(
                    startsWith("audio/staging/" + USER_ID + "/"), eq("audio/wav"), eq(4_096L), any());
            // Handed back so the client sends exactly the value that was signed; anything else is refused.
            assertThat(view.contentType()).isEqualTo("audio/wav");
        }
    }
}
