package com.pwb.liveroom.application.command;


public record CreateRoomCommand(
        Actor actor,
        String roomName,
        Integer maxParticipants,
        Integer ownerGraceSeconds
) {
}