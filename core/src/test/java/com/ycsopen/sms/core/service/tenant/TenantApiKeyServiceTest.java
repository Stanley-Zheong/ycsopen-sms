package com.ycsopen.sms.core.service.tenant;

import com.ycsopen.sms.core.web.dto.TenantApiKeyCreateRequest;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;

/** OBL-F-2-6-A/B contract checks for safe policy input and response projection. */
class TenantApiKeyServiceTest {
    @Test void policyRequestDoesNotAcceptTenantIdOrSecret() {
        var request = new TenantApiKeyCreateRequest("integration", "synthetic", LocalDateTime.now().plusDays(1),
                "127.0.0.1/32", 10, 100, 1_000, 10_000);
        assertThat(request.perDay()).isEqualTo(10_000);
        assertThat(TenantApiKeyCreateRequest.class.getRecordComponents())
                .extracting(component -> component.getName()).doesNotContain("tenantId", "secret");
    }
}
