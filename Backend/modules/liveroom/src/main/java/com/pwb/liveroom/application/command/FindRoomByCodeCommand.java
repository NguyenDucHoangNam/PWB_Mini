package com.pwb.liveroom.application.command;


public record FindRoomByCodeCommand(String roomCode, String clientIp) {
}