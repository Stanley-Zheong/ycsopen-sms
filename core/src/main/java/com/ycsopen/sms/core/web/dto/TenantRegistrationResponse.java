package com.ycsopen.sms.core.web.dto;

import com.ycsopen.sms.core.domain.entity.Tenant;

/** Public registration state with no identity plaintext, object ID, token, or storage detail. */
public record TenantRegistrationResponse(
        Long tenantId,
        String tenantNo,
        String shortName,
        String fullName,
        Tenant.VerificationStatus verificationStatus,
        Tenant.LifecycleStatus lifecycleStatus) {

    public static TenantRegistrationResponse from(Tenant tenant) {
        if (tenant == null || tenant.getId() == null) {
            throw new IllegalArgumentException("persisted tenant is required");
        }
        return new TenantRegistrationResponse(tenant.getId(), tenant.getTenantNo(),
                tenant.getShortName(), tenant.getFullName(), tenant.getVerificationStatus(),
                tenant.getLifecycleStatus());
    }
}
