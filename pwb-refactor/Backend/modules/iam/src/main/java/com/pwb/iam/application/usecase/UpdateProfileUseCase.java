package com.pwb.iam.application.usecase;

import com.pwb.iam.application.command.UpdateProfileCommand;
import com.pwb.iam.application.facade.ProfileView;

public interface UpdateProfileUseCase {

    ProfileView execute(UpdateProfileCommand command);
}
