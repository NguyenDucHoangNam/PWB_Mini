package com.pwb.backend.modules.liveroom.service;

import com.pwb.backend.modules.liveroom.config.LiveRoomProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
@RequiredArgsConstructor
public class RoomCodeGenerator {

    private final LiveRoomProperties properties;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        char[] alphabet = properties.getAlphabet().toCharArray();
        int length = properties.getCodeLength();
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(alphabet[secureRandom.nextInt(alphabet.length)]);
        }
        return builder.toString();
    }
}