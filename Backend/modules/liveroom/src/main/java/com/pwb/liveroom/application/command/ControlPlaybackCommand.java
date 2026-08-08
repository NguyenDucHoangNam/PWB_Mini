package com.pwb.liveroom.application.command;

import java.util.UUID;


public record ControlPlaybackCommand(
        UUID actorId,
        UUID roomId,
        PlaybackAction action,
        Double positionSeconds,
        Integer volumePercent
) {

    public static ControlPlaybackCommand resume(UUID actorId, UUID roomId) {
        return new ControlPlaybackCommand(actorId, roomId, PlaybackAction.RESUME, null, null);
    }

    public static ControlPlaybackCommand pause(UUID actorId, UUID roomId) {
        return new ControlPlaybackCommand(actorId, roomId, PlaybackAction.PAUSE, null, null);
    }

    public static ControlPlaybackCommand seek(UUID actorId, UUID roomId, Double positionSeconds) {
        return new ControlPlaybackCommand(actorId, roomId, PlaybackAction.SEEK, positionSeconds, null);
    }

    public static ControlPlaybackCommand volume(UUID actorId, UUID roomId, Integer volumePercent) {
        return new ControlPlaybackCommand(actorId, roomId, PlaybackAction.VOLUME, null, volumePercent);
    }
}