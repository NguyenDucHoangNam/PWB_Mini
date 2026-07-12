package com.pwb.backend.modules.audio.service;

import com.pwb.backend.modules.share.entity.DemoDistribution;

import java.util.UUID;

public interface PlaylistService {

    String buildM3U8(UUID shareToken, DemoDistribution distribution);
}
