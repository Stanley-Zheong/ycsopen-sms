package com.ycsopen.sms.core.service.statistics;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class StatisticsAggregationRefreshSchedulerTest {
    @Test
    void failedTickDoesNotDisableTheNextRefreshAttempt() {
        StatisticsAggregationService statistics = mock(StatisticsAggregationService.class);
        StatisticsAggregationRefreshScheduler scheduler = new StatisticsAggregationRefreshScheduler(statistics);
        doThrow(new IllegalStateException("temporary failure"))
                .doReturn(null)
                .when(statistics).refreshAutomatically();

        scheduler.refresh();
        scheduler.refresh();

        verify(statistics, times(2)).refreshAutomatically();
    }
}
