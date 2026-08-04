package com.pwb.liveroom.application.event;

import java.util.UUID;


public interface RealtimeSessionEvictor {


    void evictUser(UUID userId);
}