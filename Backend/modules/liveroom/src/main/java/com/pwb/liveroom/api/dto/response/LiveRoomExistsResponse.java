package com.pwb.liveroom.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveRoomExistsResponse {

    private String roomCode;
    private boolean exists;
    private boolean active;
    private boolean passwordRequired;
}