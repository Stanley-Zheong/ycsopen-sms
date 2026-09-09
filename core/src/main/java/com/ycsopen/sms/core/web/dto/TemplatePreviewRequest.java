package com.ycsopen.sms.core.web.dto;

import java.util.Map;

public record TemplatePreviewRequest(Map<String, String> variables) { }
