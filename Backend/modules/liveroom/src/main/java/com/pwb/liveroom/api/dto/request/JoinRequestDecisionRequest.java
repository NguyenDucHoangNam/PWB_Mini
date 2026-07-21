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
public class JoinRequestDecisionRequest {

    @Size(max = 500, message = "{validation.liveroom.request.message.length}")
    private String reason;
}
