package com.pwb.backend.modules.liveroom.constant;

public final class LiveRoomRedisKeys {

    public static final String ROOM_STATUS_KEY_PREFIX = "room:status:";
    public static final String ROOM_LOCK_KEY_PREFIX = "room:lock:";
    public static final String HOST_DISCONNECT_KEY_PREFIX = "room:host_disconnect:";
    public static final String SESSION_ROOM_KEY_PREFIX = "room:ws_session:";
    public static final String ACTIVE_ROOM_ZSET_KEY = "room:active";

    public static final String ROOM_WAITING_KEY_PREFIX = "room:waiting:";
    public static final String ROOM_WAITING_METADATA_KEY_PREFIX = "room:waiting_metadata:";
    public static final String ROOM_MEMBERS_KEY_PREFIX = "room:members:";
    public static final String SESSION_LISTENER_KEY_PREFIX = "room:ws_listener:";
    public static final String SESSION_LISTENER_META_KEY_PREFIX = "room:ws_listener_meta:";

    public static String roomStatusKey(String roomCode) {
        return ROOM_STATUS_KEY_PREFIX + roomCode;
    }

    public static String roomLockKey(String roomCode) {
        return ROOM_LOCK_KEY_PREFIX + roomCode;
    }

    public static String hostDisconnectKey(String roomCode) {
        return HOST_DISCONNECT_KEY_PREFIX + roomCode;
    }

    public static String sessionRoomKey(String sessionId) {
        return SESSION_ROOM_KEY_PREFIX + sessionId;
    }

    public static String roomWaitingKey(String roomCode) {
        return ROOM_WAITING_KEY_PREFIX + roomCode;
    }

    public static String roomWaitingMetadataKey(String roomCode) {
        return ROOM_WAITING_METADATA_KEY_PREFIX + roomCode;
    }

    public static String roomMembersKey(String roomCode) {
        return ROOM_MEMBERS_KEY_PREFIX + roomCode;
    }

    public static String sessionListenerKey(String sessionId) {
        return SESSION_LISTENER_KEY_PREFIX + sessionId;
    }

    public static String sessionListenerMetaKey(String sessionId) {
        return SESSION_LISTENER_META_KEY_PREFIX + sessionId;
    }

    private LiveRoomRedisKeys() {
    }
}