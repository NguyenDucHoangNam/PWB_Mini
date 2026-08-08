package com.pwb.iam.application.service;

import com.pwb.iam.application.dto.ProfileView;
import com.pwb.iam.domain.model.AvatarPolicy;
import com.pwb.infra.storage.StorageService;
import com.pwb.infra.storage.exception.StorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarUrlResolver {

    private final StorageService storageService;
    private final AvatarPolicy avatarPolicy;

    public ProfileView resolve(ProfileView view) {
        String resolved = resolve(view.avatarUrl());
        if (resolved == null ? view.avatarUrl() == null : resolved.equals(view.avatarUrl())) {
            return view;
        }
        return new ProfileView(
                view.userId(), view.email(), view.fullName(), resolved,
                view.status(), view.role(), view.oauthProvider());
    }

    public String resolve(String storedReference) {
        return resolve(storedReference, avatarPolicy.urlTtl());
    }

    /**
     * Presigns with a caller-chosen lifetime. The policy TTL is tuned for a page that can refetch
     * the profile at will; a long-lived screen that receives an avatar once needs a URL that
     * outlives the screen instead.
     */
    public String resolve(String storedReference, Duration ttl) {
        if (storedReference == null || storedReference.isBlank() || isAbsoluteUrl(storedReference)) {
            return storedReference;
        }
        try {
            return storageService
                    .generatePresignedUrl(storedReference, ttl)
                    .getUrl()
                    .toString();
        } catch (StorageException ex) {
            log.warn("Could not presign avatar, returning no avatar: key={} reason={}",
                    storedReference, ex.getMessage());
            return null;
        }
    }

    private static boolean isAbsoluteUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }
}
