package com.aura.voiceback.config;

import com.aura.voiceback.util.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // ✅ WebSocket 업그레이드 요청은 JWT 필터 건너뛰기
        if (request.getServletPath().startsWith("/ws/voip")) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            if (jwtTokenProvider.validateToken(token)) {
                String email = jwtTokenProvider.getEmail(token);

                // 컨트롤러에서 email 접근 가능하게 저장
                request.setAttribute("email", email);

                // 인증된 사용자로 등록해야 SecurityConfig의 authenticated() 규칙을 통과함
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(email, null, Collections.emptyList()));
            }
        }

        filterChain.doFilter(request, response);
    }
}

