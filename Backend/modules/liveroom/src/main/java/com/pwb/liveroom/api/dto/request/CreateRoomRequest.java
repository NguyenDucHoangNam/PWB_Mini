package com.pwb.liveroom.api.dto.request;

import com.pwb.liveroom.application.command.Actor;
import com.pwb.liveroom.application.command.CreateRoomCommand;
import com.pwb.liveroom.domain.model.LiveRoom;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


public record CreateRoomRequest(
        @NotBlank @Size(max = 100) String roomName,

        @Min(LiveRoom.MIN_CAPACITY)
        @Max(LiveRoom.MAX_CAPACITY)
        Integer maxParticipants,

        @Min(LiveRoom.MIN_GRACE_SECONDS)
        @Max(LiveRoom.MAX_GRACE_SECONDS)
        Integer ownerGraceSeconds
) {

    public CreateRoomCommand toCommand(Actor actor) {
        return new CreateRoomCommand(actor, roomName, maxParticipants, ownerGraceSeconds);
    }
}