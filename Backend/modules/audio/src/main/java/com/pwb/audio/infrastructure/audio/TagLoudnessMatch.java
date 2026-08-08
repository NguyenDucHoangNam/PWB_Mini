package com.pwb.audio.infrastructure.audio;

/**
 * Works out how much to lift a voice tag so it sits over the song rather than under it.
 *
 * <p>Mixing both at their recorded levels is what made a tag at 100% volume sound tiny. A mastered track
 * lands around -9 to -12 LUFS; a voice tag — synthesized, or recorded on whatever the user had — lands
 * around -20 to -27. That gap is 10 to 15 dB, and 10 dB is roughly half as loud to the ear, so the slider
 * ran out of travel long before the tag was audible: 100% means "unchanged", never "as loud as the song".
 *
 * <p>Matching the two removes the gap, which leaves the user's percentage doing what its label promises —
 * trimming the tag relative to the song, from a starting point where it can already be heard.
 */
public final class TagLoudnessMatch {

    /**
     * Beyond this the correction is doing more harm than good. A tag quiet enough to need more than
     * {@link #MAX_BOOST_DB} is mostly noise floor, and lifting it further just makes the hiss audible;
     * anything needing more than {@link #MAX_CUT_DB} of attenuation is loud enough that the user's own
     * volume setting is the better instrument.
     */
    private static final double MAX_BOOST_DB = 18.0;
    private static final double MAX_CUT_DB = -12.0;

    /**
     * Below this a measurement is treated as unusable rather than believed. {@code ebur128} needs signal to
     * gate against, and a near-silent file reports a number that would ask for an absurd boost.
     */
    private static final double IMPLAUSIBLY_QUIET_LUFS = -60.0;

    private TagLoudnessMatch() {
    }

    /**
     * Gain in dB to apply to the tag, or {@code 0} when either measurement is missing or implausible — the
     * unmatched mix the pipeline produced before, which is the right thing to fall back to.
     *
     * @param songLufs   integrated loudness of the song
     * @param tagLufs    integrated loudness of the voice tag
     * @param headroomDb how far above the song the tag should land
     */
    public static double gainDb(Double songLufs, Double tagLufs, double headroomDb) {
        if (!usable(songLufs) || !usable(tagLufs)) {
            return 0.0;
        }
        double gain = (songLufs + headroomDb) - tagLufs;
        return Math.max(MAX_CUT_DB, Math.min(MAX_BOOST_DB, gain));
    }

    private static boolean usable(Double lufs) {
        return lufs != null && !lufs.isNaN() && !lufs.isInfinite() && lufs > IMPLAUSIBLY_QUIET_LUFS;
    }
}
