package com.ycsopen.sms.core.service.tenant;

import com.ycsopen.sms.core.web.dto.TenantProtocolCredentialResponse;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** OBL-F-2-6-C contract check: later projections are masked. */
class TenantProtocolCredentialServiceTest {
    @Test void safeProjectionMasksAccountAndPassword() {
        var response = new TenantProtocolCredentialResponse(1, "CMPP", "******", "spid",
                "127.0.0.1", 7890, 4, 100, 8, null, "ACTIVE", "******");
        assertThat(response.account()).isEqualTo("******");
        assertThat(response.password()).isEqualTo("******");
    }
}
