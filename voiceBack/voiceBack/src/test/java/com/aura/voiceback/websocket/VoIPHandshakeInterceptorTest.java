package com.aura.voiceback.websocket;

import com.aura.voiceback.service.CallSessionManager;
import com.aura.voiceback.util.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VoIPHandshakeInterceptorTest {

    private final JwtTokenProvider jwt = new JwtTokenProvider("test-secret-key-for-jwt-signing-0123456789", 60_000);
    private final CallSessionManager callSessionManager = new CallSessionManager();
    private final VoIPHandshakeInterceptor interceptor = new VoIPHandshakeInterceptor(jwt, callSessionManager);

    private record Result(boolean accepted, int status, Map<String, Object> attributes) {}

    private Result handshake(String token, String roomId) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/ws/voip");
        if (token != null) req.setParameter("token", token);
        if (roomId != null) req.setParameter("roomId", roomId);
        MockHttpServletResponse res = new MockHttpServletResponse();
        ServletServerHttpResponse response = new ServletServerHttpResponse(res);
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(new ServletServerHttpRequest(req), response, null, attributes);
        return new Result(accepted, res.getStatus(), attributes);
    }

    @Test
    void rejectsMissingToken() {
        String roomId = callSessionManager.createRoom("owner@test.com", "room");
        Result r = handshake(null, roomId);
        assertThat(r.accepted()).isFalse();
        assertThat(r.status()).isEqualTo(401);
    }

    @Test
    void rejectsInvalidToken() {
        String roomId = callSessionManager.createRoom("owner@test.com", "room");
        Result r = handshake("not-a-jwt", roomId);
        assertThat(r.accepted()).isFalse();
        assertThat(r.status()).isEqualTo(401);
    }

    @Test
    void rejectsUserWhoIsNotInRoom() {
        String roomId = callSessionManager.createRoom("owner@test.com", "room");
        Result r = handshake(jwt.generateAccessToken("stranger@test.com"), roomId);
        assertThat(r.accepted()).isFalse();
        assertThat(r.status()).isEqualTo(403);
    }

    @Test
    void acceptsParticipantAndStoresIdentity() {
        String roomId = callSessionManager.createRoom("owner@test.com", "room");
        callSessionManager.joinRoom("guest@test.com", roomId);

        Result r = handshake(jwt.generateAccessToken("guest@test.com"), roomId);

        assertThat(r.accepted()).isTrue();
        assertThat(r.attributes())
                .containsEntry(VoIPHandshakeInterceptor.ATTR_EMAIL, "guest@test.com")
                .containsEntry(VoIPHandshakeInterceptor.ATTR_ROOM_ID, roomId);
    }

    @Test
    void rejectsUserAfterLeavingRoom() {
        String roomId = callSessionManager.createRoom("owner@test.com", "room");
        callSessionManager.joinRoom("guest@test.com", roomId);
        callSessionManager.leaveRoom("guest@test.com", roomId);

        Result r = handshake(jwt.generateAccessToken("guest@test.com"), roomId);
        assertThat(r.accepted()).isFalse();
        assertThat(r.status()).isEqualTo(403);
    }
}
