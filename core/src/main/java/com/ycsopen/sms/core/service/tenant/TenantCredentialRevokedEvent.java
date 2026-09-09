package com.ycsopen.sms.core.service.tenant;

import java.time.Instant;

/** Small in-process notification; it intentionally carries no credential material. */
public record TenantCredentialRevokedEvent(long tenantId, String credentialType,
                                           long credentialId, Instant occurredAt) { }
