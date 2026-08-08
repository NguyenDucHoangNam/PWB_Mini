package com.pwb.liveroom.application.usecase;

import com.pwb.liveroom.application.view.ParticipantView;

import java.util.List;
import java.util.UUID;

public interface ListParticipantsUseCase {


    List<ParticipantView> execute(UUID actorId, UUID roomId);
}