package com.ycsopen.sms.core.web.dto;

public record SignatureApplicationRequest(
        String signContent,
        String signType,
        String usageType,
        String evidenceRef,
        String applicantName,
        String applicantPhone
) { }
