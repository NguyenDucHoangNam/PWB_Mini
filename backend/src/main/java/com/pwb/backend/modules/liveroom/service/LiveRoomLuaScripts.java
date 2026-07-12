package com.pwb.backend.modules.liveroom.service;

import lombok.Getter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Getter
public class LiveRoomLuaScripts {

    private static final String SCRIPT_OPEN_CHECK_INCREMENT = "scripts/liveroom/open_check_increment.lua";
    private static final String SCRIPT_APPROVE_STATE_MIGRATION = "scripts/liveroom/approve_state_migration.lua";
    private static final String SCRIPT_KICK_LISTENER = "scripts/liveroom/kick_listener.lua";
    private static final String SCRIPT_LISTENER_DISCONNECT = "scripts/liveroom/listener_disconnect.lua";

    private final DefaultRedisScript<Long> openCheckIncrement;
    private final DefaultRedisScript<Long> approveStateMigration;
    private final DefaultRedisScript<Long> kickListener;
    private final DefaultRedisScript<Long> listenerDisconnect;

    public LiveRoomLuaScripts() {
        this.openCheckIncrement = loadScript(SCRIPT_OPEN_CHECK_INCREMENT);
        this.approveStateMigration = loadScript(SCRIPT_APPROVE_STATE_MIGRATION);
        this.kickListener = loadScript(SCRIPT_KICK_LISTENER);
        this.listenerDisconnect = loadScript(SCRIPT_LISTENER_DISCONNECT);
    }

    private DefaultRedisScript<Long> loadScript(String classpathResource) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        try {
            String body = StreamUtils.copyToString(
                    new ClassPathResource(classpathResource).getInputStream(),
                    StandardCharsets.UTF_8);
            script.setScriptText(body);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load " + classpathResource, ex);
        }
        return script;
    }
}