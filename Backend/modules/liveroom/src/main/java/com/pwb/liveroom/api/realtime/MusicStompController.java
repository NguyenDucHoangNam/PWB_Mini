package com.pwb.liveroom.api.realtime;

import com.pwb.liveroom.api.dto.request.MusicPlayRequest;
import com.pwb.liveroom.api.dto.request.MusicSeekRequest;
import com.pwb.liveroom.api.dto.request.MusicVolumeRequest;
import com.pwb.liveroom.api.support.Actors;
import com.pwb.liveroom.application.command.ControlPlaybackCommand;
import com.pwb.liveroom.application.command.SelectSongCommand;
import com.pwb.liveroom.application.usecase.ControlPlaybackUseCase;
import com.pwb.liveroom.application.usecase.GetPlaybackStateUseCase;
import com.pwb.liveroom.application.usecase.SelectSongUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;


@Controller
@RequiredArgsConstructor
public class MusicStompController {

    private final SelectSongUseCase selectSong;
    private final ControlPlaybackUseCase controlPlayback;
    private final GetPlaybackStateUseCase getPlaybackState;


    @MessageMapping("/liveroom/{roomId}/music/play")
    public void play(
            @DestinationVariable UUID roomId,
            @Payload MusicPlayRequest request,
            Principal principal
    ) {
        UUID actorId = Actors.userIdOf(principal);
        UUID songId = request == null ? null : request.songId();
        if (songId != null) {
            selectSong.execute(new SelectSongCommand(actorId, roomId, songId));
        } else {
            controlPlayback.execute(ControlPlaybackCommand.resume(actorId, roomId));
        }
    }

    @MessageMapping("/liveroom/{roomId}/music/pause")
    public void pause(@DestinationVariable UUID roomId, Principal principal) {
        controlPlayback.execute(ControlPlaybackCommand.pause(Actors.userIdOf(principal), roomId));
    }

    @MessageMapping("/liveroom/{roomId}/music/seek")
    public void seek(
            @DestinationVariable UUID roomId,
            @Payload MusicSeekRequest request,
            Principal principal
    ) {
        controlPlayback.execute(ControlPlaybackCommand.seek(
                Actors.userIdOf(principal), roomId, request == null ? null : request.positionSeconds()));
    }

    @MessageMapping("/liveroom/{roomId}/music/volume")
    public void volume(
            @DestinationVariable UUID roomId,
            @Payload MusicVolumeRequest request,
            Principal principal
    ) {
        controlPlayback.execute(ControlPlaybackCommand.volume(
                Actors.userIdOf(principal), roomId, request == null ? null : request.volumePercent()));
    }


    @MessageMapping("/liveroom/{roomId}/music/get-state")
    public void getState(@DestinationVariable UUID roomId, Principal principal) {
        getPlaybackState.execute(Actors.userIdOf(principal), roomId);
    }
}