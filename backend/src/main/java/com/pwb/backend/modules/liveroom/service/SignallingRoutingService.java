package com.pwb.backend.modules.liveroom.service;

import com.pwb.backend.modules.liveroom.dto.ws.SignallingErrorFrame;
import com.pwb.backend.modules.liveroom.dto.ws.SignallingForwardedFrame;
import com.pwb.backend.modules.liveroom.dto.ws.SignallingFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignallingRoutingService {

    public static final String SIGNALLING_USER_DESTINATION = "/queue/rooms/signalling";

    public static final String CODE_PEER_OFFLINE = "PEER_OFFLINE";

    private final SimpMessagingTemplate messagingTemplate;
    private final RoomMemberReader roomMemberReader;

    public void forward(String roomCode, UUID senderId, SignallingFrame frame) {
        UUID receiverId = frame.getReceiverId();
        if (receiverId == null || receiverId.equals(senderId)) {
            log.warn("SIGNALLING_INVALID_RECEIVER roomCode={} senderId={} receiverId={}",
                    roomCode, senderId, receiverId);
            return;
        }
        boolean senderInRoom = roomMemberReader.isMember(roomCode, senderId);
        if (!senderInRoom) {
            log.warn("SIGNALLING_SENDER_NOT_IN_ROOM roomCode={} senderId={}",
                    roomCode, senderId);
            return;
        }
        boolean receiverInRoom = roomMemberReader.isMember(roomCode, receiverId);
        if (!receiverInRoom) {
            deliverError(senderId, CODE_PEER_OFFLINE, receiverId);
            log.warn("SIGNALLING_TARGET_OFFLINE roomCode={} senderId={} receiverId={}",
                    roomCode, senderId, receiverId);
            return;
        }
        SignallingForwardedFrame forwarded = SignallingForwardedFrame.builder()
                .senderId(senderId)
                .receiverId(receiverId)
                .type(frame.getType())
                .payload(frame.getPayload())
                .build();
        try {
            messagingTemplate.convertAndSendToUser(
                    receiverId.toString(), SIGNALLING_USER_DESTINATION, forwarded);
            log.info("SIGNALLING_FORWARDED roomCode={} senderId={} receiverId={} type={}",
                    roomCode, senderId, receiverId, frame.getType());
        } catch (Exception ex) {
            log.warn("SIGNALLING_FORWARD_FAILED roomCode={} senderId={} receiverId={} reason={}",
                    roomCode, senderId, receiverId, ex.getMessage());
        }
    }

    private void deliverError(UUID senderId, String code, UUID peerId) {
        SignallingErrorFrame error = SignallingErrorFrame.builder()
                .event(SignallingErrorFrame.EVENT)
                .code(code)
                .peerId(peerId)
                .build();
        try {
            messagingTemplate.convertAndSendToUser(
                    senderId.toString(), SIGNALLING_USER_DESTINATION, error);
        } catch (Exception ex) {
            log.warn("SIGNALLING_ERROR_DELIVERY_FAILED senderId={} peerId={} reason={}",
                    senderId, peerId, ex.getMessage());
        }
    }
}
