package com.pwb.liveroom.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLiveRoomSettingsRequest {

    @Size(max = 200, message = "{validation.liveroom.title.maxlength}")
    private String title;

    @Size(max = 1000, message = "{validation.liveroom.description.maxlength}")
    private String description;

    @Min(value = 2, message = "{validation.liveroom.capacity.range}")
    @Max(value = 500, message = "{validation.liveroom.capacity.range}")
    private Integer maxParticipants;
}
