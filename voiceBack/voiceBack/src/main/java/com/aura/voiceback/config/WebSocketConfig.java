package com.aura.voiceback.config;

import com.aura.voiceback.websocket.VoIPHandshakeInterceptor;
import com.aura.voiceback.websocket.VoIPWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final VoIPWebSocketHandler voipHandler;
    private final VoIPHandshakeInterceptor voipHandshakeInterceptor;

    public WebSocketConfig(VoIPWebSocketHandler voipHandler, VoIPHandshakeInterceptor voipHandshakeInterceptor) {
        this.voipHandler = voipHandler;
        this.voipHandshakeInterceptor = voipHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(voipHandler, "/ws/voip")
                .addInterceptors(voipHandshakeInterceptor) // JWT + 방 참가자 확인
                .setAllowedOrigins("*");
    }
}
