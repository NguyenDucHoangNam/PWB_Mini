package com.pwb.liveroom.domain.service;


public interface RoomCodeLookupThrottle {


    boolean tryConsume(String clientIp);
}