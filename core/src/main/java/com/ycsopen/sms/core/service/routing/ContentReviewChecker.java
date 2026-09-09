package com.ycsopen.sms.core.service.routing;

import com.ycsopen.sms.core.domain.entity.SensitiveWord;
import com.ycsopen.sms.core.repository.SensitiveWordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * F-5.5 内容审核管理 —— 对每条提交内容做实时安全扫描（区别于 F-3 的资质/资源审核，见 PRD 5.3 节说明）。
 * <p><b>覆盖 PRD 检视 Finding #2</b>：本检查器扫描的是 {@link RoutingContext#getContent()}，
 * 调用方必须传入"模板 + 变量拼接后的最终文本"，而不是只扫描模板本身——否则用户可以在验证码
 * 模板的变量位置注入广告/违规文字绕过审核。这一点在 {@code MessageSubmitService} 组装
 * RoutingContext 时必须遵守，测试见 ContentReviewCheckerTest。</p>
 */
@Component
public class ContentReviewChecker {

    private static final Logger log = LoggerFactory.getLogger(ContentReviewChecker.class);

    private final SensitiveWordRepository sensitiveWordRepository;
    private final JdbcTemplate jdbc;

    public ContentReviewChecker(SensitiveWordRepository sensitiveWordRepository, JdbcTemplate jdbc) {
        this.sensitiveWordRepository = sensitiveWordRepository;
        this.jdbc = jdbc;
    }

    @Transactional
    public Result check(RoutingContext ctx) {
        return evaluate(ctx, true);
    }

    @Transactional(readOnly = true)
    public Result preview(RoutingContext ctx) {
        return evaluate(ctx, false);
    }

