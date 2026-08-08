package com.pwb.liveroom.application.usecase;

import com.pwb.liveroom.application.command.AddTrackCommentCommand;
import com.pwb.liveroom.application.view.TrackCommentView;


public interface AddTrackCommentUseCase {

    TrackCommentView execute(AddTrackCommentCommand command);
}