package com.pwb.audio.infrastructure.audio;

import com.pwb.audio.domain.model.AudioProcessingRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WatermarkFilterBuilder – periodic voice tag filtergraph")
class WatermarkFilterBuilderTest {

    private static final UUID SONG_ID = UUID.randomUUID();

    private final Locale originalLocale = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(originalLocale);
    }

    /** Most cases are about placement and ducking, so they build with the match gain switched off. */
    private static String build(AudioProcessingRequest request,
                                double songDurationSeconds,
                                double voiceTagDurationSeconds) {
        return WatermarkFilterBuilder.build(request, songDurationSeconds, voiceTagDurationSeconds, 0.0);
    }

    private static AudioProcessingRequest request(int intervalSeconds,
                                                  int startOffsetSeconds,
                                                  int tagVolume,
                                                  int ducking) {
        return new AudioProcessingRequest(
                SONG_ID,
                "audio/originals/u/song.mp3",
                "audio/voice-tags/u/tag.mp3",
                intervalSeconds,
                startOffsetSeconds,
                tagVolume,
                ducking,
                "audio/processed/u/song.mp3"
        );
    }

    @Nested
    @DisplayName("insertion placement")
    class InsertionPlacement {

        @Test
        void loops_a_single_interval_long_period_once_per_insertion() {
            String filter = build(request(60, 10, 30, 100), 200, 3);

            // 4 insertions: the period is emitted once, then looped 3 more times.
            assertThat(filter).contains("apad=whole_dur=60,aloop=loop=3:size=2646000");
            assertThat(filter).contains("adelay=10000:all=1[tagtrack]");
            assertThat(filter).doesNotContain("asplit");
        }

        /**
         * A period this long cannot be buffered, so the graph falls back to one delayed copy per insertion.
         */
        @Test
        void falls_back_to_one_delayed_copy_per_insertion_when_the_period_is_too_long_to_buffer() {
            String filter = build(request(200, 0, 30, 100), 1000, 3);

            assertThat(filter).contains("asplit=6");
            assertThat(filter).contains("adelay=0:all=1");
            assertThat(filter).contains("adelay=200000:all=1");
            assertThat(filter).contains("adelay=1000000:all=1");
            assertThat(filter).contains("amix=inputs=6:duration=longest:normalize=0[tagtrack]");
            assertThat(filter).doesNotContain("aloop");
        }

        @Test
        void skips_the_split_when_only_one_tag_fits() {
            String filter = build(request(60, 5, 30, 100), 50, 3);

            assertThat(filter).doesNotContain("asplit");
            assertThat(filter).doesNotContain("aloop");
            assertThat(filter).contains("adelay=5000:all=1");
            assertThat(filter).contains("[tagtrack]");
        }

        @Test
        void leaves_the_song_untouched_when_the_offset_is_past_its_end() {
            String filter = build(request(60, 500, 30, 80), 200, 3);

            assertThat(filter).isEqualTo("[0:a]anull[out]");
        }
    }

    @Nested
    @DisplayName("ducking")
    class Ducking {

        @Test
        void dips_the_song_only_while_a_tag_plays() {
            String filter = build(request(60, 10, 30, 80), 200, 3);

            assertThat(filter).contains("volume='if(lt(t,10.000),1,"
                    + "if(lt(mod(t-10.000,60.000),3.000),0.800,1))':eval=frame[bed]");
        }

        @Test
        void skips_the_volume_filter_entirely_at_full_volume() {
            String filter = build(request(60, 10, 30, 100), 200, 3);

            assertThat(filter).contains("[0:a]anull[bed]");
            assertThat(filter).doesNotContain("eval=frame");
        }
    }

    @Nested
    @DisplayName("graph well-formedness")
    class WellFormedness {

        @Test
        void never_emits_doubled_stream_labels() {
            String filter = build(request(60, 10, 30, 80), 200, 3);

            assertThat(filter).doesNotContain("[[");
            assertThat(filter).doesNotContain("]]");
        }

        @Test
        void exposes_the_mix_on_the_documented_output_label() {
            String filter = build(request(60, 10, 30, 80), 200, 3);

            assertThat(filter).contains("amix=inputs=2:duration=first:normalize=0");
            assertThat(filter).endsWith(WatermarkFilterBuilder.OUTPUT_LABEL);
        }

        @Test
        void formats_decimals_independently_of_the_platform_locale() {
            Locale.setDefault(Locale.GERMANY);

            String filter = build(request(60, 10, 30, 80), 200, 3);

            assertThat(filter).contains("0.800");
            assertThat(filter).doesNotContain("0,800");
        }
    }

    @Nested
    @DisplayName("loudness match")
    class LoudnessMatch {

        @Test
        void lifts_the_tag_before_the_user_percentage_is_applied() {
            String filter = WatermarkFilterBuilder.build(request(60, 10, 80, 50), 200, 3, 11.25);

            // Order matters: the match brings the tag up to the song, the percentage trims from there.
            assertThat(filter).contains("[1:a]aresample=44100,volume=11.250dB,volume=0.800");
        }

        @Test
        void attenuates_the_tag_when_it_is_the_louder_of_the_two() {
            String filter = WatermarkFilterBuilder.build(request(60, 10, 100, 50), 200, 3, -6.5);

            assertThat(filter).contains("volume=-6.500dB");
        }

        @Test
        void leaves_the_tag_chain_alone_when_there_is_nothing_to_match() {
            String filter = WatermarkFilterBuilder.build(request(60, 10, 80, 50), 200, 3, 0.0);

            assertThat(filter).contains("[1:a]aresample=44100,volume=0.800");
            assertThat(filter).doesNotContain("dB");
        }

        @Test
        void formats_the_match_gain_independently_of_the_platform_locale() {
            Locale.setDefault(Locale.GERMANY);

            String filter = WatermarkFilterBuilder.build(request(60, 10, 80, 50), 200, 3, 11.25);

            assertThat(filter).contains("11.250dB");
            assertThat(filter).doesNotContain("11,250dB");
        }
    }

    @Nested
    @DisplayName("clipping")
    class Clipping {

        @Test
        void limits_the_summed_mix_so_a_loud_master_cannot_clip() {
            String filter = build(request(60, 10, 80, 50), 200, 3);

            assertThat(filter).contains("alimiter=limit=0.950:level=disabled");
        }

        /**
         * {@code level} defaults to enabled, which would normalize the output back up to 0 dBFS and undo
         * the ducking the filter above it just applied.
         */
        @Test
        void never_lets_the_limiter_auto_level_the_output() {
            String filter = build(request(60, 10, 80, 50), 200, 3);

            assertThat(filter).doesNotContain("level=enabled");
            assertThat(filter).doesNotContain("alimiter=limit=0.950[");
        }

        @Test
        void skips_the_limiter_when_no_tag_is_mixed_in() {
            String filter = build(request(60, 500, 80, 50), 200, 3);

            assertThat(filter).isEqualTo("[0:a]anull[out]");
        }
    }
}
