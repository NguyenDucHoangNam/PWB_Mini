package com.pwb.backend.modules.liveroom.dto.ws;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignallingForwardedFrame {

    private UUID senderId;

    private UUID receiverId;

    private String type;

    private String payload;
}
