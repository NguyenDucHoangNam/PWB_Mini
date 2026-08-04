package com.pwb.liveroom.application.event;

import java.util.UUID;


public interface LiveroomEventPublisher {


    String CHAT_CHANNEL = "chat";


    String MUSIC_CHANNEL = "music";


    void broadcastToRoom(RoomEvent event);


    void broadcastToRoomChannel(RoomEvent event, String channel);


    void sendToUser(UUID userId, RoomEvent event);
}