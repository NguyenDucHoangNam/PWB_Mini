package com.pwb.liveroom.api.dto.request;

import com.pwb.liveroom.api.enums.LiveRoomMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
public class CreateLiveRoomRequest {

    @NotBlank(message = "{validation.liveroom.title.required}")
    @Size(max = 200, message = "{validation.liveroom.title.maxlength}")
    private String title;

    @Size(max = 1000, message = "{validation.liveroom.description.maxlength}")
    private String description;

    @Size(max = 100, message = "{validation.liveroom.hostDisplayName.maxlength}")
    private String hostDisplayName;

    @NotNull(message = "{validation.liveroom.mode.required}")
    private LiveRoomMode mode;

    @Min(value = 2, message = "{validation.liveroom.capacity.range}")
    @Max(value = 5, message = "{validation.liveroom.capacity.range}")
    private Integer maxParticipants;
}
