package com.pwb.liveroom.api.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateJoinRequestRequest {

    @Size(min = 1, max = 100, message = "{validation.liveroom.displayname.length}")
    private String displayName;

    @Size(max = 500, message = "{validation.liveroom.request.message.length}")
    private String message;
}
