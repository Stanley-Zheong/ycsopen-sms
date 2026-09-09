package com.ycsopen.sms.core.service.tool;

public interface ProviderStatusTaxonomyPort {
    NormalizedStatus normalize(String providerName, String protocol, String providerCode);

    record NormalizedStatus(String providerName, String protocol, String providerCode, String versionNo,
                            String platformCategory, boolean finalState, boolean billable, boolean retryable,
                            String severity, String advice, String source) { }
}
