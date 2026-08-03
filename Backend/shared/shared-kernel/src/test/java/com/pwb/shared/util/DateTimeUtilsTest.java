package com.pwb.shared.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DateTimeUtils — date/time utility")
class DateTimeUtilsTest {

    @Test
    @DisplayName("should_format_iso_with_local_date_time")
    void should_format_iso_with_local_date_time() {
        LocalDateTime dt = LocalDateTime.of(2026, 7, 31, 12, 0, 0);

        String iso = DateTimeUtils.formatIso(dt);

        assertThat(iso).isEqualTo("2026-07-31T12:00:00");
    }

    @Test
    @DisplayName("should_format_date_with_yyyy_mm_dd_pattern")
    void should_format_date_with_yyyy_mm_dd_pattern() {
        LocalDateTime dt = LocalDateTime.of(2026, 1, 5, 23, 59, 59);

        String formatted = DateTimeUtils.formatDate(dt);

        assertThat(formatted).isEqualTo("2026-01-05");
    }

    @Test
    @DisplayName("should_format_datetime_with_yyyy_mm_dd_hh_mm_ss_pattern")
    void should_format_datetime_with_yyyy_mm_dd_hh_mm_ss_pattern() {
        LocalDateTime dt = LocalDateTime.of(2026, 12, 31, 23, 59, 59);

        String formatted = DateTimeUtils.formatDateTime(dt);

        assertThat(formatted).isEqualTo("2026-12-31 23:59:59");
    }

    @Test
    @DisplayName("should_return_null_when_input_is_null_for_format_methods")
    void should_return_null_when_input_is_null_for_format_methods() {
        assertThat(DateTimeUtils.formatIso(null)).isNull();
        assertThat(DateTimeUtils.formatDate(null)).isNull();
        assertThat(DateTimeUtils.formatDateTime(null)).isNull();
    }

    @Test
    @DisplayName("should_use_default_zone_asia_ho_chi_minh")
    void should_use_default_zone_asia_ho_chi_minh() {
        ZoneId zone = DateTimeUtils.defaultZone();

        assertThat(zone).isEqualTo(ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    @Test
    @DisplayName("should_parse_iso_string_back_to_local_date_time")
    void should_parse_iso_string_back_to_local_date_time() {
        String iso = "2026-07-31T12:00:00";

        LocalDateTime parsed = DateTimeUtils.parseIso(iso);

        assertThat(parsed).isEqualTo(LocalDateTime.of(2026, 7, 31, 12, 0, 0));
    }

    @Test
    @DisplayName("should_return_null_when_parsing_null_string")
    void should_return_null_when_parsing_null_string() {
        assertThat(DateTimeUtils.parseIso(null)).isNull();
    }

    @Test
    @DisplayName("should_convert_epoch_milli_to_local_date_time_in_default_zone")
    void should_convert_epoch_milli_to_local_date_time_in_default_zone() {
        LocalDateTime beforeEpoch = DateTimeUtils.ofEpochMilli(0L);

        assertThat(beforeEpoch).isNotNull();
        assertThat(beforeEpoch.getYear()).isEqualTo(1970);
    }
}