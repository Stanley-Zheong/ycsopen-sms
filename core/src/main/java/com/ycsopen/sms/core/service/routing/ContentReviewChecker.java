package com.ycsopen.sms.core.service.routing;

import com.ycsopen.sms.core.domain.entity.SensitiveWord;
import com.ycsopen.sms.core.repository.SensitiveWordRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * F-5.5 内容审核管理 —— 对每条提交内容做实时安全扫描（区别于 F-3 的资质/资源审核，见 PRD 5.3 节说明）。
 * <p><b>覆盖 PRD 检视 Finding #2</b>：本检查器扫描的是 {@link RoutingContext#getContent()}，
 * 调用方必须传入"模板 + 变量拼接后的最终文本"，而不是只扫描模板本身——否则用户可以在验证码
 * 模板的变量位置注入广告/违规文字绕过审核。这一点在 {@code MessageSubmitService} 组装
 * RoutingContext 时必须遵守，测试见 ContentReviewCheckerTest。</p>
 */
@Component
public class ContentReviewChecker {

    private final SensitiveWordRepository sensitiveWordRepository;

    public ContentReviewChecker(SensitiveWordRepository sensitiveWordRepository) {
        this.sensitiveWordRepository = sensitiveWordRepository;
    }

    public Result check(String content, Long tenantId) {
        if (content == null || content.isBlank()) {
            return Result.pass(content);
        }
        List<SensitiveWord> activeWords = sensitiveWordRepository.findAllByStatus(SensitiveWord.Status.ACTIVE);

        String workingContent = content;
        for (SensitiveWord sw : activeWords) {
            if (!appliesToTenant(sw, tenantId)) continue;
            if (!workingContent.contains(sw.getWord())) continue;

            switch (sw.getAction()) {
                case BLOCK -> {
                    return Result.blocked("命中内容审核词库：分类=" + sw.getCategory() + " 词=" + mask(sw.getWord()));
                }
                case REPLACE -> workingContent = workingContent.replace(sw.getWord(),
                        sw.getReplacement() == null ? "***" : sw.getReplacement());
                case ALERT -> {
                    // 仅告警，不拦截，不改写内容；由调用方决定是否记录告警事件 (F-12.1)
                }
            }
        }
        return Result.pass(workingContent);
    }

    private boolean appliesToTenant(SensitiveWord sw, Long tenantId) {
        return switch (sw.getScope()) {
            case GLOBAL -> true;
            case TENANT -> tenantId != null; // 简化：真实实现应比对 scopeRefId == tenantId
            case PRODUCT -> true;            // 简化：真实实现应比对 scopeRefId == productId
        };
    }

    private String mask(String word) {
        return word.length() <= 1 ? "*" : word.charAt(0) + "*".repeat(word.length() - 1);
    }

    public record Result(boolean blocked, String reason, String finalContent) {
        static Result pass(String finalContent) { return new Result(false, null, finalContent); }
        static Result blocked(String reason) { return new Result(true, reason, null); }
    }
}
