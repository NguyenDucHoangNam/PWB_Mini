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
        void places_one_tag_per_interval_starting_at_the_offset() {
            String filter = WatermarkFilterBuilder.build(request(60, 10, 30, 100), 200, 3);

            assertThat(filter).contains("asplit=4");
            assertThat(filter).contains("adelay=10000:all=1");
            assertThat(filter).contains("adelay=70000:all=1");
            assertThat(filter).contains("adelay=130000:all=1");
            assertThat(filter).contains("adelay=190000:all=1");
            assertThat(filter).contains("amix=inputs=4:duration=longest:normalize=0[tagtrack]");
        }

        @Test
        void skips_the_split_when_only_one_tag_fits() {
            String filter = WatermarkFilterBuilder.build(request(60, 5, 30, 100), 50, 3);

            assertThat(filter).doesNotContain("asplit");
            assertThat(filter).contains("adelay=5000:all=1");
            assertThat(filter).contains("[tagtrack]");
        }

        @Test
        void leaves_the_song_untouched_when_the_offset_is_past_its_end() {
            String filter = WatermarkFilterBuilder.build(request(60, 500, 30, 80), 200, 3);

            assertThat(filter).isEqualTo("[0:a]anull[out]");
        }
    }

    @Nested
    @DisplayName("ducking")
    class Ducking {

        @Test
        void dips_the_song_only_while_a_tag_plays() {
            String filter = WatermarkFilterBuilder.build(request(60, 10, 30, 80), 200, 3);

            assertThat(filter).contains("volume='if(lt(t,10.000),1,"
                    + "if(lt(mod(t-10.000,60.000),3.000),0.800,1))':eval=frame[bed]");
        }

        @Test
        void skips_the_volume_filter_entirely_at_full_volume() {
            String filter = WatermarkFilterBuilder.build(request(60, 10, 30, 100), 200, 3);

            assertThat(filter).contains("[0:a]anull[bed]");
            assertThat(filter).doesNotContain("eval=frame");
        }
    }

    @Nested
    @DisplayName("graph well-formedness")
    class WellFormedness {

        @Test
        void never_emits_doubled_stream_labels() {
            String filter = WatermarkFilterBuilder.build(request(60, 10, 30, 80), 200, 3);

            assertThat(filter).doesNotContain("[[");
            assertThat(filter).doesNotContain("]]");
        }

        @Test
        void exposes_the_mix_on_the_documented_output_label() {
            String filter = WatermarkFilterBuilder.build(request(60, 10, 30, 80), 200, 3);

            assertThat(filter).endsWith("amix=inputs=2:duration=first:normalize=0" + WatermarkFilterBuilder.OUTPUT_LABEL);
        }

        @Test
        void formats_decimals_independently_of_the_platform_locale() {
            Locale.setDefault(Locale.GERMANY);

            String filter = WatermarkFilterBuilder.build(request(60, 10, 30, 80), 200, 3);

            assertThat(filter).contains("0.800");
            assertThat(filter).doesNotContain("0,800");
        }
    }
}
