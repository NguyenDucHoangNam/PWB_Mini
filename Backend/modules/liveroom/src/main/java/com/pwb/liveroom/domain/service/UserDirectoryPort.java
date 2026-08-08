package com.pwb.liveroom.domain.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;


public interface UserDirectoryPort {

    String avatarUrlOf(UUID userId);

    Map<UUID, String> avatarUrlsOf(Collection<UUID> userIds);
}