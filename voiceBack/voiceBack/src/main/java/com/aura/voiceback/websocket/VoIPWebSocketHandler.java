package com.aura.voiceback.websocket;

import com.aura.voiceback.service.VoIPService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.nio.ByteBuffer;


@Component
public class VoIPWebSocketHandler extends AbstractWebSocketHandler {

    private final VoIPService voipService;

    public VoIPWebSocketHandler(VoIPService voipService) {
        this.voipService = voipService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        voipService.registerSession(roomIdOf(session), session);
        System.out.println("✅ WebSocket connected: " + session.getId() + " (room: " + roomIdOf(session) + ")");
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        ByteBuffer payload = message.getPayload();
        byte[] audioBytes = new byte[payload.remaining()];
        payload.get(audioBytes);
        // 같은 방의 다른 참가자에게만 전달
        voipService.forwardAudio(roomIdOf(session), session.getId(), audioBytes);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        voipService.removeSession(roomIdOf(session), session.getId());
        System.out.println("⚠️ WebSocket disconnected: " + session.getId());
    }

    // 핸드셰이크에서 검증 후 저장한 방 ID
    private String roomIdOf(WebSocketSession session) {
        return (String) session.getAttributes().get(VoIPHandshakeInterceptor.ATTR_ROOM_ID);
    }
}
