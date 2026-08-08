package com.pwb.liveroom.infrastructure.service;

import com.pwb.liveroom.domain.model.vo.RoomCode;
import com.pwb.liveroom.domain.service.RoomCodeGenerator;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;


@Service
public class SecureRandomRoomCodeGeneratorAdapter implements RoomCodeGenerator {

    private final SecureRandom random = new SecureRandom();

    @Override
    public RoomCode generate() {
        StringBuilder builder = new StringBuilder(RoomCode.LENGTH);
        for (int i = 0; i < RoomCode.LENGTH; i++) {
            builder.append(RoomCode.ALPHABET.charAt(random.nextInt(RoomCode.ALPHABET.length())));
        }
        return RoomCode.of(builder.toString());
    }
}