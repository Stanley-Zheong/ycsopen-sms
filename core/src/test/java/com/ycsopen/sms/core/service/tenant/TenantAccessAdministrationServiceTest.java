package com.ycsopen.sms.core.service.tenant;

import com.ycsopen.sms.core.web.dto.TenantAdministratorCreateRequest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** OBL-F-1-3-A/B and OBL-F-2-7-A contract shape checks. */
class TenantAccessAdministrationServiceTest {
    @Test void accountRequestHasNoTenantSelectorAndOnlyTenantRoles() {
        var request = new TenantAdministratorCreateRequest("business-user", "Passw0rd!", "业务用户", "TENANT_USER");
        assertThat(request.userType()).isIn("TENANT_USER", "TENANT_DEV");
        assertThat(TenantAdministratorCreateRequest.class.getRecordComponents())
                .extracting(component -> component.getName()).doesNotContain("tenantId", "roleId");
    }
}
