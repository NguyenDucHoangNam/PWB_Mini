package com.pwb.shared.util;

import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@UtilityClass
public class DateTimeUtils {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public static String formatIso(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(ISO_FORMATTER);
    }

    public static String formatDate(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(DATE_FORMATTER);
    }

    public static String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(DATETIME_FORMATTER);
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(DEFAULT_ZONE);
    }

    public static long nowMillis() {
        return Instant.now().toEpochMilli();
    }

    public static LocalDateTime ofEpochMilli(long epochMilli) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), DEFAULT_ZONE);
    }

    public static LocalDateTime parseIso(String value) {
        return value == null ? null : LocalDateTime.parse(value, ISO_FORMATTER);
    }

    public static ZoneId defaultZone() {
        return DEFAULT_ZONE;
    }
}
