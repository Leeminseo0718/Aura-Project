package com.aura.voiceback.controller;

import com.aura.voiceback.config.JwtAuthenticationFilter;
import com.aura.voiceback.config.SecurityConfig;
import com.aura.voiceback.service.CallSessionManager;
import com.aura.voiceback.service.VoIPService;
import com.aura.voiceback.util.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CallController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        CallSessionManager.class, VoIPService.class})
@TestPropertySource(properties = "jwt.secret=test-secret-key-for-jwt-signing-0123456789")
class CallControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwt;

    @Autowired
    private CallSessionManager callSessionManager;

    @Test
    void callApiRequiresLogin() throws Exception {
        mockMvc.perform(get("/call/room/list")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/call/room/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomName\":\"r\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedAuthEndpointIsNotShadowedByAuthWildcard() throws Exception {
        // /auth/** permitAll 보다 먼저 매칭되어 401 이어야 함
        mockMvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void createRoomUsesEmailFromJwtNotRequestBody() throws Exception {
        String token = jwt.generateAccessToken("owner@test.com");

        mockMvc.perform(post("/call/room/create")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomName\":\"r\",\"creatorId\":\"someone-else@test.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creatorId").value("owner@test.com"));

        String roomId = callSessionManager.getAllRooms().get(0).getId();
        assertThat(callSessionManager.isParticipant(roomId, "owner@test.com")).isTrue();
        assertThat(callSessionManager.isParticipant(roomId, "someone-else@test.com")).isFalse();
    }
}
