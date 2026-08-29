package com.ycsopen.sms.core.service.complaint;

import com.ycsopen.sms.core.domain.entity.Channel;
import com.ycsopen.sms.core.domain.entity.ComplaintRatioStats;
import com.ycsopen.sms.core.domain.entity.Tenant;
import com.ycsopen.sms.core.repository.ChannelRepository;
import com.ycsopen.sms.core.repository.ComplaintRatioStatsRepository;
import com.ycsopen.sms.core.repository.ComplaintRepository;
import com.ycsopen.sms.core.repository.MessageTaskRepository;
import com.ycsopen.sms.core.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * F-11.9 验收要点：投诉占比 = 投诉量/发送量；默认阈值千分之三（0.003），
 * 达到或超过阈值即标记 overThreshold=true，供仪表盘标红置顶。
 * 阈值本身的业务含义在 PRD 5.11 节已标注为"待与业务方确认"的假设，测试只锁定当前实现口径。
 */
@ExtendWith(MockitoExtension.class)
class ComplaintRatioServiceTest {

    @Mock TenantRepository tenantRepository;
    @Mock ChannelRepository channelRepository;
    @Mock MessageTaskRepository messageTaskRepository;
    @Mock ComplaintRepository complaintRepository;
    @Mock ComplaintRatioStatsRepository complaintRatioStatsRepository;

    private ComplaintRatioService newService(double threshold) {
        return new ComplaintRatioService(tenantRepository, channelRepository, messageTaskRepository,
                complaintRepository, complaintRatioStatsRepository, threshold);
    }

    private Tenant tenant(long id) {
        Tenant t = new Tenant();
        t.setId(id);
        return t;
    }

    private Channel channel(long id) {
        Channel c = new Channel();
        c.setId(id);
        return c;
    }

    @Test
    void ratioAtExactlyThreeInThousand_shouldBeMarkedOverThreshold() {
        when(tenantRepository.findAll()).thenReturn(List.of(tenant(1L)));
        when(channelRepository.findAll()).thenReturn(List.of());
        // 1000 条发送，3 条投诉 => 0.003 恰好等于默认阈值
        when(messageTaskRepository.countByTenantIdAndCreatedAtBetween(eq(1L), any(), any())).thenReturn(1000L);
        when(complaintRepository.countByTenantIdAndCreatedAtBetween(eq(1L), any(), any())).thenReturn(3L);
        when(complaintRatioStatsRepository.findByStatMonthAndDimensionTypeAndDimensionId(any(), any(), anyLong()))
                .thenReturn(Optional.empty());
        ArgumentCaptor<ComplaintRatioStats> captor = ArgumentCaptor.forClass(ComplaintRatioStats.class);
        when(complaintRatioStatsRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        newService(0.003).recalculate(YearMonth.of(2026, 8));

        ComplaintRatioStats saved = captor.getValue();
        assertThat(saved.getRatio().doubleValue()).isEqualTo(0.003, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(saved.getOverThreshold()).isTrue();
    }

    @Test
    void ratioBelowThreshold_shouldNotBeFlagged() {
        when(tenantRepository.findAll()).thenReturn(List.of(tenant(1L)));
        when(channelRepository.findAll()).thenReturn(List.of());
        when(messageTaskRepository.countByTenantIdAndCreatedAtBetween(eq(1L), any(), any())).thenReturn(10000L);
        when(complaintRepository.countByTenantIdAndCreatedAtBetween(eq(1L), any(), any())).thenReturn(1L); // 0.0001
        when(complaintRatioStatsRepository.findByStatMonthAndDimensionTypeAndDimensionId(any(), any(), anyLong()))
                .thenReturn(Optional.empty());
        ArgumentCaptor<ComplaintRatioStats> captor = ArgumentCaptor.forClass(ComplaintRatioStats.class);
        when(complaintRatioStatsRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        newService(0.003).recalculate(YearMonth.of(2026, 8));

        assertThat(captor.getValue().getOverThreshold()).isFalse();
    }

    @Test
    void zeroSendCount_shouldNotDivideByZero_ratioIsZero() {
        when(tenantRepository.findAll()).thenReturn(List.of());
        when(channelRepository.findAll()).thenReturn(List.of(channel(5L)));
        when(messageTaskRepository.countByChannelIdAndCreatedAtBetween(eq(5L), any(), any())).thenReturn(0L);
        when(complaintRepository.countByChannelIdAndCreatedAtBetween(eq(5L), any(), any())).thenReturn(0L);
        when(complaintRatioStatsRepository.findByStatMonthAndDimensionTypeAndDimensionId(any(), any(), anyLong()))
                .thenReturn(Optional.empty());
        ArgumentCaptor<ComplaintRatioStats> captor = ArgumentCaptor.forClass(ComplaintRatioStats.class);
        when(complaintRatioStatsRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        newService(0.003).recalculate(YearMonth.of(2026, 8));

        assertThat(captor.getValue().getRatio().doubleValue()).isZero();
        assertThat(captor.getValue().getOverThreshold()).isFalse();
    }

    private static <T> T eq(T value) { return org.mockito.ArgumentMatchers.eq(value); }
}
