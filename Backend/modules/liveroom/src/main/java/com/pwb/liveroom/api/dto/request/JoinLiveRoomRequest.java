package com.pwb.liveroom.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JoinLiveRoomRequest {

    @Size(min = 1, max = 100, message = "{validation.liveroom.displayname.length}")
    private String displayName;

    @NotNull(message = "{validation.liveroom.micMuted.required}")
    private Boolean micMuted;

    @NotNull(message = "{validation.liveroom.cameraOff.required}")
    private Boolean cameraOff;
}
