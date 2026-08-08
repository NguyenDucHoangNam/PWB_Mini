package com.pwb.audio.infrastructure.audio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TagLoudnessMatch – putting the voice tag on the song's level")
class TagLoudnessMatchTest {

    private static final double HEADROOM_DB = 3.0;

    @Nested
    @DisplayName("matching")
    class Matching {

        @Test
        void lifts_a_quiet_tag_to_the_song_plus_headroom() {
            // A mastered track against a synthesized tag: the gap this whole class exists for.
            double gain = TagLoudnessMatch.gainDb(-10.0, -22.0, HEADROOM_DB);

            assertThat(gain).isEqualTo(15.0);
        }

        @Test
        void pulls_a_tag_down_when_it_is_already_louder_than_the_song() {
            double gain = TagLoudnessMatch.gainDb(-18.0, -9.0, HEADROOM_DB);

            assertThat(gain).isEqualTo(-6.0);
        }

        @Test
        void leaves_an_already_matched_tag_where_it_is() {
            double gain = TagLoudnessMatch.gainDb(-14.0, -11.0, HEADROOM_DB);

            assertThat(gain).isZero();
        }

        @Test
        void places_the_tag_level_with_the_song_when_no_headroom_is_asked_for() {
            double gain = TagLoudnessMatch.gainDb(-12.0, -20.0, 0.0);

            assertThat(gain).isEqualTo(8.0);
        }
    }

    @Nested
    @DisplayName("limits")
    class Limits {

        /**
         * A tag needing more than this is mostly noise floor, and the boost would bring the hiss up with it.
         */
        @Test
        void refuses_to_boost_a_very_quiet_tag_without_limit() {
            double gain = TagLoudnessMatch.gainDb(-8.0, -45.0, HEADROOM_DB);

            assertThat(gain).isEqualTo(18.0);
        }

        @Test
        void refuses_to_cut_without_limit() {
            double gain = TagLoudnessMatch.gainDb(-40.0, -5.0, HEADROOM_DB);

            assertThat(gain).isEqualTo(-12.0);
        }
    }

    @Nested
    @DisplayName("unusable measurements")
    class UnusableMeasurements {

        @Test
        void falls_back_to_the_unmatched_mix_when_the_song_could_not_be_measured() {
            assertThat(TagLoudnessMatch.gainDb(null, -22.0, HEADROOM_DB)).isZero();
        }

        @Test
        void falls_back_to_the_unmatched_mix_when_the_tag_could_not_be_measured() {
            assertThat(TagLoudnessMatch.gainDb(-10.0, null, HEADROOM_DB)).isZero();
        }

        /**
         * Silence reports as {@code -inf}; believing it would ask for an unbounded boost of nothing.
         */
        @Test
        void distrusts_silence() {
            assertThat(TagLoudnessMatch.gainDb(-10.0, Double.NEGATIVE_INFINITY, HEADROOM_DB)).isZero();
            assertThat(TagLoudnessMatch.gainDb(-10.0, -70.0, HEADROOM_DB)).isZero();
            assertThat(TagLoudnessMatch.gainDb(Double.NaN, -22.0, HEADROOM_DB)).isZero();
        }
    }
}
