package com.ycsopen.sms.core.service.statistics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Replaces every changed business date; a failed tick keeps the durable watermark for retry. */
@Component
public class StatisticsAggregationRefreshScheduler {
    private static final Logger log = LoggerFactory.getLogger(StatisticsAggregationRefreshScheduler.class);

    private final StatisticsAggregationService statistics;

    public StatisticsAggregationRefreshScheduler(StatisticsAggregationService statistics) {
        this.statistics = statistics;
    }

    @Scheduled(fixedDelayString = "${ycsopen.statistics.refresh.fixed-delay:PT5S}")
    public void refresh() {
        try {
            statistics.refreshAutomatically();
        } catch (RuntimeException failure) {
            log.warn("Statistics refresh failed; durable watermark remains unchanged", failure);
        }
    }
}
