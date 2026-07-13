package com.pwb.backend.modules.liveroom.dto.ws;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatFrame {

    public static final String TYPE_TEXT = "TEXT";

    public static final String TYPE_REACTION = "REACTION";

    private String type;

    private String content;
}
