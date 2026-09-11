package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.service.shortlink.ShortLinkSafetyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShortLinkSafetyControllerTest {
    @Test
    void tenantCreateUsesAuthenticatedUsersTenantAndIgnoresSpoofedTenantId() {
        ShortLinkSafetyService service = mock(ShortLinkSafetyService.class);
        UserRepository users = mock(UserRepository.class);
        User user = new User();
        user.setId(101L);
        user.setTenantId(7L);
        when(users.findById(101L)).thenReturn(Optional.of(user));
        when(service.create(any(), eq("101"))).thenReturn(row(7L));
        var controller = new ShortLinkSafetyController(service, users);
        var auth = UsernamePasswordAuthenticationToken.authenticated("101", null, List.of());

        var response = controller.create(new ShortLinkSafetyService.CreateCommand(999L,
                "https://example.com/campaign", null, LocalDate.now().plusDays(30), List.of(), List.of()), auth);

        ArgumentCaptor<ShortLinkSafetyService.CreateCommand> command = ArgumentCaptor.forClass(ShortLinkSafetyService.CreateCommand.class);
        verify(service).create(command.capture(), eq("101"));
        assertThat(command.getValue().tenantId()).isEqualTo(7L);
        assertThat(response.getData().tenantId()).isEqualTo(7L);
    }

    private static ShortLinkSafetyService.ShortLinkRow row(long tenantId) {
        return new ShortLinkSafetyService.ShortLinkRow(1L, tenantId, "https://example.com/campaign",
                "s.ycsopen.test", "abc12345", "https://s.ycsopen.test/s/abc12345",
                LocalDate.now().plusDays(30), "PENDING", 0L, 1, "sha",
                "{\"verdict\":\"PASS\"}", "domain-evidence:example.com", "{}",
                "LOW", null, null, null, null, null, null);
    }
}
