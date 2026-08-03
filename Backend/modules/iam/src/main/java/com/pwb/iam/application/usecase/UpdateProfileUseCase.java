package com.pwb.iam.application.usecase;

import com.pwb.iam.application.command.UpdateProfileCommand;
import com.pwb.iam.application.dto.ProfileView;

public interface UpdateProfileUseCase {

    ProfileView execute(UpdateProfileCommand command);
}
