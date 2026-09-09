package com.ycsopen.sms.core.service.risk;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.SensitiveWord;
import com.ycsopen.sms.core.repository.SensitiveWordRepository;
import com.ycsopen.sms.core.service.routing.ContentReviewChecker;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentSafetyServiceTest {
    @Mock SensitiveWordRepository repository;
    @Mock ContentReviewChecker checker;
    @Mock JdbcTemplate jdbc;

    @Test
    void savesValidatedTenantPolicyAndDefaultsReplacement() {
        when(repository.existsByWordAndScopeAndScopeRefIdAndStatus("营销", SensitiveWord.Scope.TENANT, 17L,
                SensitiveWord.Status.ACTIVE)).thenReturn(false);
        when(repository.save(any())).thenAnswer(invocation -> {
            SensitiveWord word = invocation.getArgument(0);
            word.setId(7L);
            return word;
        });

        var row = service().save(new ContentSafetyService.PolicyRequest(null, "营销", "MARKETING",
                "HIGH", "", "REPLACE", "TENANT", 17L, "ACTIVE"));

        assertThat(row.id()).isEqualTo(7L);
        assertThat(row.replacement()).isEqualTo("***");
        ArgumentCaptor<SensitiveWord> saved = ArgumentCaptor.forClass(SensitiveWord.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getScopeRefId()).isEqualTo(17L);
    }

    @Test
    void rejectsScopedPolicyWithoutScopeReference() {
        assertThatThrownBy(() -> service().save(new ContentSafetyService.PolicyRequest(null, "营销",
                "MARKETING", "HIGH", "***", "BLOCK", "TENANT", null, "ACTIVE")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("CONTENT_SCOPE_REF_REQUIRED");
    }

    @Test
    void importPoliciesReturnsPartialFailureEvidence() {
        when(repository.existsByWordAndScopeAndScopeRefIdAndStatus("有效词", SensitiveWord.Scope.GLOBAL, null,
                SensitiveWord.Status.ACTIVE)).thenReturn(false);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service().importPolicies(new ContentSafetyService.ImportRequest(
                List.of("有效词", ""), "OTHER", "LOW", null, "ALERT", "GLOBAL", null));

        assertThat(result.success()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.errors()).hasSize(1);
    }

    @Test
    void exportRequestRecordsFiltersAndMatchedRows() {
        SensitiveWord word = new SensitiveWord();
        word.setId(1L);
        word.setWord("营销");
        word.setCategory(SensitiveWord.Category.MARKETING);
        word.setLevel(SensitiveWord.Level.HIGH);
        word.setAction(SensitiveWord.Action.BLOCK);
        word.setScope(SensitiveWord.Scope.GLOBAL);
        word.setStatus(SensitiveWord.Status.ACTIVE);
        word.setHitCount(3L);
        when(repository.findAll()).thenReturn(List.of(word));

        var result = service().exportRequest("营销", "MARKETING", "BLOCK", "ACTIVE", "operator-7");

        assertThat(result.matchedRows()).isEqualTo(1);
        assertThat(result.status()).isEqualTo("REQUESTED");
        verify(jdbc).update(any(String.class), any(), eq("营销"), eq("MARKETING"), eq("BLOCK"),
                eq("ACTIVE"), eq(1), eq("operator-7"), eq("REQUESTED"), any());
    }

    @Test
    void disableSoftDeletesPolicyForAuditReplay() {
        SensitiveWord word = new SensitiveWord();
        word.setId(1L);
        word.setWord("营销");
        word.setCategory(SensitiveWord.Category.MARKETING);
        word.setLevel(SensitiveWord.Level.HIGH);
        word.setAction(SensitiveWord.Action.BLOCK);
        word.setScope(SensitiveWord.Scope.GLOBAL);
        word.setStatus(SensitiveWord.Status.ACTIVE);
        word.setHitCount(0L);
        when(repository.findById(1L)).thenReturn(Optional.of(word));
        when(repository.save(word)).thenReturn(word);

        var row = service().disable(1L);

        assertThat(row.status()).isEqualTo("DISABLED");
    }

    @Test
    void scanUsesDryRunPreviewWithoutPersistingHits() {
        when(checker.preview(any())).thenReturn(new ContentReviewChecker.Result(false, null, "安全内容"));

        var result = service().scan(new ContentSafetyService.ScanRequest(17L, 8L, "安全内容"));

        assertThat(result.finalContent()).isEqualTo("安全内容");
        verify(checker).preview(any());
        verifyNoInteractions(repository, jdbc);
    }

    private ContentSafetyService service() {
        return new ContentSafetyService(repository, checker, jdbc);
    }
}
