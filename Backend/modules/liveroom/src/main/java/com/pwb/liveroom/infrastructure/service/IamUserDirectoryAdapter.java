package com.pwb.liveroom.infrastructure.service;

import com.pwb.iam.application.service.AvatarUrlResolver;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.liveroom.domain.service.UserDirectoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class IamUserDirectoryAdapter implements UserDirectoryPort {

    private static final Duration AVATAR_URL_TTL = Duration.ofHours(12);

    private final UserRepository userRepository;
    private final AvatarUrlResolver avatarUrlResolver;

    @Override
    @Transactional(readOnly = true)
    public String avatarUrlOf(UUID userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .map(User::getAvatarUrl)
                .map(stored -> avatarUrlResolver.resolve(stored, AVATAR_URL_TTL))
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> avatarUrlsOf(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> resolved = new HashMap<>();
        for (User user : userRepository.findAllById(userIds)) {
            String url = avatarUrlResolver.resolve(user.getAvatarUrl(), AVATAR_URL_TTL);
            if (url != null) {
                resolved.put(user.getUserId(), url);
            }
        }
        return resolved;
    }
}