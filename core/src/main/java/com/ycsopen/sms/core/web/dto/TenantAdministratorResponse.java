package com.ycsopen.sms.core.web.dto;

import java.time.Instant;

public record TenantAdministratorResponse(long id, String username, String realName,
                                          String userType, String status, Instant createdAt,
                                          String roleNames) { }
