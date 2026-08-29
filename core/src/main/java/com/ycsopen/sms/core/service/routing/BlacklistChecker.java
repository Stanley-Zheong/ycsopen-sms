package com.ycsopen.sms.core.service.routing;

import com.ycsopen.sms.core.domain.entity.BlacklistEntry;
import com.ycsopen.sms.core.repository.BlacklistEntryRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * F-5.1 黑名单检测 —— 路由前置拦截，硬性要求。
 * <p>检测源：① 系统级黑名单 ② 机构级黑名单 ③ 第三方风险名单服务（{@link ThirdPartyBlacklistClient}）。
 * 白名单优先级高于黑名单（F-5.2）：命中机构级白名单直接放行，跳过后续黑名单判断。</p>
 * <p>三类结果按"任一命中即拦截"合并判定（F-5.1 验收标准）。</p>
 */
@Component
public class BlacklistChecker {

    private final BlacklistEntryRepository blacklistEntryRepository;
    private final ThirdPartyBlacklistClient thirdPartyBlacklistClient;

    public BlacklistChecker(BlacklistEntryRepository blacklistEntryRepository,
                             ThirdPartyBlacklistClient thirdPartyBlacklistClient) {
        this.blacklistEntryRepository = blacklistEntryRepository;
        this.thirdPartyBlacklistClient = thirdPartyBlacklistClient;
    }

    public Result check(RoutingContext ctx) {
        // 白名单优先：机构级白名单命中直接放行 (F-5.2)
        List<BlacklistEntry> tenantWhite = blacklistEntryRepository
                .findByMobileHashAndTenantIdAndStatus(ctx.getMobileHash(), ctx.getTenantId(), BlacklistEntry.Status.ACTIVE)
                .stream().filter(e -> e.getListType() == BlacklistEntry.ListType.WHITE).toList();
        if (!tenantWhite.isEmpty()) {
            return Result.pass();
        }

        // ① 系统级黑名单
        boolean systemHit = blacklistEntryRepository
                .findByMobileHashAndTenantIdIsNullAndStatus(ctx.getMobileHash(), BlacklistEntry.Status.ACTIVE)
                .stream().anyMatch(e -> e.getListType() == BlacklistEntry.ListType.BLACK);
        if (systemHit) {
            return Result.blocked("系统级黑名单命中");
        }

        // ② 机构级黑名单
        boolean tenantHit = blacklistEntryRepository
                .findByMobileHashAndTenantIdAndStatus(ctx.getMobileHash(), ctx.getTenantId(), BlacklistEntry.Status.ACTIVE)
                .stream().anyMatch(e -> e.getListType() == BlacklistEntry.ListType.BLACK);
        if (tenantHit) {
            return Result.blocked("机构级黑名单命中（如历史退订用户）");
        }

        // ③ 第三方风险名单服务（F-5.3：超时/异常按配置降级，不阻塞主链路）
        ThirdPartyBlacklistClient.CheckResult thirdParty = thirdPartyBlacklistClient.check(ctx.getMobileHash());
        if (thirdParty.hit()) {
            return Result.blocked("第三方风险名单命中：" + thirdParty.sourceDescription());
        }

        return Result.pass();
    }

    public record Result(boolean blocked, String reason) {
        static Result pass() { return new Result(false, null); }
        static Result blocked(String reason) { return new Result(true, reason); }
    }
}
