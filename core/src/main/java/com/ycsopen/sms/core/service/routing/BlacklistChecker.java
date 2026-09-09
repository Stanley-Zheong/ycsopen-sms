package com.ycsopen.sms.core.service.routing;

import com.ycsopen.sms.core.domain.entity.BlacklistEntry;
import com.ycsopen.sms.core.common.security.persistence.BlindIndexLookupService;
import org.springframework.stereotype.Component;

/**
 * F-5.1 黑名单检测 —— 路由前置拦截，硬性要求。
 * <p>检测源：① 系统级黑名单 ② 机构级黑名单 ③ 第三方风险名单服务（{@link ThirdPartyBlacklistClient}）。
 * 白名单优先级高于黑名单（F-5.2）：命中机构级白名单直接放行，跳过后续黑名单判断。</p>
 * <p>三类结果按"任一命中即拦截"合并判定（F-5.1 验收标准）。</p>
 */
@Component
public class BlacklistChecker {

    private final BlindIndexLookupService blindIndexLookupService;
    private final ThirdPartyBlacklistClient thirdPartyBlacklistClient;

    public BlacklistChecker(BlindIndexLookupService blindIndexLookupService,
                             ThirdPartyBlacklistClient thirdPartyBlacklistClient) {
        this.blindIndexLookupService = blindIndexLookupService;
        this.thirdPartyBlacklistClient = thirdPartyBlacklistClient;
    }

    public Result check(RoutingContext ctx) {
        BlindIndexLookupService.BlacklistLookupResult lookup = blindIndexLookupService.lookupBlacklist(
                ctx.getTenantId(), ctx.getLegacyMobileLookupToken(), BlacklistEntry.Status.ACTIVE);
        if (lookup.tenantWhitelist()) {
            return Result.pass();
        }
        if (lookup.blockReason()
                == BlindIndexLookupService.BlacklistLookupResult.BlockReason.SYSTEM_BLACKLIST) {
            return Result.blocked("SYSTEM_BLACKLIST", "系统级黑名单命中");
        }
        if (lookup.blockReason()
                == BlindIndexLookupService.BlacklistLookupResult.BlockReason.TENANT_BLACKLIST) {
            return Result.blocked("TENANT_BLACKLIST", "机构级黑名单命中（如历史退订用户）");
        }

        // ③ 第三方风险名单服务（F-5.3：超时/异常按配置降级，不阻塞主链路）
        ThirdPartyBlacklistClient.CheckResult thirdParty = thirdPartyBlacklistClient.check(
                ctx.getOpaqueMobileQueryValue());
        if (thirdParty.recordable()) {
            return Result.thirdParty(thirdParty);
        }

        return Result.pass();
    }

    public record Result(boolean blocked, String reason, String sourceCategory,
                         String riskResult, boolean recordable) {
        public Result(boolean blocked, String reason) {
            this(blocked, reason, blocked ? "TENANT_BLACKLIST" : "NO_MATCH",
                    blocked ? "BLOCK" : "ALLOW", blocked);
        }

        public Result(boolean blocked, String reason, String sourceCategory, String riskResult) {
            this(blocked, reason, sourceCategory, riskResult, blocked || !"NO_MATCH".equals(sourceCategory));
        }

        static Result pass() { return new Result(false, null, "NO_MATCH", "ALLOW", false); }
        static Result blocked(String sourceCategory, String reason) {
            return new Result(true, reason, sourceCategory, "BLOCK", true);
        }

        static Result thirdParty(ThirdPartyBlacklistClient.CheckResult thirdParty) {
            return new Result(thirdParty.hit(), thirdParty.sourceDescription(),
                    thirdParty.sourceCategory(), thirdParty.riskResult(), true);
        }
    }
}
