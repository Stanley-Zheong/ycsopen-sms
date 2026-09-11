package com.ycsopen.sms.core.service.tenant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Phase 49: cooperation termination clearance, approval, resource revocation and retained history. */
@Service
public class TenantCooperationTerminationService {
    private static final List<ParticipantDefinition> PARTICIPANTS = List.of(
            new ParticipantDefinition("HTTP_ACCEPTANCE", "HTTP 接收入口", "new submissions rejected by tenant lifecycle"),
            new ParticipantDefinition("API_KEYS", "API Key", "ACTIVE keys are revoked on effect"),
            new ParticipantDefinition("CMPP_SESSIONS", "CMPP 下游会话/凭据", "ACTIVE protocol credentials are revoked on effect"),
            new ParticipantDefinition("CONSOLE_SESSIONS", "控制台会话", "tenant users and sessions are disabled on effect"),
            new ParticipantDefinition("BULK_SCHEDULED_WORK", "批量/定时任务", "pending/running/paused work is cancelled on effect"),
            new ParticipantDefinition("WEBHOOK", "Webhook 回调", "active callback config is disabled on effect"),
            new ParticipantDefinition("UPLINK", "上行记录", "history remains retained/readable"),
            new ParticipantDefinition("UNSUBSCRIBE", "退订记录", "history remains retained/readable"),
            new ParticipantDefinition("SIGNATURE", "签名资源", "approved/pending signatures are deactivated on effect"),
            new ParticipantDefinition("TEMPLATE", "模板资源", "approved/pending templates are deactivated on effect"),
            new ParticipantDefinition("BILLING", "计费账户", "prepaid balance/frozen amount must be cleared"),
            new ParticipantDefinition("SETTLEMENT", "后付费结算", "unsettled statements/settlements must be cleared"),
            new ParticipantDefinition("ARCHIVE_RETENTION", "归档保留", "termination never deletes retained history"),
            new ParticipantDefinition("FAILURE_COMPENSATION", "失败补偿", "effect stores per-resource mutation counts"),
            new ParticipantDefinition("IRREVERSIBILITY", "终止不可逆", "terminated tenant cannot be restored by this service")
    );

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public TenantCooperationTerminationService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.json = Objects.requireNonNull(json);
    }

    @Transactional(readOnly = true)
    public List<TerminationRequestView> list(Long tenantId, String status) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT id, tenant_id, reason, request_evidence, request_status, requested_by,
                       requested_at, approved_by, approved_at, admin_opinion, effective_at,
                       clearance_snapshot_json, participant_snapshot_json, compensation_json
                  FROM tenant_termination_requests
                 WHERE 1=1
                """);
        if (tenantId != null) {
            sql.append(" AND tenant_id=?");
            args.add(tenantId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND request_status=?");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }
        sql.append(" ORDER BY requested_at DESC, id DESC");
        return jdbc.query(sql.toString(), this::view, args.toArray());
    }

    @Transactional(readOnly = true)
    public TerminationDetail detail(long requestId) {
        TerminationRequestView request = request(requestId);
        List<ParticipantView> participants = participants(requestId);
        List<AuditView> audits = jdbc.query("""
                SELECT id, request_id, tenant_id, action, actor, result_status, evidence_json, created_at
                  FROM tenant_termination_audits
                 WHERE request_id=?
                 ORDER BY created_at, id
                """, (rs, row) -> new AuditView(rs.getLong("id"), rs.getLong("request_id"), rs.getLong("tenant_id"),
                rs.getString("action"), rs.getString("actor"), rs.getString("result_status"),
                rs.getString("evidence_json"), toLocal(rs.getTimestamp("created_at"))), requestId);
        return new TerminationDetail(request, participants, audits);
    }

    @Transactional
    public TerminationDetail requestTermination(TerminationCommand command, String actor) {
        TerminationCommand checked = command.checked();
        String checkedActor = actor(actor);
        TenantState tenant = tenantForUpdate(checked.tenantId());
        if (!List.of("SIGNED", "FROZEN").contains(tenant.lifecycleStatus())) {
            throw new BusinessException("TENANT_TERMINATION_STATE_INVALID", "只有已签约或冻结机构可以发起终止");
        }
        ensureNoOpenRequest(checked.tenantId());

        ClearanceView clearance = calculateClearance(tenant);
        List<ParticipantView> participants = calculateParticipants(checked.tenantId(), clearance, false, null);
        String status = clearance.clearancePassed() ? "PENDING_ADMIN_APPROVAL" : "BLOCKED_CLEARANCE";
        long requestId = insertRequest(checked, status, checkedActor, clearance, participants);
        replaceParticipants(requestId, checked.tenantId(), participants);
        audit(requestId, checked.tenantId(), "REQUEST", checkedActor, status, Map.of(
                "reason", checked.reason(),
                "financeClearance", clearance.clearancePassed(),
                "participantCount", participants.size()
        ));
        return detail(requestId);
    }

    @Transactional
    public TerminationDetail refreshClearance(long requestId, String actor) {
        TerminationRequestView request = request(requestId);
        if (List.of("EFFECTIVE", "REJECTED").contains(request.requestStatus())) {
            return detail(requestId);
        }
        TenantState tenant = tenantForUpdate(request.tenantId());
        ClearanceView clearance = calculateClearance(tenant);
        List<ParticipantView> participants = calculateParticipants(request.tenantId(), clearance, false, null);
        String nextStatus = clearance.clearancePassed() ? "PENDING_ADMIN_APPROVAL" : "BLOCKED_CLEARANCE";
        jdbc.update("""
                UPDATE tenant_termination_requests
                   SET request_status=?, clearance_snapshot_json=?, participant_snapshot_json=?
                 WHERE id=?
                """, nextStatus, json(clearance), json(participants), requestId);
        replaceParticipants(requestId, request.tenantId(), participants);
        audit(requestId, request.tenantId(), "REFRESH_CLEARANCE", actor(actor), nextStatus,
                Map.of("financeClearance", clearance.clearancePassed(), "participantCount", participants.size()));
        return detail(requestId);
    }

    @Transactional
    public TerminationDetail approve(long requestId, ApprovalCommand command, String actor) {
        ApprovalCommand checked = command == null ? new ApprovalCommand("") : command;
        TerminationRequestView request = request(requestId);
        if (!"PENDING_ADMIN_APPROVAL".equals(request.requestStatus())) {
            throw new BusinessException("TENANT_TERMINATION_NOT_READY", "终止未通过清算检查，不可审批");
        }
        TenantState tenant = tenantForUpdate(request.tenantId());
        if ("TERMINATED".equals(tenant.lifecycleStatus())) {
            throw new BusinessException("TENANT_TERMINATION_IRREVERSIBLE", "机构已终止，不可重复审批");
        }
        String checkedActor = actor(actor);
        jdbc.update("""
                UPDATE tenant_termination_requests
                   SET request_status='APPROVED', approved_by=?, approved_at=?, admin_opinion=?
                 WHERE id=?
                """, checkedActor, Timestamp.valueOf(LocalDateTime.now()), trim(checked.opinion(), 500), requestId);
        audit(requestId, request.tenantId(), "APPROVE", checkedActor, "APPROVED",
                Map.of("opinion", trim(checked.opinion(), 500)));
        return detail(requestId);
    }

    @Transactional
    public TerminationDetail effect(long requestId, String actor) {
        TerminationRequestView request = request(requestId);
        if (!"APPROVED".equals(request.requestStatus())) {
            throw new BusinessException("TENANT_TERMINATION_NOT_APPROVED", "终止必须先审批通过");
        }
        TenantState tenant = tenantForUpdate(request.tenantId());
        if ("TERMINATED".equals(tenant.lifecycleStatus())) {
            throw new BusinessException("TENANT_TERMINATION_IRREVERSIBLE", "机构终止后不可恢复或重复生效");
        }
        String checkedActor = actor(actor);
        LocalDateTime now = LocalDateTime.now();
        ClearanceView clearance = calculateClearance(tenant);
        if (!clearance.clearancePassed()) {
            throw new BusinessException("TENANT_TERMINATION_CLEARANCE_STALE", "终止清算状态已失效，不可生效");
        }
        MutationSummary mutation = revokeResources(request.tenantId());
        List<ParticipantView> participants = calculateParticipants(request.tenantId(), clearance, true, mutation);
        jdbc.update("""
                UPDATE tenants
                   SET lifecycle_status='TERMINATED',
                       termination_requested_by=COALESCE(termination_requested_by, ?),
                       termination_reason=?,
                       termination_approved_by=COALESCE(termination_approved_by, ?),
                       termination_approved_at=COALESCE(termination_approved_at, ?),
                       termination_effective_date=?,
                       termination_settlement_status='SETTLED'
                 WHERE id=? AND lifecycle_status <> 'TERMINATED'
                """, request.requestedBy(), request.reason(), request.approvedBy(), Timestamp.valueOf(now),
                java.sql.Date.valueOf(LocalDate.from(now)), request.tenantId());
        jdbc.update("""
                UPDATE tenant_termination_requests
                   SET request_status='EFFECTIVE', effective_at=?, clearance_snapshot_json=?,
                       participant_snapshot_json=?, compensation_json=?
                 WHERE id=?
                """, Timestamp.valueOf(now), json(clearance), json(participants), json(mutation), requestId);
        replaceParticipants(requestId, request.tenantId(), participants);
        audit(requestId, request.tenantId(), "EFFECT", checkedActor, "EFFECTIVE",
                Map.of("mutation", mutation, "participantCount", participants.size()));
        return detail(requestId);
    }

    public void requireTenantNotTerminated(long tenantId, String surface) {
        List<String> states = jdbc.query("SELECT lifecycle_status FROM tenants WHERE id=?",
                (rs, row) -> rs.getString(1), tenantId);
        if (!states.isEmpty() && "TERMINATED".equals(states.getFirst())) {
            throw new BusinessException("TENANT_TERMINATED", surface + " 已拒绝终止机构");
        }
    }

    public List<ParticipantDefinition> participantInventory() {
        return PARTICIPANTS;
    }

    private MutationSummary revokeResources(long tenantId) {
        int users = jdbc.update("UPDATE users SET status='DISABLED' WHERE tenant_id=? AND status <> 'DISABLED'", tenantId);
        int sessions = jdbc.update("""
                UPDATE user_sessions SET expires_at=CURRENT_TIMESTAMP
                 WHERE tenant_id=? AND expires_at > CURRENT_TIMESTAMP
                """, tenantId);
        int apiKeys = jdbc.update("""
                UPDATE tenant_api_keys
                   SET status='DISABLED', revoked_at=COALESCE(revoked_at, CURRENT_TIMESTAMP)
                 WHERE tenant_id=? AND status='ACTIVE'
                """, tenantId);
        int credentials = jdbc.update("""
                UPDATE tenant_protocol_credentials
                   SET status='DISABLED', revoked_at=COALESCE(revoked_at, CURRENT_TIMESTAMP)
                 WHERE tenant_id=? AND status='ACTIVE'
                """, tenantId);
        int callbacks = jdbc.update("""
                UPDATE tenant_callback_configs
                   SET config_status='DISABLED', paused_at=COALESCE(paused_at, CURRENT_TIMESTAMP)
                 WHERE tenant_id=? AND config_status='ACTIVE'
                """, tenantId);
        int bulkItems = jdbc.update("""
                UPDATE bulk_sending_items
                   SET send_status='CANCELLED'
                 WHERE send_status='PENDING'
                   AND bulk_id IN (SELECT id FROM bulk_sendings WHERE tenant_id=?)
                """, tenantId);
        int bulkTasks = jdbc.update("""
                UPDATE bulk_sendings
                   SET task_status='CANCELLED', control_reason='合作终止生效', end_time=CURRENT_TIMESTAMP
                 WHERE tenant_id=? AND task_status IN ('PENDING','RUNNING','PAUSED')
                """, tenantId);
        int signatures = jdbc.update("""
                UPDATE signatures
                   SET audit_status='REJECTED', audit_comment='合作终止生效，签名不可再用'
                 WHERE tenant_id=? AND audit_status IN ('PENDING','APPROVED','SUPPLEMENT_REQUIRED')
                """, tenantId);
        int templates = jdbc.update("""
                UPDATE templates
                   SET audit_status='REJECTED', audit_comment='合作终止生效，模板不可再用'
                 WHERE tenant_id=? AND audit_status IN ('PENDING','APPROVED','AMENDMENT_REQUIRED')
                """, tenantId);
        return new MutationSummary(users, sessions, apiKeys, credentials, callbacks, bulkTasks, bulkItems, signatures, templates);
    }

    private ClearanceView calculateClearance(TenantState tenant) {
        List<ClearanceItem> items = new ArrayList<>();
        long prepaidBalance = longValue("SELECT COALESCE(SUM(balance_mil + frozen_mil),0) FROM prepaid_accounts WHERE tenant_id=?", tenant.id());
        long accountBalance = longValue("SELECT COALESCE(SUM(balance + frozen_amount),0) FROM tenant_accounts WHERE tenant_id=?", tenant.id());
        long prepaidOutstanding = prepaidBalance + accountBalance;
        if ("PREPAID".equals(tenant.billingMode()) && prepaidOutstanding > 0) {
            items.add(new ClearanceItem("PREPAID_REFUND", false, prepaidOutstanding,
                    "预付费余额或冻结金额未退款/解冻"));
        } else {
            items.add(new ClearanceItem("PREPAID_REFUND", true, prepaidOutstanding,
                    "预付费余额已清零或非预付费机构"));
        }

        long unsettledStatements = longValue("""
                SELECT COUNT(*) FROM statements
                 WHERE tenant_id=? AND settlement_status NOT IN ('SETTLED','PAID')
                """, tenant.id());
        long unsettledRecords = longValue("""
                SELECT COUNT(*) FROM settlement_records
                 WHERE tenant_id=? AND status NOT IN ('SETTLED','PAID','RECEIVED')
                """, tenant.id());
        long postpaidOutstanding = unsettledStatements + unsettledRecords;
        if ("POSTPAID".equals(tenant.billingMode()) && postpaidOutstanding > 0) {
            items.add(new ClearanceItem("POSTPAID_SETTLEMENT", false, postpaidOutstanding,
                    "后付费账单或结算记录未完成"));
        } else {
            items.add(new ClearanceItem("POSTPAID_SETTLEMENT", true, postpaidOutstanding,
                    "后付费结算已完成或非后付费机构"));
        }
        boolean passed = items.stream().allMatch(ClearanceItem::passed);
        return new ClearanceView(tenant.id(), tenant.lifecycleStatus(), tenant.billingMode(), passed, items);
    }

    private List<ParticipantView> calculateParticipants(long tenantId, ClearanceView clearance,
                                                        boolean effective, MutationSummary mutation) {
        Map<String, Long> active = new LinkedHashMap<>();
        active.put("HTTP_ACCEPTANCE", effective || "TERMINATED".equals(lifecycle(tenantId)) ? 0L : 1L);
        active.put("API_KEYS", longValue("SELECT COUNT(*) FROM tenant_api_keys WHERE tenant_id=? AND status='ACTIVE'", tenantId));
        active.put("CMPP_SESSIONS", longValue("SELECT COUNT(*) FROM tenant_protocol_credentials WHERE tenant_id=? AND status='ACTIVE'", tenantId));
        active.put("CONSOLE_SESSIONS", longValue("""
                SELECT COUNT(*) FROM users WHERE tenant_id=? AND status <> 'DISABLED'
                """, tenantId) + longValue("""
                SELECT COUNT(*) FROM user_sessions WHERE tenant_id=? AND expires_at > CURRENT_TIMESTAMP
                """, tenantId));
        active.put("BULK_SCHEDULED_WORK", longValue("""
                SELECT COUNT(*) FROM bulk_sendings WHERE tenant_id=? AND task_status IN ('PENDING','RUNNING','PAUSED')
                """, tenantId));
        active.put("WEBHOOK", longValue("SELECT COUNT(*) FROM tenant_callback_configs WHERE tenant_id=? AND config_status='ACTIVE'", tenantId));
        active.put("UPLINK", longValue("SELECT COUNT(*) FROM uplink_records WHERE tenant_id=?", tenantId));
        active.put("UNSUBSCRIBE", longValue("SELECT COUNT(*) FROM unsubscribe_records WHERE tenant_id=?", tenantId));
        active.put("SIGNATURE", longValue("SELECT COUNT(*) FROM signatures WHERE tenant_id=? AND audit_status IN ('PENDING','APPROVED','SUPPLEMENT_REQUIRED')", tenantId));
        active.put("TEMPLATE", longValue("SELECT COUNT(*) FROM templates WHERE tenant_id=? AND audit_status IN ('PENDING','APPROVED','AMENDMENT_REQUIRED')", tenantId));
        active.put("BILLING", clearance.items().stream().filter(i -> "PREPAID_REFUND".equals(i.code()) && !i.passed()).count());
        active.put("SETTLEMENT", clearance.items().stream().filter(i -> "POSTPAID_SETTLEMENT".equals(i.code()) && !i.passed()).count());
        active.put("ARCHIVE_RETENTION", longValue("SELECT COUNT(*) FROM archive_manifests WHERE tenant_id=?", tenantId));
        active.put("FAILURE_COMPENSATION", mutation == null ? 0L : mutation.totalMutations());
        active.put("IRREVERSIBILITY", effective || "TERMINATED".equals(lifecycle(tenantId)) ? 1L : 0L);

        List<ParticipantView> result = new ArrayList<>();
        for (ParticipantDefinition definition : PARTICIPANTS) {
            long count = active.getOrDefault(definition.code(), 0L);
            String state = participantState(definition.code(), count, effective, clearance.clearancePassed());
            result.add(new ParticipantView(0, 0, tenantId, definition.code(), definition.name(), state, count,
                    json(Map.of("activeOrRetainedCount", count, "rule", definition.rule()))));
        }
        return result;
    }

    private String participantState(String code, long count, boolean effective, boolean clearancePassed) {
        if (List.of("UPLINK", "UNSUBSCRIBE", "ARCHIVE_RETENTION", "IRREVERSIBILITY").contains(code)) {
            return count > 0 ? "RETAINED" : "READY";
        }
        if ("BILLING".equals(code) || "SETTLEMENT".equals(code)) {
            return clearancePassed ? "CLEARED" : "BLOCKED";
        }
        if ("FAILURE_COMPENSATION".equals(code)) {
            return effective ? "RECORDED" : "READY";
        }
        if (effective) {
            return count == 0 ? "REVOKED" : "BLOCKED";
        }
        return count == 0 ? "READY" : "READY_TO_REVOKE";
    }

    private long insertRequest(TerminationCommand command, String status, String actor,
                               ClearanceView clearance, List<ParticipantView> participants) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO tenant_termination_requests(tenant_id, reason, request_evidence,
                        request_status, requested_by, clearance_snapshot_json, participant_snapshot_json)
                    VALUES (?,?,?,?,?,?,?)
                    """, new String[]{"id"});
            statement.setLong(1, command.tenantId());
            statement.setString(2, command.reason());
            statement.setString(3, command.requestEvidence());
            statement.setString(4, status);
            statement.setString(5, actor);
            statement.setString(6, json(clearance));
            statement.setString(7, json(participants));
            return statement;
        }, key);
        Number id = key.getKey();
        if (id == null) {
            throw new BusinessException("TENANT_TERMINATION_CREATE_FAILED", "终止请求创建失败");
        }
        return id.longValue();
    }

    private void replaceParticipants(long requestId, long tenantId, List<ParticipantView> participants) {
        jdbc.update("DELETE FROM tenant_termination_participants WHERE request_id=?", requestId);
        for (ParticipantView participant : participants) {
            jdbc.update("""
                    INSERT INTO tenant_termination_participants(request_id, tenant_id, participant_code,
                        participant_name, participant_state, blocker_count, evidence_json)
                    VALUES (?,?,?,?,?,?,?)
                    """, requestId, tenantId, participant.participantCode(), participant.participantName(),
                    participant.participantState(), participant.blockerCount(), participant.evidenceJson());
        }
    }

    private void ensureNoOpenRequest(long tenantId) {
        int open = jdbc.queryForObject("""
                SELECT COUNT(*) FROM tenant_termination_requests
                 WHERE tenant_id=? AND request_status IN ('BLOCKED_CLEARANCE','PENDING_ADMIN_APPROVAL','APPROVED')
                """, Integer.class, tenantId);
        if (open > 0) {
            throw new BusinessException("TENANT_TERMINATION_OPEN_REQUEST_EXISTS", "机构已有未结束终止请求");
        }
    }

    private TenantState tenantForUpdate(long tenantId) {
        List<TenantState> tenants = jdbc.query("""
                SELECT id, lifecycle_status, billing_mode FROM tenants WHERE id=? FOR UPDATE
                """, (rs, row) -> new TenantState(rs.getLong("id"), rs.getString("lifecycle_status"),
                rs.getString("billing_mode")), tenantId);
        if (tenants.isEmpty()) {
            throw new BusinessException("TENANT_NOT_FOUND", "机构不存在");
        }
        return tenants.getFirst();
    }

    private TerminationRequestView request(long requestId) {
        List<TerminationRequestView> rows = jdbc.query("""
                SELECT id, tenant_id, reason, request_evidence, request_status, requested_by,
                       requested_at, approved_by, approved_at, admin_opinion, effective_at,
                       clearance_snapshot_json, participant_snapshot_json, compensation_json
                  FROM tenant_termination_requests
                 WHERE id=?
                """, this::view, requestId);
        if (rows.isEmpty()) {
            throw new BusinessException("TENANT_TERMINATION_NOT_FOUND", "终止请求不存在");
        }
        return rows.getFirst();
    }

    private List<ParticipantView> participants(long requestId) {
        return jdbc.query("""
                SELECT id, request_id, tenant_id, participant_code, participant_name, participant_state,
                       blocker_count, evidence_json
                  FROM tenant_termination_participants
                 WHERE request_id=?
                 ORDER BY id
                """, (rs, row) -> new ParticipantView(rs.getLong("id"), rs.getLong("request_id"),
                rs.getLong("tenant_id"), rs.getString("participant_code"), rs.getString("participant_name"),
                rs.getString("participant_state"), rs.getLong("blocker_count"), rs.getString("evidence_json")),
                requestId);
    }

    private TerminationRequestView view(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new TerminationRequestView(rs.getLong("id"), rs.getLong("tenant_id"), rs.getString("reason"),
                rs.getString("request_evidence"), rs.getString("request_status"), rs.getString("requested_by"),
                toLocal(rs.getTimestamp("requested_at")), rs.getString("approved_by"), toLocal(rs.getTimestamp("approved_at")),
                rs.getString("admin_opinion"), toLocal(rs.getTimestamp("effective_at")),
                rs.getString("clearance_snapshot_json"), rs.getString("participant_snapshot_json"),
                rs.getString("compensation_json"));
    }

    private void audit(long requestId, long tenantId, String action, String actor, String result, Object evidence) {
        jdbc.update("""
                INSERT INTO tenant_termination_audits(request_id, tenant_id, action, actor, result_status, evidence_json)
                VALUES (?,?,?,?,?,?)
                """, requestId, tenantId, action, actor, result, json(evidence));
    }

    private long longValue(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private String lifecycle(long tenantId) {
        List<String> rows = jdbc.query("SELECT lifecycle_status FROM tenants WHERE id=?",
                (rs, row) -> rs.getString(1), tenantId);
        return rows.isEmpty() ? "" : rows.getFirst();
    }

    private String json(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException("TENANT_TERMINATION_JSON_FAILED", "终止证据序列化失败");
        }
    }

    private static LocalDateTime toLocal(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static String actor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不能为空");
        }
        return trim(actor, 64);
    }

    private static String trim(String value, int max) {
        String checked = value == null ? "" : value.trim();
        return checked.length() <= max ? checked : checked.substring(0, max);
    }

    private record TenantState(long id, String lifecycleStatus, String billingMode) { }

    public record TerminationCommand(long tenantId, String reason, String requestEvidence) {
        TerminationCommand checked() {
            String checkedReason = reason == null ? "" : reason.trim().toUpperCase(Locale.ROOT);
            if (!List.of("VOLUNTARY", "SEVERE_VIOLATION", "LONG_TERM_ARREARS", "EXPIRED_CREDENTIALS")
                    .contains(checkedReason)) {
                throw new BusinessException("TENANT_TERMINATION_REASON_INVALID", "终止原因不合法");
            }
            String checkedEvidence = trim(requestEvidence, 1000);
            if (checkedEvidence.isBlank()) {
                throw new BusinessException("TENANT_TERMINATION_EVIDENCE_REQUIRED", "终止依据不能为空");
            }
            return new TerminationCommand(tenantId, checkedReason, checkedEvidence);
        }
    }

    public record ApprovalCommand(String opinion) { }

    public record TerminationRequestView(long id, long tenantId, String reason, String requestEvidence,
                                         String requestStatus, String requestedBy, LocalDateTime requestedAt,
                                         String approvedBy, LocalDateTime approvedAt, String adminOpinion,
                                         LocalDateTime effectiveAt, String clearanceSnapshotJson,
                                         String participantSnapshotJson, String compensationJson) { }

    public record TerminationDetail(TerminationRequestView request, List<ParticipantView> participants,
                                    List<AuditView> audits) { }

    public record ClearanceView(long tenantId, String lifecycleStatus, String billingMode,
                                boolean clearancePassed, List<ClearanceItem> items) { }

    public record ClearanceItem(String code, boolean passed, long amountOrCount, String evidence) { }

    public record ParticipantDefinition(String code, String name, String rule) { }

    public record ParticipantView(long id, long requestId, long tenantId, String participantCode,
                                  String participantName, String participantState, long blockerCount,
                                  String evidenceJson) { }

    public record AuditView(long id, long requestId, long tenantId, String action, String actor,
                            String resultStatus, String evidenceJson, LocalDateTime createdAt) { }

    public record MutationSummary(int usersDisabled, int sessionsExpired, int apiKeysRevoked,
                                  int cmppCredentialsRevoked, int callbacksDisabled, int bulkTasksCancelled,
                                  int bulkItemsCancelled, int signaturesDeactivated, int templatesDeactivated) {
        long totalMutations() {
            return (long) usersDisabled + sessionsExpired + apiKeysRevoked + cmppCredentialsRevoked
                    + callbacksDisabled + bulkTasksCancelled + bulkItemsCancelled
                    + signaturesDeactivated + templatesDeactivated;
        }
    }
}
