package com.ycsopen.sms.core.web;

import com.ycsopen.sms.core.common.security.HmacSignatureVerifier;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 PRD 9.1 节的 HTTP API 签名机制：待签名串拼接、签名可复现、时间戳容差、nonce 防重放。 */
class HmacSignatureVerifierTest {

    private final HmacSignatureVerifier verifier = new HmacSignatureVerifier();

    @Test
    void sameInput_shouldProduceSameSignature_deterministic() {
        String stringToSign = verifier.buildStringToSign("POST", "/api/v1/sms/send", "",
                "x-app-key:ak_123\nx-timestamp:1700000000", "{\"phoneNumber\":\"13800001111\"}");

        String sig1 = verifier.sign(stringToSign, "app-secret");
        String sig2 = verifier.sign(stringToSign, "app-secret");

        assertThat(sig1).isEqualTo(sig2);
    }

    @Test
    void verify_shouldAcceptCorrectSignature_andRejectTamperedBody() {
        String secret = "app-secret";
        String stringToSign = verifier.buildStringToSign("POST", "/api/v1/sms/send", "",
                "x-app-key:ak_123", "{\"phoneNumber\":\"13800001111\"}");
        String signature = verifier.sign(stringToSign, secret);

        assertThat(verifier.verify(signature, stringToSign, secret)).isTrue();

        String tamperedStringToSign = verifier.buildStringToSign("POST", "/api/v1/sms/send", "",
                "x-app-key:ak_123", "{\"phoneNumber\":\"13900002222\"}"); // 请求体被篡改
        assertThat(verifier.verify(signature, tamperedStringToSign, secret)).isFalse();
    }

    @Test
    void verifyTimestamp_within5Minutes_shouldPass() {
        long now = Instant.now().getEpochSecond();
        assertThat(verifier.verifyTimestamp(now)).isTrue();
        assertThat(verifier.verifyTimestamp(now - 200)).isTrue();  // 3分20秒前，仍在容差内
    }

    @Test
    void verifyTimestamp_beyond5Minutes_shouldFail() {
        long now = Instant.now().getEpochSecond();
        assertThat(verifier.verifyTimestamp(now - 400)).isFalse(); // 6分40秒前，超出 5 分钟容差
    }

    @Test
    void nonce_reusedValue_shouldBeRejectedOnSecondUse() {
        String nonce = "unique-nonce-" + System.nanoTime();
        assertThat(verifier.checkAndRecordNonce(nonce)).isTrue();  // 第一次：放行
        assertThat(verifier.checkAndRecordNonce(nonce)).isFalse(); // 第二次（重放）：拒绝
    }
}
