package com.ycsopen.sms.core.service.risk;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.FrequencyRule;
import com.ycsopen.sms.core.repository.FrequencyRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FrequencyRuleServiceTest {
    @Mock FrequencyRuleRepository repository;
    @Mock JdbcTemplate jdbc;

    @Test
    void savesValidatedTenantRule() {
        when(repository.existsByRuleNameAndLimitTypeAndScopeAndScopeRefIdAndStatus(
                "同号秒级", FrequencyRule.LimitType.MOBILE, FrequencyRule.Scope.TENANT, 17L,
                FrequencyRule.Status.ACTIVE)).thenReturn(false);
        when(repository.save(any())).thenAnswer(invocation -> {
            FrequencyRule rule = invocation.getArgument(0);
            rule.setId(7L);
            return rule;
        });

        var row = service().save(new FrequencyRuleService.RuleRequest(null, "同号秒级",
                "MOBILE", 3, 1, "BLOCK", "TENANT", 17L, "ACTIVE"));

        assertThat(row.id()).isEqualTo(7L);
        assertThat(row.scope()).isEqualTo("TENANT");
        ArgumentCaptor<FrequencyRule> saved = ArgumentCaptor.forClass(FrequencyRule.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getLimitWindowSeconds()).isEqualTo(1);
    }

    @Test
    void rejectsUnsupportedWindow() {
        assertThatThrownBy(() -> service().save(new FrequencyRuleService.RuleRequest(null,
                "五秒窗口", "IP", 1, 5, "BLOCK", "GLOBAL", null, "ACTIVE")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("FREQUENCY_WINDOW_INVALID");
    }

    @Test
    void importRulesReturnsPartialFailureEvidence() {
        when(repository.existsByRuleNameAndLimitTypeAndScopeAndScopeRefIdAndStatus(
                "有效规则", FrequencyRule.LimitType.IP, FrequencyRule.Scope.GLOBAL, null,
                FrequencyRule.Status.ACTIVE)).thenReturn(false);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service().importRules(new FrequencyRuleService.ImportRequest(
                List.of("有效规则", ""), "IP", 10, 60, "ALERT", "GLOBAL", null));

        assertThat(result.success()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.errors()).hasSize(1);
    }

    @Test
    void disableAndEnableAreSoftStateTransitions() {
        FrequencyRule rule = rule();
        when(repository.findById(1L)).thenReturn(Optional.of(rule));
        when(repository.save(rule)).thenReturn(rule);

        assertThat(service().disable(1L).status()).isEqualTo("DISABLED");
        assertThat(service().enable(1L).status()).isEqualTo("ACTIVE");
    }

    @Test
    void exportRequestRecordsFiltersAndMatchedRows() {
        when(repository.findAll()).thenReturn(List.of(rule()));

        var result = service().exportRequest("同号", "MOBILE", "BLOCK", "ACTIVE", "operator-7");

        assertThat(result.matchedRows()).isEqualTo(1);
        verify(jdbc).update(any(String.class), any(), eq("同号"), eq("MOBILE"), eq("BLOCK"),
                eq("ACTIVE"), eq(1), eq("operator-7"), eq("REQUESTED"), any());
    }

    private FrequencyRuleService service() {
        return new FrequencyRuleService(repository, jdbc);
    }

    private FrequencyRule rule() {
        FrequencyRule rule = new FrequencyRule();
        rule.setId(1L);
        rule.setRuleName("同号秒级");
        rule.setLimitType(FrequencyRule.LimitType.MOBILE);
        rule.setLimitCount(3);
        rule.setLimitWindowSeconds(1);
        rule.setAction(FrequencyRule.Action.BLOCK);
        rule.setScope(FrequencyRule.Scope.GLOBAL);
        rule.setStatus(FrequencyRule.Status.ACTIVE);
        rule.setHitCount(0L);
        return rule;
    }
}
