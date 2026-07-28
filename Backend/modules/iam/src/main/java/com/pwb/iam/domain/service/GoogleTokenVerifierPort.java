package com.pwb.iam.domain.service;

import com.pwb.iam.api.dto.GoogleIdTokenPayload;

public interface GoogleTokenVerifierPort {

    GoogleIdTokenPayload verify(String idToken);
}
