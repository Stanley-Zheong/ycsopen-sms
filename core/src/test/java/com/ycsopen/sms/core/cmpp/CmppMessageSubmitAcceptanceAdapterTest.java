package com.ycsopen.sms.core.cmpp;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.message.MessageSubmitService;
import com.ycsopen.sms.core.web.dto.SmsSendRequest;
import com.ycsopen.sms.core.web.dto.SmsSendResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CmppMessageSubmitAcceptanceAdapterTest {
    @Test
    void adapterPassesCmppBindingMetadataToSharedMessageSubmitService() {
        MessageSubmitService messages = mock(MessageSubmitService.class);
        when(messages.submit(eq(91L), org.mockito.ArgumentMatchers.any(SmsSendRequest.class), eq("10.0.0.7")))
                .thenReturn(new SmsSendResponse("MSG-100", "PENDING"));
        CmppMessageSubmitAcceptanceAdapter adapter = new CmppMessageSubmitAcceptanceAdapter(messages);

        var result = adapter.accept(new CmppDownstreamAcceptancePort.SubmitCommand(
                91, 7, "10.0.0.7", "submit-100", "13800138000",
                "sp100", "1001", "PRODUCT_A", "code=1234", true));

        ArgumentCaptor<SmsSendRequest> request = ArgumentCaptor.forClass(SmsSendRequest.class);
        verify(messages).submit(eq(91L), request.capture(), eq("10.0.0.7"));
        assertThat(result.accepted()).isTrue();
        assertThat(result.messageId()).isEqualTo("MSG-100");
        assertThat(request.getValue().templateId()).isEqualTo("1001");
        assertThat(request.getValue().templateParams()).containsEntry("content", "code=1234")
                .containsEntry("_cmpp_service_id", "sp100")
                .containsEntry("_cmpp_product_code", "PRODUCT_A")
                .containsEntry("_cmpp_credential_id", "7");
    }

    @Test
    void adapterRejectsMissingCmppProductBeforeSharedSubmit() {
        MessageSubmitService messages = mock(MessageSubmitService.class);
        CmppMessageSubmitAcceptanceAdapter adapter = new CmppMessageSubmitAcceptanceAdapter(messages);

        var result = adapter.accept(new CmppDownstreamAcceptancePort.SubmitCommand(
                91, 7, "10.0.0.7", "submit-100", "13800138000",
                "sp100", "1001", "", "code=1234", true));

        assertThat(result.accepted()).isFalse();
        assertThat(result.errorCode()).isEqualTo("CMPP_BINDING_REQUIRED");
        verifyNoInteractions(messages);
    }

    @Test
    void adapterMapsSharedBusinessRejectionToAcceptanceRejection() {
        MessageSubmitService messages = mock(MessageSubmitService.class);
        when(messages.submit(eq(91L), org.mockito.ArgumentMatchers.any(SmsSendRequest.class), eq("10.0.0.7")))
                .thenThrow(new BusinessException("TEMPLATE_NOT_APPROVED", "模板未通过审核"));
        CmppMessageSubmitAcceptanceAdapter adapter = new CmppMessageSubmitAcceptanceAdapter(messages);

        var result = adapter.accept(new CmppDownstreamAcceptancePort.SubmitCommand(
                91, 7, "10.0.0.7", "submit-100", "13800138000",
                "sp100", "1001", "PRODUCT_A", "code=1234", true));

        assertThat(result.accepted()).isFalse();
        assertThat(result.errorCode()).isEqualTo("TEMPLATE_NOT_APPROVED");
    }
}
