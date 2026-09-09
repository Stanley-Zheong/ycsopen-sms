package com.ycsopen.sms.core.service.routing;

import com.ycsopen.sms.core.domain.entity.SensitiveWord;
import com.ycsopen.sms.core.repository.SensitiveWordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentReviewCheckerTest {
    @Mock SensitiveWordRepository repository;
    @Mock JdbcTemplate jdbc;

    @Test
    void scansCanonicalFinalRenderedContentIncludingVariableValuesAndBlocks() {
        SensitiveWord word = policy(1L, "abc", SensitiveWord.Category.ILLEGAL, SensitiveWord.Level.HIGH,
                SensitiveWord.Action.BLOCK, SensitiveWord.Scope.TENANT, 17L, null);
        when(repository.findAllByStatus(SensitiveWord.Status.ACTIVE)).thenReturn(List.of(word));

        var result = newChecker().check(RoutingContext.builder()
                .tenantId(17L)
                .templateId(8L)
                .content("【签名】变量填入ＡＢＣ")
                .build());

        assertThat(result.blocked()).isTrue();
        assertThat(result.reason()).contains("ILLEGAL").contains("HIGH").contains("a**");
        assertThat(word.getHitCount()).isEqualTo(1);
        verify(repository).save(word);
        verify(jdbc).update(any(String.class), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void appliesDeterministicScopeLevelAndActionPrecedenceAndReturnsReplacedContent() {
        SensitiveWord globalBlock = policy(1L, "会员", SensitiveWord.Category.MARKETING, SensitiveWord.Level.LOW,
                SensitiveWord.Action.BLOCK, SensitiveWord.Scope.GLOBAL, null, null);
        SensitiveWord productReplace = policy(2L, "会员", SensitiveWord.Category.MARKETING, SensitiveWord.Level.HIGH,
                SensitiveWord.Action.REPLACE, SensitiveWord.Scope.PRODUCT, 8L, "用户");
        SensitiveWord tenantAlert = policy(3L, "到期", SensitiveWord.Category.OTHER, SensitiveWord.Level.MEDIUM,
                SensitiveWord.Action.ALERT, SensitiveWord.Scope.TENANT, 17L, null);
        when(repository.findAllByStatus(SensitiveWord.Status.ACTIVE))
                .thenReturn(List.of(globalBlock, tenantAlert, productReplace));

        var result = newChecker().check(RoutingContext.builder()
                .tenantId(17L)
                .templateId(8L)
                .content("会员权益到期")
                .build());

        assertThat(result.blocked()).isFalse();
        assertThat(result.finalContent()).isEqualTo("用户权益到期");
        assertThat(productReplace.getHitCount()).isEqualTo(1);
        assertThat(tenantAlert.getHitCount()).isEqualTo(1);
        assertThat(globalBlock.getHitCount()).isZero();
    }

    @Test
    void tenantScopedPolicyDoesNotLeakAcrossTenants() {
        SensitiveWord word = policy(1L, "内部", SensitiveWord.Category.OTHER, SensitiveWord.Level.HIGH,
                SensitiveWord.Action.BLOCK, SensitiveWord.Scope.TENANT, 17L, null);
        when(repository.findAllByStatus(SensitiveWord.Status.ACTIVE)).thenReturn(List.of(word));

        var result = newChecker().check(RoutingContext.builder()
                .tenantId(18L)
                .content("内部通知")
                .build());

        assertThat(result.blocked()).isFalse();
        assertThat(result.finalContent()).isEqualTo("内部通知");
        assertThat(word.getHitCount()).isZero();
    }

    @Test
    void passPreservesOriginalCaseAndWidthWhenNoPolicyMatches() {
        when(repository.findAllByStatus(SensitiveWord.Status.ACTIVE)).thenReturn(List.of());

        var result = newChecker().check(RoutingContext.builder()
                .tenantId(17L)
                .content("Your OTP is AB12CD，访问 Http://Example.com/X")
                .build());

        assertThat(result.blocked()).isFalse();
        assertThat(result.finalContent()).isEqualTo("Your OTP is AB12CD，访问 Http://Example.com/X");
    }

    @Test
    void replaceUsesCanonicalMatchButPreservesUnmatchedOriginalText() {
        SensitiveWord word = policy(1L, "abc", SensitiveWord.Category.MARKETING, SensitiveWord.Level.HIGH,
                SensitiveWord.Action.REPLACE, SensitiveWord.Scope.GLOBAL, null, "用户");
        when(repository.findAllByStatus(SensitiveWord.Status.ACTIVE)).thenReturn(List.of(word));

        var result = newChecker().check(RoutingContext.builder()
                .tenantId(17L)
                .content("Your code ＡＢＣ keeps Case")
                .build());

        assertThat(result.blocked()).isFalse();
        assertThat(result.finalContent()).isEqualTo("Your code 用户 keeps Case");
    }

    @Test
    void replaceHandlesExpandedUnicodeNormalizationWithoutOverlappingRanges() {
        SensitiveWord word = policy(1L, "f", SensitiveWord.Category.MARKETING, SensitiveWord.Level.HIGH,
                SensitiveWord.Action.REPLACE, SensitiveWord.Scope.GLOBAL, null, "*");
        when(repository.findAllByStatus(SensitiveWord.Status.ACTIVE)).thenReturn(List.of(word));

        var result = newChecker().check(RoutingContext.builder()
                .tenantId(17L)
                .content("code ﬃ end")
                .build());

        assertThat(result.blocked()).isFalse();
        assertThat(result.finalContent()).isEqualTo("code * end");
    }

    @Test
    void previewComputesDecisionWithoutRecordingPermanentHitEvidence() {
        SensitiveWord word = policy(1L, "会员", SensitiveWord.Category.MARKETING, SensitiveWord.Level.HIGH,
                SensitiveWord.Action.REPLACE, SensitiveWord.Scope.GLOBAL, null, "用户");
        when(repository.findAllByStatus(SensitiveWord.Status.ACTIVE)).thenReturn(List.of(word));

        var result = newChecker().preview(RoutingContext.builder()
                .tenantId(17L)
                .content("会员权益")
                .build());

        assertThat(result.blocked()).isFalse();
        assertThat(result.finalContent()).isEqualTo("用户权益");
        assertThat(word.getHitCount()).isZero();
        verify(repository).findAllByStatus(SensitiveWord.Status.ACTIVE);
        verifyNoInteractions(jdbc);
    }

    private ContentReviewChecker newChecker() {
        return new ContentReviewChecker(repository, jdbc);
    }

    private SensitiveWord policy(Long id, String word, SensitiveWord.Category category, SensitiveWord.Level level,
                                 SensitiveWord.Action action, SensitiveWord.Scope scope, Long scopeRefId,
                                 String replacement) {
        SensitiveWord entity = new SensitiveWord();
        entity.setId(id);
        entity.setWord(word);
        entity.setCategory(category);
        entity.setLevel(level);
        entity.setAction(action);
        entity.setScope(scope);
        entity.setScopeRefId(scopeRefId);
        entity.setReplacement(replacement);
        entity.setStatus(SensitiveWord.Status.ACTIVE);
        entity.setHitCount(0L);
        return entity;
    }
}
