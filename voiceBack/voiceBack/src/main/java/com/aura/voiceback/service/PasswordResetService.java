package com.aura.voiceback.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 비밀번호 재설정 인증코드 발급/검증
 * - SecureRandom으로 6자리 코드 생성
 * - 10분 후 만료, 5번 틀리면 폐기, 재요청은 60초 간격
 * (메모리 저장이라 서버 재시작 시 초기화됨)
 */
@Service
public class PasswordResetService {

    static final Duration CODE_TTL = Duration.ofMinutes(10);
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    static final int MAX_ATTEMPTS = 5;

    public enum VerifyResult { OK, INVALID, EXPIRED, TOO_MANY_ATTEMPTS }

    private static final class ResetCode {
        final String code;
        final Instant issuedAt;
        final Instant expiresAt;
        int failedAttempts;

        ResetCode(String code, Instant issuedAt, Instant expiresAt) {
            this.code = code;
            this.issuedAt = issuedAt;
            this.expiresAt = expiresAt;
        }
    }

    private final SecureRandom random = new SecureRandom();
    private final Map<String, ResetCode> codes = new ConcurrentHashMap<>();
    private final Clock clock;

    public PasswordResetService() {
        this(Clock.systemUTC());
    }

    PasswordResetService(Clock clock) {
        this.clock = clock;
    }

    /** 새 인증코드 발급. 재요청 대기시간(60초) 안이면 비어 있음 */
    public Optional<String> issueCode(String email) {
        Instant now = clock.instant();
        String newCode = String.format("%06d", random.nextInt(1_000_000));
        boolean[] issued = {false};

        codes.compute(email, (key, existing) -> {
            if (existing != null && now.isBefore(existing.issuedAt.plus(RESEND_COOLDOWN))) {
                return existing;
            }
            issued[0] = true;
            return new ResetCode(newCode, now, now.plus(CODE_TTL));
        });
        return issued[0] ? Optional.of(newCode) : Optional.empty();
    }

    /** 코드 확인. 성공·만료·시도 초과 시 코드는 폐기됨 */
    public VerifyResult verifyAndConsume(String email, String code) {
        Instant now = clock.instant();
        VerifyResult[] result = {VerifyResult.INVALID};

        codes.compute(email, (key, rc) -> {
            if (rc == null) {
                result[0] = VerifyResult.INVALID;
                return null;
            }
            if (now.isAfter(rc.expiresAt)) {
                result[0] = VerifyResult.EXPIRED;
                return null;
            }
            if (code != null && MessageDigest.isEqual(
                    rc.code.getBytes(StandardCharsets.UTF_8), code.getBytes(StandardCharsets.UTF_8))) {
                result[0] = VerifyResult.OK;
                return null;
            }
            rc.failedAttempts++;
            if (rc.failedAttempts >= MAX_ATTEMPTS) {
                result[0] = VerifyResult.TOO_MANY_ATTEMPTS;
                return null;
            }
            result[0] = VerifyResult.INVALID;
            return rc;
        });
        return result[0];
    }

    /** 메일 발송 실패 등으로 발급한 코드를 취소 */
    public void invalidate(String email) {
        codes.remove(email);
    }
}
