package com.pwb.iam.application.usecase;

import com.pwb.iam.application.command.UpdateAvatarCommand;

public interface UpdateAvatarUseCase {

    String execute(UpdateAvatarCommand command);
}
