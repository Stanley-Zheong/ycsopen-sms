package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.service.uplink.UplinkNormalizationService;
import com.ycsopen.sms.core.service.uplink.UplinkNormalizationService.UplinkRecord;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Profile({"dev", "test"})
@RestController
public class SimulatorUplinkController {
    private final UplinkNormalizationService service;

    public SimulatorUplinkController(UplinkNormalizationService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/simulator/uplinks")
    public ApiResponse<UplinkRecord> create(@RequestBody SimulatorUplinkRequest request) {
        SimulatorUplinkRequest checked = request.checked();
        return ApiResponse.ok(service.normalizeHttpUplink(
                checked.tenantId(),
                checked.sourceConnector(),
                checked.sourceEventId(),
                checked.messageId(),
                checked.phoneNumber(),
                checked.content(),
                checked.contentKeyword(),
                checked.carrier(),
                checked.province(),
                checked.city(),
                checked.destination(),
                checked.channelId(),
                checked.signatureId(),
                checked.productCode(),
                checked.pushRequested(),
                checked.receiveTime()));
    }

    public record SimulatorUplinkRequest(long tenantId,
                                         String sourceConnector,
                                         String sourceEventId,
                                         String messageId,
                                         String phoneNumber,
                                         String content,
                                         String contentKeyword,
                                         String carrier,
                                         String province,
                                         String city,
                                         String destination,
                                         Long channelId,
                                         Long signatureId,
                                         String productCode,
                                         boolean pushRequested,
                                         LocalDateTime receiveTime) {
        SimulatorUplinkRequest checked() {
            return new SimulatorUplinkRequest(
                    tenantId,
                    blankToDefault(sourceConnector, "simulator-http"),
                    sourceEventId,
                    messageId,
                    phoneNumber,
                    content,
                    contentKeyword,
                    carrier,
                    province,
                    city,
                    destination,
                    channelId,
                    signatureId,
                    productCode,
                    pushRequested,
                    receiveTime);
        }

        private static String blankToDefault(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }
    }
}