    private Result evaluate(RoutingContext ctx, boolean recordHits) {
        Objects.requireNonNull(ctx, "routing context is required");
        String content = ctx.getContent();
        if (content == null || content.isBlank()) {
            return Result.pass(content);
        }
        List<SensitiveWord> activeWords = sensitiveWordRepository.findAllByStatus(SensitiveWord.Status.ACTIVE)
                .stream()
                .filter(word -> appliesToContext(word, ctx))
                .sorted(Comparator
                        .comparingInt((SensitiveWord word) -> scopeRank(word, ctx))
                        .thenComparingInt(word -> levelRank(word.getLevel()))
                        .thenComparingInt(word -> actionRank(word.getAction()))
                        .thenComparing(SensitiveWord::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();

        String resultContent = content;
        for (SensitiveWord sw : activeWords) {
            String canonicalWord = canonical(sw.getWord());
            String canonicalContent = canonical(resultContent);
            if (canonicalWord.isBlank() || !canonicalContent.contains(canonicalWord)) continue;

            switch (sw.getAction()) {
                case BLOCK -> {
                    if (recordHits) {
                        recordHit(sw, ctx, canonicalWord, resultContent, true);
                    }
                    return Result.blocked("命中内容审核词库：分类=" + sw.getCategory()
                            + " 级别=" + sw.getLevel() + " 词=" + mask(canonicalWord));
                }
                case REPLACE -> {
                    String replacement = sw.getReplacement() == null || sw.getReplacement().isBlank()
                            ? "***" : sw.getReplacement().trim();
                    resultContent = replaceCanonicalMatches(resultContent, canonicalWord, replacement);
                    if (recordHits) {
                        recordHit(sw, ctx, canonicalWord, resultContent, false);
                    }
                }
                case ALERT -> {
                    if (recordHits) {
                        recordHit(sw, ctx, canonicalWord, resultContent, false);
                    }
                }
            }
        }
        return Result.pass(resultContent);
    }

    public Result check(String content, Long tenantId) {
        return check(RoutingContext.builder().tenantId(tenantId).content(content).build());
    }

    private boolean appliesToContext(SensitiveWord sw, RoutingContext ctx) {
        return switch (sw.getScope()) {
            case GLOBAL -> true;
            case TENANT -> ctx.getTenantId() != null && ctx.getTenantId().equals(sw.getScopeRefId());
            case PRODUCT -> ctx.getTemplateId() != null && ctx.getTemplateId().equals(sw.getScopeRefId());
        };
    }

    private int scopeRank(SensitiveWord sw, RoutingContext ctx) {
        return switch (sw.getScope()) {
            case PRODUCT -> 0;
            case TENANT -> 1;
            case GLOBAL -> 2;
        };
    }

    private int levelRank(SensitiveWord.Level level) {
        return switch (level) {
            case HIGH -> 0;
            case MEDIUM -> 1;
            case LOW -> 2;
        };
    }

    private int actionRank(SensitiveWord.Action action) {
        return switch (action) {
            case BLOCK -> 0;
            case REPLACE -> 1;
            case ALERT -> 2;
        };
    }

    private void recordHit(SensitiveWord word, RoutingContext ctx, String canonicalWord,
                           String resultContent, boolean blocked) {
        word.setHitCount((word.getHitCount() == null ? 0 : word.getHitCount()) + 1);
        sensitiveWordRepository.save(word);
        try {
            jdbc.update("""
                    INSERT INTO content_safety_hits
                    (policy_id, tenant_id, template_id, action, level, matched_word, result_content, blocked, created_at)
                    VALUES (?,?,?,?,?,?,?,?,?)
                    """, word.getId(), ctx.getTenantId(), ctx.getTemplateId(), word.getAction().name(),
                    word.getLevel().name(), canonicalWord, resultContent, blocked, LocalDateTime.now());
        } catch (DataAccessException failure) {
            log.warn("content safety hit evidence write failed; routing decision is preserved");
        }
    }

    private String canonical(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }

    private String replaceCanonicalMatches(String content, String canonicalWord, String replacement) {
        CanonicalView view = canonicalView(content);
        List<Range> ranges = new ArrayList<>();
        int from = 0;
        while (from <= view.value().length() - canonicalWord.length()) {
            int match = view.value().indexOf(canonicalWord, from);
            if (match < 0) {
                break;
            }
            ranges.add(new Range(view.starts()[match], view.ends()[match + canonicalWord.length() - 1]));
            from = match + canonicalWord.length();
        }
        if (ranges.isEmpty()) {
            return content;
        }
        StringBuilder replaced = new StringBuilder(content.length());
        int originalCursor = 0;
        for (Range range : ranges) {
            if (range.start() < originalCursor) {
                continue;
            }
            replaced.append(content, originalCursor, range.start());
            replaced.append(replacement);
            originalCursor = range.end();
        }
        replaced.append(content, originalCursor, content.length());
        return replaced.toString();
    }

    private CanonicalView canonicalView(String content) {
        StringBuilder value = new StringBuilder();
        List<Integer> starts = new ArrayList<>();
        List<Integer> ends = new ArrayList<>();
        for (int offset = 0; offset < content.length(); ) {
            int codePoint = content.codePointAt(offset);
            int next = offset + Character.charCount(codePoint);
            String normalized = canonical(content.substring(offset, next));
            for (int i = 0; i < normalized.length(); i++) {
                value.append(normalized.charAt(i));
                starts.add(offset);
                ends.add(next);
            }
            offset = next;
        }
        return new CanonicalView(value.toString(), starts.stream().mapToInt(Integer::intValue).toArray(),
                ends.stream().mapToInt(Integer::intValue).toArray());
    }

    private String mask(String word) {
        return word.length() <= 1 ? "*" : word.charAt(0) + "*".repeat(word.length() - 1);
    }

    private record CanonicalView(String value, int[] starts, int[] ends) { }

    private record Range(int start, int end) { }

    public record Result(boolean blocked, String reason, String finalContent) {
        static Result pass(String finalContent) { return new Result(false, null, finalContent); }
        static Result blocked(String reason) { return new Result(true, reason, null); }
    }
}
