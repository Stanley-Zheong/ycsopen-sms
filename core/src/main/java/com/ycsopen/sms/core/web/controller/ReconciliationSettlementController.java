package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.service.billing.ReconciliationSettlementService;
import com.ycsopen.sms.core.service.billing.ReconciliationSettlementService.ConfirmationCommand;
import com.ycsopen.sms.core.service.billing.ReconciliationSettlementService.DifferenceRow;
import com.ycsopen.sms.core.service.billing.ReconciliationSettlementService.EvidenceCommand;
import com.ycsopen.sms.core.service.billing.ReconciliationSettlementService.InvoiceIssueCommand;
import com.ycsopen.sms.core.service.billing.ReconciliationSettlementService.InvoiceRequestCommand;
import com.ycsopen.sms.core.service.billing.ReconciliationSettlementService.InvoiceRow;
import com.ycsopen.sms.core.service.billing.ReconciliationSettlementService.ResolveCommand;
import com.ycsopen.sms.core.service.billing.ReconciliationSettlementService.SettlementRow;
import com.ycsopen.sms.core.service.billing.ReconciliationSettlementService.StatementGenerateCommand;
import com.ycsopen.sms.core.service.billing.ReconciliationSettlementService.StatementRow;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/console/reconciliation")
public class ReconciliationSettlementController {
    private final ReconciliationSettlementService service;
    private final UserRepository users;

    public ReconciliationSettlementController(ReconciliationSettlementService service, UserRepository users) {
        this.service = service;
        this.users = users;
    }

    @PostMapping("/statements/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('trial-prepaid:write')")
    public ApiResponse<StatementRow> generateStatement(@PathVariable long tenantId,
                                                       @RequestBody StatementGenerateCommand command,
                                                       Authentication authentication) {
        requireFinance(authentication);
        return ApiResponse.ok(service.generateStatement(tenantId, command, actor(authentication)));
    }

    @GetMapping("/statements")
    @PreAuthorize("hasAuthority('trial-prepaid:read')")
    public ApiResponse<List<StatementRow>> adminStatements(Authentication authentication) {
        requireFinance(authentication);
        return ApiResponse.ok(service.adminStatements());
    }

    @GetMapping("/statements/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('trial-prepaid:read')")
    public ApiResponse<List<StatementRow>> tenantStatements(@PathVariable long tenantId, Authentication authentication) {
        return ApiResponse.ok(service.tenantStatements(scopedTenantId(authentication, tenantId)));
    }

    @GetMapping("/statements/{statementId}/differences")
    @PreAuthorize("hasAuthority('trial-prepaid:read')")
    public ApiResponse<List<DifferenceRow>> differences(@PathVariable long statementId, Authentication authentication) {
        requireStatementTenantAccess(authentication, statementId);
        return ApiResponse.ok(service.differences(statementId));
    }

    @PostMapping("/statements/{statementId}/tenant-confirm")
    @PreAuthorize("hasAuthority('trial-prepaid:write')")
    public ApiResponse<StatementRow> tenantConfirm(@PathVariable long statementId,
                                                   @RequestBody ConfirmationCommand command,
                                                   Authentication authentication) {
        requireStatementTenantAccess(authentication, statementId);
        return ApiResponse.ok(service.tenantConfirm(statementId, command, actor(authentication)));
    }

    @PostMapping("/statements/{statementId}/finance-confirm")
    @PreAuthorize("hasAuthority('trial-prepaid:write')")
    public ApiResponse<StatementRow> financeConfirm(@PathVariable long statementId,
                                                    @RequestBody ConfirmationCommand command,
                                                    Authentication authentication) {
        requireFinance(authentication);
        return ApiResponse.ok(service.financeConfirm(statementId, command, actor(authentication)));
    }

    @PostMapping("/differences/{differenceId}/resolve")
    @PreAuthorize("hasAuthority('trial-prepaid:write')")
    public ApiResponse<StatementRow> resolveDifference(@PathVariable long differenceId,
                                                       @RequestBody ResolveCommand command,
                                                       Authentication authentication) {
        requireFinance(authentication);
        return ApiResponse.ok(service.resolveDifference(differenceId, command, actor(authentication)));
    }

    @PostMapping("/statements/{statementId}/settlements")
    @PreAuthorize("hasAuthority('trial-prepaid:write')")
    public ApiResponse<SettlementRow> startSettlement(@PathVariable long statementId,
                                                      @RequestBody EvidenceCommand command,
                                                      Authentication authentication) {
        requireFinance(authentication);
        return ApiResponse.ok(service.startSettlement(statementId, command, actor(authentication)));
    }

