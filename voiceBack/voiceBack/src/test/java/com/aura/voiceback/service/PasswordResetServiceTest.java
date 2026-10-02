package com.aura.voiceback.service;

import com.aura.voiceback.service.PasswordResetService.VerifyResult;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetServiceTest {

    private static final String EMAIL = "user@test.com";

    /** 테스트에서 시간을 앞으로 돌릴 수 있는 Clock */
    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration d) { now = now.plus(d); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MutableClock clock = new MutableClock();
    private final PasswordResetService service = new PasswordResetService(clock);

    private static String wrong(String code) {
        return code.equals("000000") ? "000001" : "000000";
    }

    @Test
    void issuesSixDigitCodeThatWorksOnce() {
        String code = service.issueCode(EMAIL).orElseThrow();

        assertThat(code).matches("\\d{6}");
        assertThat(service.verifyAndConsume(EMAIL, code)).isEqualTo(VerifyResult.OK);
        assertThat(service.verifyAndConsume(EMAIL, code)).isEqualTo(VerifyResult.INVALID);
    }

    @Test
    void codeExpiresAfterTtl() {
        String code = service.issueCode(EMAIL).orElseThrow();

        clock.advance(PasswordResetService.CODE_TTL.plusSeconds(1));

        assertThat(service.verifyAndConsume(EMAIL, code)).isEqualTo(VerifyResult.EXPIRED);
    }

    @Test
    void codeIsDiscardedAfterMaxFailedAttempts() {
        String code = service.issueCode(EMAIL).orElseThrow();

        for (int i = 1; i < PasswordResetService.MAX_ATTEMPTS; i++) {
            assertThat(service.verifyAndConsume(EMAIL, wrong(code))).isEqualTo(VerifyResult.INVALID);
        }
        assertThat(service.verifyAndConsume(EMAIL, wrong(code))).isEqualTo(VerifyResult.TOO_MANY_ATTEMPTS);

        // 맞는 코드를 넣어도 이미 폐기됨
        assertThat(service.verifyAndConsume(EMAIL, code)).isEqualTo(VerifyResult.INVALID);
    }

    @Test
    void resendIsBlockedDuringCooldownThenAllowed() {
        String first = service.issueCode(EMAIL).orElseThrow();

        assertThat(service.issueCode(EMAIL)).isEmpty();

        clock.advance(PasswordResetService.RESEND_COOLDOWN);
        String second = service.issueCode(EMAIL).orElseThrow();

        // 새 코드가 발급되면 이전 코드는 무효 (같은 값이 우연히 나온 경우 제외)
        if (!first.equals(second)) {
            assertThat(service.verifyAndConsume(EMAIL, first)).isEqualTo(VerifyResult.INVALID);
        }
        assertThat(service.verifyAndConsume(EMAIL, second)).isEqualTo(VerifyResult.OK);
    }

    @Test
    void nullOrMissingCodeIsInvalid() {
        assertThat(service.verifyAndConsume(EMAIL, "123456")).isEqualTo(VerifyResult.INVALID);
        service.issueCode(EMAIL);
        assertThat(service.verifyAndConsume(EMAIL, null)).isEqualTo(VerifyResult.INVALID);
    }
}
