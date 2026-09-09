package com.ycsopen.sms.core.service.routing;

/** Records auditable pre-task risk decisions made by the routing chain. */
public interface RiskDecisionRecorder {
    void recordBlacklistDecision(RoutingContext context, BlacklistChecker.Result result);
}
