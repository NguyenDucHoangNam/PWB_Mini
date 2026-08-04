package com.pwb.liveroom.api.support;

import com.pwb.liveroom.application.command.Actor;
import com.pwb.liveroom.application.exception.LiveroomBusinessException;
import com.pwb.liveroom.application.exception.LiveroomErrorCode;
import com.pwb.web.security.AuthenticatedUser;

import java.security.Principal;
import java.util.UUID;


public final class Actors {

    private Actors() {
    }

    public static Actor from(AuthenticatedUser user) {
        return new Actor(
                UUID.fromString(user.getUserId()),
                user.getEmail(),
                user.getAuthorities()
        );
    }


    public static UUID userIdOf(Principal principal) {
        if (principal == null) {
            throw new LiveroomBusinessException(LiveroomErrorCode.WS_UNAUTHORIZED);
        }
        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException ex) {
            throw new LiveroomBusinessException(LiveroomErrorCode.WS_UNAUTHORIZED);
        }
    }
}