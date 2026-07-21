package com.pwb.liveroom.api.dto.request;

import com.pwb.liveroom.api.enums.LiveRoomMode;
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

    private LiveRoomMode mode;

    @Size(min = 4, max = 64, message = "{validation.liveroom.password.length}")
    private String password;

    @Min(value = 2, message = "{validation.liveroom.capacity.range}")
    @Max(value = 500, message = "{validation.liveroom.capacity.range}")
    private Integer maxParticipants;
}