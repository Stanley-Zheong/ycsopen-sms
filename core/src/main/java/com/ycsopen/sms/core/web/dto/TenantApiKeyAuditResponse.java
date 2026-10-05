package com.ycsopen.sms.core.web.dto;

import java.time.Instant;

/** Redacted tenant-facing lifecycle evidence for one HTTP API credential. */
public record TenantApiKeyAuditResponse(long id, String actor, String operation,
                                        String resourceId, String result,
                                        Instant occurredAt) { }
