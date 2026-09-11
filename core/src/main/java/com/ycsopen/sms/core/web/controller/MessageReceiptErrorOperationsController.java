package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.service.delivery.MessageReceiptErrorOperationsService;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/console/message-operations")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
public class MessageReceiptErrorOperationsController {
    private final MessageReceiptErrorOperationsService operations;

    public MessageReceiptErrorOperationsController(MessageReceiptErrorOperationsService operations) {
        this.operations = operations;
    }

    @GetMapping("/submissions")
    public ApiResponse<List<MessageReceiptErrorOperationsService.SubmissionRow>> submissions(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String messageId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long channelId,
            @RequestParam(required = false) String errorCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt) {
        return ApiResponse.ok(operations.submissions(filter(tenantId, messageId, status, channelId, errorCode,
                startAt, endAt)));
    }

    @GetMapping("/sends")
    public ApiResponse<List<MessageReceiptErrorOperationsService.SendRow>> sends(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String messageId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long channelId,
            @RequestParam(required = false) String errorCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt) {
        return ApiResponse.ok(operations.sends(filter(tenantId, messageId, status, channelId, errorCode,
                startAt, endAt)));
    }

    @GetMapping("/receipts")
    public ApiResponse<List<MessageReceiptErrorOperationsService.ReceiptRow>> receipts(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String messageId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long channelId,
            @RequestParam(required = false) String errorCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt) {
        return ApiResponse.ok(operations.receipts(filter(tenantId, messageId, status, channelId, errorCode,
                startAt, endAt)));
    }

    @GetMapping("/errors")
    public ApiResponse<List<MessageReceiptErrorOperationsService.ErrorGroupRow>> errors(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String messageId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long channelId,
            @RequestParam(required = false) String errorCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt) {
        return ApiResponse.ok(operations.errorGroups(filter(tenantId, messageId, status, channelId, errorCode,
                startAt, endAt)));
    }

    @PostMapping("/sends/{messageId}/resend")
    public ApiResponse<MessageReceiptErrorOperationsService.ActionResult> resend(@PathVariable String messageId,
            @RequestBody MessageReceiptErrorOperationsService.ActionRequest request,
            Authentication authentication) {
        return ApiResponse.ok(operations.resend(messageId, request, actor(authentication)));
    }

    @PostMapping("/sends/{messageId}/appeal")
    public ApiResponse<MessageReceiptErrorOperationsService.ActionResult> appeal(@PathVariable String messageId,
            @RequestBody MessageReceiptErrorOperationsService.ActionRequest request,
            Authentication authentication) {
        return ApiResponse.ok(operations.appeal(messageId, request, actor(authentication)));
    }

    @PostMapping("/receipts/{receiptId}/correct")
    public ApiResponse<MessageReceiptErrorOperationsService.ActionResult> correctReceipt(@PathVariable long receiptId,
            @RequestBody MessageReceiptErrorOperationsService.ReceiptCorrectionRequest request,
            Authentication authentication) {
        return ApiResponse.ok(operations.correctReceipt(receiptId, request, actor(authentication)));
    }

    @PostMapping("/receipts/{receiptId}/replay")
    public ApiResponse<MessageReceiptErrorOperationsService.ActionResult> replayReceipt(@PathVariable long receiptId,
            @RequestBody MessageReceiptErrorOperationsService.ActionRequest request,
            Authentication authentication) {
        return ApiResponse.ok(operations.replayReceipt(receiptId, request, actor(authentication)));
    }

    @PostMapping("/errors/actions")
    public ApiResponse<MessageReceiptErrorOperationsService.BulkActionResult> bulkErrorAction(
            @RequestBody MessageReceiptErrorOperationsService.BulkErrorActionRequest request,
            Authentication authentication) {
        return ApiResponse.ok(operations.bulkErrors(request, actor(authentication)));
    }

    @PostMapping("/exports")
    public ApiResponse<MessageReceiptErrorOperationsService.ActionResult> export(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String messageId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long channelId,
            @RequestParam(required = false) String errorCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt,
            @RequestBody MessageReceiptErrorOperationsService.ActionRequest request,
            Authentication authentication) {
        return ApiResponse.ok(operations.exportRequest(filter(tenantId, messageId, status, channelId, errorCode,
                startAt, endAt), request, actor(authentication)));
    }

    private static String actor(Authentication authentication) {
        return authentication == null ? "system" : authentication.getName();
    }

    private static MessageReceiptErrorOperationsService.OperationFilter filter(Long tenantId, String messageId,
                                                                               String status, Long channelId,
                                                                               String errorCode,
                                                                               LocalDateTime startAt,
                                                                               LocalDateTime endAt) {
        return new MessageReceiptErrorOperationsService.OperationFilter(
                tenantId, messageId, status, channelId, errorCode, startAt, endAt);
    }
}
