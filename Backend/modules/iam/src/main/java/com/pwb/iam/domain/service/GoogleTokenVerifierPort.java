package com.pwb.iam.domain.service;

import com.pwb.iam.domain.model.GoogleUserInfo;

public interface GoogleTokenVerifierPort {

    GoogleUserInfo verify(String idToken);
}