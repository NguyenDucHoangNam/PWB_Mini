package com.pwb.audio.api.controller;

import com.pwb.audio.application.usecase.SongSearchUseCase;
import com.pwb.audio.application.usecase.SongUseCase;
import com.pwb.audio.application.usecase.VoiceTagSearchUseCase;
import com.pwb.audio.application.usecase.VoiceTagUseCase;
import com.pwb.audio.application.view.TtsPreview;
import com.pwb.audio.application.view.UploadUrlView;
import com.pwb.web.message.MessageResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The paid-feature boundary. Until these annotations existed the only thing standing between a free
 * account and the whole audio feature set was the frontend's {@code useProGuard}, which a request made
 * outside the browser never passes through — so this file is about a hole that was open, not about a
 * rule that merely might regress.
 *
 * <p>It drives the controllers through a real {@code @EnableMethodSecurity} context rather than through
 * MockMvc. Standalone MockMvc builds the controller by hand and never installs the method-security
 * interceptor, so {@code @PreAuthorize} silently does nothing there and every assertion below would pass
 * against an unguarded controller. Asking the container for the bean is what makes the proxy real.
 */
@DisplayName("Audio controllers – the PRO boundary")
class AudioProRoleGuardTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SONG_ID = UUID.randomUUID();

    private AnnotationConfigApplicationContext context;
    private SongController songController;
    private VoiceTagController voiceTagController;
    private SongUseCase songUseCase;
    private VoiceTagUseCase voiceTagUseCase;

    @Configuration
    @EnableMethodSecurity
    static class TestConfig {

        @Bean
        SongUseCase songUseCase() {
            return mock(SongUseCase.class);
        }

        @Bean
        SongSearchUseCase songSearchUseCase() {
            return mock(SongSearchUseCase.class);
        }

        @Bean
        VoiceTagUseCase voiceTagUseCase() {
            return mock(VoiceTagUseCase.class);
        }

        @Bean
        VoiceTagSearchUseCase voiceTagSearchUseCase() {
            return mock(VoiceTagSearchUseCase.class);
        }

        @Bean
        MessageResolver messageResolver() {
            return mock(MessageResolver.class);
        }

        @Bean
        SongController songController(SongUseCase songUseCase,
                                      SongSearchUseCase songSearchUseCase,
                                      MessageResolver messageResolver) {
            return new SongController(songUseCase, songSearchUseCase, messageResolver);
        }

        @Bean
        VoiceTagController voiceTagController(VoiceTagUseCase voiceTagUseCase,
                                              VoiceTagSearchUseCase voiceTagSearchUseCase,
                                              MessageResolver messageResolver) {
            return new VoiceTagController(voiceTagUseCase, voiceTagSearchUseCase, messageResolver);
        }
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        songController = context.getBean(SongController.class);
        voiceTagController = context.getBean(VoiceTagController.class);
        songUseCase = context.getBean(SongUseCase.class);
        voiceTagUseCase = context.getBean(VoiceTagUseCase.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    private void authenticateAs(String... roles) {
        List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID.toString(), null, authorities));
    }

    @Nested
    @DisplayName("a free account is refused anything that creates audio")
    class FreeAccountRefused {

        @Test
        @DisplayName("cannot obtain an upload URL")
        void should_refuse_upload_url() {
            authenticateAs("ROLE_USER");

            assertThatThrownBy(() -> songController.createUploadUrl(USER_ID, null))
                    .isInstanceOf(AccessDeniedException.class);

            // The guard has to stop the call before the use case runs; a refusal that still handed out a
            // presigned PUT would be no refusal at all.
            verifyNoInteractions(songUseCase);
        }

        @Test
        @DisplayName("cannot register a song")
        void should_refuse_create_song() {
            authenticateAs("ROLE_USER");

            assertThatThrownBy(() -> songController.createSong(USER_ID, null))
                    .isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(songUseCase);
        }

        @Test
        @DisplayName("cannot re-queue an FFmpeg render")
        void should_refuse_retry_processing() {
            authenticateAs("ROLE_USER");

            assertThatThrownBy(() -> songController.retryProcessing(USER_ID, SONG_ID))
                    .isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(songUseCase);
        }

        @Test
        @DisplayName("cannot spend the TTS budget on a preview")
        void should_refuse_tts_preview() {
            authenticateAs("ROLE_USER");

            assertThatThrownBy(() -> voiceTagController.previewVoiceTagTts(null))
                    .isInstanceOf(AccessDeniedException.class);

            // The one that matters most: a preview stores nothing, so an unguarded loop here would bill
            // Google indefinitely and leave no trace to clean up afterwards.
            verifyNoInteractions(voiceTagUseCase);
        }

        @Test
        @DisplayName("cannot synthesise a voice tag")
        void should_refuse_tts_create() {
            authenticateAs("ROLE_USER");

            assertThatThrownBy(() -> voiceTagController.createVoiceTagTts(USER_ID, null))
                    .isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(voiceTagUseCase);
        }

        @Test
        @DisplayName("cannot upload a recorded clip")
        void should_refuse_voice_tag_upload() {
            authenticateAs("ROLE_USER");

            assertThatThrownBy(() -> voiceTagController.createVoiceTagUpload(USER_ID, "tag", null))
                    .isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(voiceTagUseCase);
        }
    }

    @Nested
    @DisplayName("a subscriber is let through")
    class SubscriberAllowed {

        @Test
        @DisplayName("PRO reaches the upload URL")
        void should_allow_pro_upload_url() throws Exception {
            authenticateAs("ROLE_PRO");
            when(songUseCase.createUploadUrl(any(), any())).thenReturn(new UploadUrlView(
                    "audio/originals/k.mp3", java.net.URI.create("https://s3/put").toURL(), Instant.now()));

            assertThatCode(() -> songController.createUploadUrl(
                    USER_ID, new com.pwb.audio.api.dto.request.UploadUrlRequest("mp3")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("PRO reaches the TTS preview")
        void should_allow_pro_tts_preview() {
            authenticateAs("ROLE_PRO");
            when(voiceTagUseCase.previewVoiceTagTts(any(), any(), any()))
                    .thenReturn(new TtsPreview(new byte[]{1}, "audio/mpeg", 2));

            assertThatCode(() -> voiceTagController.previewVoiceTagTts(
                    new com.pwb.audio.api.dto.request.PreviewVoiceTagTtsRequest("xin chào", "vi-VN", null)))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("reading stays open, so the live room's song picker keeps working")
    class ReadsStayOpen {

        /**
         * A participant who is not a subscriber may still open the picker — {@code SelectSongUseCaseImpl}
         * lets anyone in the room play a track of their own. Guarding the listing too would turn that
         * empty list into an error toast, so these two must not start throwing.
         */
        @Test
        @DisplayName("a free account may list its own (empty) library")
        void should_allow_free_account_to_list() {
            authenticateAs("ROLE_USER");
            SongSearchUseCase searchUseCase = context.getBean(SongSearchUseCase.class);
            when(songUseCase.listSongs(any(), any(), any())).thenReturn(emptyPage());

            assertThatCode(() -> songController.listSongs(USER_ID, null, PageRequest.of(0, 20)))
                    .doesNotThrowAnyException();
            assertThat(searchUseCase).isNotNull();
        }

        @Test
        @DisplayName("a free account may search its own library")
        void should_allow_free_account_to_search() {
            authenticateAs("ROLE_USER");
            SongSearchUseCase searchUseCase = context.getBean(SongSearchUseCase.class);
            when(searchUseCase.search(any(), any())).thenReturn(emptyPage());

            assertThatCode(() -> songController.searchSongs(
                    USER_ID, "ha noi", null, null, null, null, PageRequest.of(0, 20)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("the voice catalog costs nothing, so it stays open too")
        void should_allow_free_account_to_read_voice_catalog() {
            authenticateAs("ROLE_USER");
            when(voiceTagUseCase.listAvailableVoices()).thenReturn(List.of());

            assertThatCode(() -> voiceTagController.listTtsVoices()).doesNotThrowAnyException();
        }
    }

    private static <T> Page<T> emptyPage() {
        return new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
    }

    @Nested
    @DisplayName("an administrator is not a subscriber")
    class AdminIsNotPro {

        /**
         * Matches the live room, where {@code Actor.isPro()} tests for ROLE_PRO alone and an ADMIN is
         * refused a new room. Stated as a test because the opposite reading is the intuitive one, and
         * someone will eventually reach for {@code hasAnyRole('PRO','ADMIN')} on the assumption that the
         * omission was an oversight.
         */
        @Test
        @DisplayName("ADMIN is refused the TTS preview just like a free account")
        void should_refuse_admin() {
            authenticateAs("ROLE_ADMIN");

            assertThatThrownBy(() -> voiceTagController.previewVoiceTagTts(null))
                    .isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(voiceTagUseCase);
        }
    }
}
