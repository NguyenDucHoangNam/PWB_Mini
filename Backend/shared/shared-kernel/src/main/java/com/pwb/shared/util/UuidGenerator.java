package com.pwb.shared.util;

import lombok.experimental.UtilityClass;

import java.util.UUID;

@UtilityClass
public class UuidGenerator {

    public static String generate() {
        return UUID.randomUUID().toString();
    }

    public static String generateWithoutHyphens() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
