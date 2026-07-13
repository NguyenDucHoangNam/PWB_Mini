package com.pwb.backend.modules.liveroom.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IceServerListResponse {

    private List<IceServerEntry> iceServers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IceServerEntry {

        private List<String> urls;

        private String username;

        private String credential;
    }
}
