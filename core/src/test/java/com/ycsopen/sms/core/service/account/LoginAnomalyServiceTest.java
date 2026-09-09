package com.ycsopen.sms.core.service.account;

import com.ycsopen.sms.core.service.audit.SecurityEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LoginAnomalyServiceTest {
    @Mock JdbcTemplate jdbc;
    @Mock SecurityEventService securityEvents;

    @Test
    void changedSourceIsUnusualAndProducesDurableHandoff() {
        LoginAnomalyService service = new LoginAnomalyService(jdbc, securityEvents);

        assertThat(service.isUnusual("192.0.2.1", "192.0.2.2")).isTrue();
        assertThat(service.isUnusual("192.0.2.1", "192.0.2.1")).isFalse();

        service.enqueue(7L, 3L, "session-7", "192.0.2.2",
                "0123456789abcdef0123456789abcdef");
        verify(jdbc).update(contains("INSERT INTO identity_notification_outbox"),
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq("session-7"));
        verify(securityEvents).recordUnusualLogin(7L, 3L, "session-7", "192.0.2.2",
                "0123456789abcdef0123456789abcdef");
    }
}
