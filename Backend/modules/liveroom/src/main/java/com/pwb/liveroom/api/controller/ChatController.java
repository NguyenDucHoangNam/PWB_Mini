package com.pwb.liveroom.api.controller;

import com.pwb.liveroom.api.dto.response.ChatHistoryResponse;
import com.pwb.liveroom.application.command.LoadChatHistoryCommand;
import com.pwb.liveroom.application.usecase.LoadChatHistoryUseCase;
import com.pwb.liveroom.domain.model.ChatMessage;
import com.pwb.shared.dto.ApiResponse;
import com.pwb.web.security.CurrentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;


@RestController
@RequestMapping("/api/v1/liveroom/rooms/{roomId}/chat")
@RequiredArgsConstructor
@Validated
public class ChatController {

    private final LoadChatHistoryUseCase loadChatHistory;


    @GetMapping("/messages")
    public ResponseEntity<ApiResponse<ChatHistoryResponse>> history(
            @CurrentUser UUID userId,
            @PathVariable UUID roomId,
            @RequestParam(required = false) UUID cursor,
            @RequestParam(defaultValue = "200") @Min(1) @Max(ChatMessage.DEFAULT_HISTORY_SIZE) int size
    ) {
        ChatHistoryResponse body = ChatHistoryResponse.from(
                loadChatHistory.execute(new LoadChatHistoryCommand(userId, roomId, cursor, size)));
        return ResponseEntity.ok(ApiResponse.success(body));
    }
}