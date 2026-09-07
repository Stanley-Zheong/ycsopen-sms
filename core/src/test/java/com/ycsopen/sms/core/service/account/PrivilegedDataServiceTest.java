package com.ycsopen.sms.core.service.account;

import com.ycsopen.sms.core.service.audit.OperationAuditService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrivilegedDataServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-07T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void revealReturnsTemporaryValueOnlyAfterLinkedAuditSucceeds() {
        PlatformAccountPhoneStore phones = mock(PlatformAccountPhoneStore.class);
        OperationAuditService audits = mock(OperationAuditService.class);
        when(phones.revealForPrivilegedAccess(21L)).thenReturn("13812345678");
        when(audits.append(any())).thenReturn(91L);
        PrivilegedDataService service = new PrivilegedDataService(phones, audits, CLOCK);

        PrivilegedDataService.RevealedValue result = service.revealPhone(
                21L, 7L, PrivilegedDataService.RevealPurpose.CUSTOMER_SUPPORT,
                "192.0.2.9", "0123456789abcdef0123456789abcdef");

        assertThat(result.value()).isEqualTo("13812345678");
        assertThat(result.auditId()).isEqualTo(91L);
        assertThat(result.expiresAt()).isEqualTo(Instant.parse("2026-09-07T08:01:00Z"));
        ArgumentCaptorSupport.assertAuditContainsNoPlaintext(audits, "13812345678");
    }

    @Test
    void auditFailurePreventsRevealResponse() {
        PlatformAccountPhoneStore phones = mock(PlatformAccountPhoneStore.class);
        OperationAuditService audits = mock(OperationAuditService.class);
        when(phones.revealForPrivilegedAccess(21L)).thenReturn("13812345678");
        when(audits.append(any())).thenThrow(new IllegalStateException("audit unavailable"));
        PrivilegedDataService service = new PrivilegedDataService(phones, audits, CLOCK);

        assertThatThrownBy(() -> service.revealPhone(
                21L, 7L, PrivilegedDataService.RevealPurpose.COMPLIANCE_REVIEW,
                "192.0.2.9", "0123456789abcdef0123456789abcdef"))
                .isInstanceOf(IllegalStateException.class);
        verify(phones).revealForPrivilegedAccess(21L);
    }

    private static final class ArgumentCaptorSupport {
        private static void assertAuditContainsNoPlaintext(OperationAuditService audits, String plaintext) {
            org.mockito.ArgumentCaptor<OperationAuditService.AuditCommand> command =
                    org.mockito.ArgumentCaptor.forClass(OperationAuditService.AuditCommand.class);
            verify(audits).append(command.capture());
            assertThat(command.getValue().toString()).doesNotContain(plaintext);
            assertThat(command.getValue().sanitizedRequest()).contains("CUSTOMER_SUPPORT");
        }
    }
}
