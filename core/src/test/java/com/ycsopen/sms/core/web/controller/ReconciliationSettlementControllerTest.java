package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.service.billing.ReconciliationSettlementService;
import com.ycsopen.sms.core.service.billing.ReconciliationSettlementService.ConfirmationCommand;
import com.ycsopen.sms.core.service.billing.ReconciliationSettlementService.StatementRow;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReconciliationSettlementControllerTest {

    @Test
    void tenantCannotConfirmAnotherTenantStatement() {
        var service = mock(ReconciliationSettlementService.class);
        var users = mock(UserRepository.class);
        var controller = new ReconciliationSettlementController(service, users);
        var authentication = new TestingAuthenticationToken("501", "n/a", "ROLE_TENANT_ADMIN");
        var user = new User();
        user.setId(501L);
        user.setTenantId(42L);
        when(users.findById(501L)).thenReturn(Optional.of(user));
        when(service.getStatement(9001L)).thenReturn(statement(9001L, 77L));

        assertThatThrownBy(() -> controller.tenantConfirm(9001L,
                new ConfirmationCommand(true, null, null, null, null), authentication))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能访问其他机构账单");

        verify(service, never()).tenantConfirm(anyLong(), any(ConfirmationCommand.class), any(String.class));
    }

    @Test
    void tenantCannotReadAnotherTenantStatementDifferences() {
        var service = mock(ReconciliationSettlementService.class);
        var users = mock(UserRepository.class);
        var controller = new ReconciliationSettlementController(service, users);
        var authentication = new TestingAuthenticationToken("501", "n/a", "ROLE_TENANT_ADMIN");
        var user = new User();
        user.setId(501L);
        user.setTenantId(42L);
        when(users.findById(501L)).thenReturn(Optional.of(user));
        when(service.getStatement(9001L)).thenReturn(statement(9001L, 77L));

        assertThatThrownBy(() -> controller.differences(9001L, authentication))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能访问其他机构账单");

        verify(service, never()).differences(9001L);
    }

    private static StatementRow statement(long id, long tenantId) {
        return new StatementRow(id, tenantId, "STMT-" + tenantId, LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30), 0, 0, 0, 0, "PENDING", "NOT_SETTLED",
                "POSTPAID", "SMS_STANDARD_V1", null, null, null, null);
    }
}
