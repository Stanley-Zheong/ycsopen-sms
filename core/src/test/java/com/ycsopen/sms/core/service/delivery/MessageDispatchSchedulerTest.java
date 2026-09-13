package com.ycsopen.sms.core.service.delivery;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.Mockito.*;

class MessageDispatchSchedulerTest {
    @Test
    void scheduledTickDispatchesOneReadyMessage() {
        HttpMessageDeliveryService delivery = mock(HttpMessageDeliveryService.class);
        when(delivery.dispatchNext()).thenReturn(Optional.of(new HttpMessageDeliveryService.DispatchResult("m", "ACCEPTED", "p", null)));
        new MessageDispatchScheduler(delivery).tick();
        verify(delivery).dispatchNext();
    }
}