    @GetMapping("/settlements")
    @PreAuthorize("hasAuthority('trial-prepaid:read')")
    public ApiResponse<List<SettlementRow>> settlements(Authentication authentication) {
        requireFinance(authentication);
        return ApiResponse.ok(service.settlements());
    }

    @PostMapping("/settlements/{settlementId}/complete")
    @PreAuthorize("hasAuthority('trial-prepaid:write')")
    public ApiResponse<SettlementRow> completeSettlement(@PathVariable long settlementId,
                                                        @RequestBody EvidenceCommand command,
                                                        Authentication authentication) {
        requireFinance(authentication);
        return ApiResponse.ok(service.completeSettlement(settlementId, command, actor(authentication)));
    }

    @PostMapping("/settlements/{settlementId}/received")
    @PreAuthorize("hasAuthority('trial-prepaid:write')")
    public ApiResponse<SettlementRow> markReceived(@PathVariable long settlementId,
                                                   @RequestBody EvidenceCommand command,
                                                   Authentication authentication) {
        requireFinance(authentication);
        return ApiResponse.ok(service.markReceived(settlementId, command, actor(authentication)));
    }

    @PostMapping("/invoices/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('trial-prepaid:write')")
    public ApiResponse<InvoiceRow> requestInvoice(@PathVariable long tenantId,
                                                  @RequestBody InvoiceRequestCommand command,
                                                  Authentication authentication) {
        return ApiResponse.ok(service.requestInvoice(scopedTenantId(authentication, tenantId), command, actor(authentication)));
    }

    @GetMapping("/invoices/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('trial-prepaid:read')")
    public ApiResponse<List<InvoiceRow>> tenantInvoices(@PathVariable long tenantId, Authentication authentication) {
        return ApiResponse.ok(service.tenantInvoices(scopedTenantId(authentication, tenantId)));
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasAuthority('trial-prepaid:read')")
    public ApiResponse<List<InvoiceRow>> adminInvoices(Authentication authentication) {
        requireFinance(authentication);
        return ApiResponse.ok(service.adminInvoices());
    }

    @PostMapping("/invoices/{invoiceId}/issue")
    @PreAuthorize("hasAuthority('trial-prepaid:write')")
    public ApiResponse<InvoiceRow> issueInvoice(@PathVariable long invoiceId,
                                                @RequestBody InvoiceIssueCommand command,
                                                Authentication authentication) {
        requireFinance(authentication);
        return ApiResponse.ok(service.issueInvoice(invoiceId, command, actor(authentication)));
    }

    private long scopedTenantId(Authentication authentication, long requestedTenantId) {
        if (isPlatform(authentication)) return requestedTenantId;
        Long ownTenantId = ownTenantId(authentication);
        if (ownTenantId == null || ownTenantId != requestedTenantId) {
            throw new BusinessException("TENANT_SCOPE_FORBIDDEN", "不能访问其他机构账单");
        }
        return requestedTenantId;
    }

    private StatementRow requireStatementTenantAccess(Authentication authentication, long statementId) {
        StatementRow statement = service.getStatement(statementId);
        if (isPlatform(authentication)) {
            return statement;
        }
        Long ownTenantId = ownTenantId(authentication);
        if (ownTenantId == null || !ownTenantId.equals(statement.tenantId())) {
            throw new BusinessException("TENANT_SCOPE_FORBIDDEN", "不能访问其他机构账单");
        }
        return statement;
    }

    private void requireFinance(Authentication authentication) {
        if (!(hasRole(authentication, "ROLE_ADMIN") || hasRole(authentication, "ROLE_FINANCE"))) {
            throw new BusinessException("FINANCE_ROLE_REQUIRED", "仅管理员或财务可执行账务操作");
        }
    }

    private boolean isPlatform(Authentication authentication) {
        return hasRole(authentication, "ROLE_ADMIN") || hasRole(authentication, "ROLE_OPERATOR")
                || hasRole(authentication, "ROLE_FINANCE");
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }

    private Long ownTenantId(Authentication authentication) {
        long userId;
        try {
            userId = Long.parseLong(actor(authentication));
        } catch (NumberFormatException ex) {
            throw new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不合法");
        }
        User user = users.findById(userId)
                .orElseThrow(() -> new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不存在"));
        return user.getTenantId();
    }

    private String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不能为空");
        }
        return authentication.getName();
    }
}
