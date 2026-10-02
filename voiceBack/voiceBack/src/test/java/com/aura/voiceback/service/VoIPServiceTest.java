package com.aura.voiceback.service;

import com.aura.voiceback.websocket.VoIPHandshakeInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VoIPServiceTest {

    private final VoIPService voipService = new VoIPService();

    private WebSocketSession session(String id, String email) {
        WebSocketSession s = mock(WebSocketSession.class);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(VoIPHandshakeInterceptor.ATTR_EMAIL, email);
        when(s.getId()).thenReturn(id);
        when(s.isOpen()).thenReturn(true);
        when(s.getAttributes()).thenReturn(attrs);
        return s;
    }

    @Test
    void forwardsAudioOnlyToOtherParticipantsInSameRoom() throws Exception {
        WebSocketSession a1 = session("a1", "a1@test.com");
        WebSocketSession a2 = session("a2", "a2@test.com");
        WebSocketSession b1 = session("b1", "b1@test.com");
        voipService.registerSession("roomA", a1);
        voipService.registerSession("roomA", a2);
        voipService.registerSession("roomB", b1);

        voipService.forwardAudio("roomA", "a1", new byte[]{1, 2, 3, 4});

        verify(a2, times(1)).sendMessage(any(BinaryMessage.class));
        verify(a1, never()).sendMessage(any(WebSocketMessage.class));
        verify(b1, never()).sendMessage(any(WebSocketMessage.class));
    }

    @Test
    void removedSessionNoLongerReceivesAudio() throws Exception {
        WebSocketSession a1 = session("a1", "a1@test.com");
        WebSocketSession a2 = session("a2", "a2@test.com");
        voipService.registerSession("roomA", a1);
        voipService.registerSession("roomA", a2);

        voipService.removeSession("roomA", "a2");
        voipService.forwardAudio("roomA", "a1", new byte[]{1, 2});

        verify(a2, never()).sendMessage(any(WebSocketMessage.class));
    }

    @Test
    void closeUserSessionsClosesOnlyThatUsersConnectionInRoom() throws Exception {
        WebSocketSession a1 = session("a1", "a1@test.com");
        WebSocketSession a2 = session("a2", "a2@test.com");
        WebSocketSession b2 = session("b2", "a2@test.com");
        voipService.registerSession("roomA", a1);
        voipService.registerSession("roomA", a2);
        voipService.registerSession("roomB", b2);

        voipService.closeUserSessions("roomA", "a2@test.com");

        verify(a2).close(CloseStatus.NORMAL);
        verify(a1, never()).close(any());
        verify(b2, never()).close(any());
    }
}
