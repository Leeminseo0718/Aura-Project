package com.aura.voiceback.websocket;

import com.aura.voiceback.service.CallSessionManager;
import com.aura.voiceback.util.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * 통화 웹소켓 접속 시 JWT와 방 참가 여부를 확인
 * 브라우저 WebSocket은 헤더를 붙일 수 없어서 쿼리로 전달: /ws/voip?roomId=...&token=...
 */
@Component
@RequiredArgsConstructor
public class VoIPHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_EMAIL = "email";
    public static final String ATTR_ROOM_ID = "roomId";

    private final JwtTokenProvider jwtTokenProvider;
    private final CallSessionManager callSessionManager;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
        HttpServletRequest req = servletRequest.getServletRequest();
        String token = req.getParameter("token");
        String roomId = req.getParameter("roomId");

        if (token == null || !jwtTokenProvider.validateToken(token)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        String email = jwtTokenProvider.getEmail(token);
        if (roomId == null || !callSessionManager.isParticipant(roomId, email)) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        attributes.put(ATTR_EMAIL, email);
        attributes.put(ATTR_ROOM_ID, roomId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
