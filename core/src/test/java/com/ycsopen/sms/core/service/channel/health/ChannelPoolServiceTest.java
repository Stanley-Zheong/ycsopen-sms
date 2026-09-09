package com.ycsopen.sms.core.service.channel.health;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.Channel;
import com.ycsopen.sms.core.repository.ChannelRepository;
import com.ycsopen.sms.core.web.dto.ChannelPoolRequest;
import com.ycsopen.sms.core.web.dto.ChannelPoolResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChannelPoolServiceTest {

    @Test
    void createsWeightedPoolAndRetainsDisabledIneligibleMember() {
        JdbcTemplate jdbc = ChannelHealthTestSupport.jdbc("pool-weighted");
        Channel normal = ChannelHealthTestSupport.channel(1L, Channel.Status.NORMAL);
        Channel paused = ChannelHealthTestSupport.channel(2L, Channel.Status.PAUSED);
        ChannelPoolService service = service(jdbc, normal, paused);

        var response = service.create(new ChannelPoolRequest("国内营销池", "WEIGHTED", null, List.of(
                new ChannelPoolRequest.Member(1L, 100, false, true),
                new ChannelPoolRequest.Member(2L, 0, false, false)
        )));

        assertThat(response.mode()).isEqualTo("WEIGHTED");
        assertThat(response.members()).hasSize(2);
        assertThat(response.version()).isZero();
    }

    @Test
    void rejectsInvalidWeightedPoolAndIneligibleEnabledMember() {
        JdbcTemplate jdbc = ChannelHealthTestSupport.jdbc("pool-invalid");
        Channel normal = ChannelHealthTestSupport.channel(1L, Channel.Status.NORMAL);
        Channel paused = ChannelHealthTestSupport.channel(2L, Channel.Status.PAUSED);
        ChannelPoolService service = service(jdbc, normal, paused);

        assertThatThrownBy(() -> service.create(new ChannelPoolRequest("bad-weight", "WEIGHTED", null, List.of(
                new ChannelPoolRequest.Member(1L, 90, false, true)
        )))).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo("CHANNEL_POOL_WEIGHT_INVALID"));

        assertThatThrownBy(() -> service.create(new ChannelPoolRequest("bad-member", "WEIGHTED", null, List.of(
                new ChannelPoolRequest.Member(2L, 100, false, true)
        )))).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo("CHANNEL_POOL_MEMBER_INELIGIBLE"));

        assertThatThrownBy(() -> service.create(new ChannelPoolRequest("duplicate-member", "WEIGHTED", null, List.of(
                new ChannelPoolRequest.Member(1L, 50, false, true),
                new ChannelPoolRequest.Member(1L, 50, false, true)
        )))).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo("CHANNEL_POOL_MEMBER_DUPLICATED"));
    }

    @Test
    void primaryBackupRequiresExactlyOnePrimary() {
        JdbcTemplate jdbc = ChannelHealthTestSupport.jdbc("pool-primary");
        Channel first = ChannelHealthTestSupport.channel(1L, Channel.Status.NORMAL);
        Channel second = ChannelHealthTestSupport.channel(2L, Channel.Status.NORMAL);
        ChannelPoolService service = service(jdbc, first, second);

        assertThatThrownBy(() -> service.create(new ChannelPoolRequest("主备池", "PRIMARY_BACKUP", null, List.of(
                new ChannelPoolRequest.Member(1L, 0, true, true),
                new ChannelPoolRequest.Member(2L, 0, true, true)
        )))).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo("CHANNEL_POOL_PRIMARY_INVALID"));

        var response = service.create(new ChannelPoolRequest("主备池", "PRIMARY_BACKUP", null, List.of(
                new ChannelPoolRequest.Member(1L, 0, true, true),
                new ChannelPoolRequest.Member(2L, 0, false, true)
        )));

        assertThat(response.members()).filteredOn(ChannelPoolResponse.Member::primaryMember).hasSize(1);
    }

    @Test
    void staleExpectedVersionIsRejected() {
        JdbcTemplate jdbc = ChannelHealthTestSupport.jdbc("pool-stale");
        Channel normal = ChannelHealthTestSupport.channel(1L, Channel.Status.NORMAL);
        ChannelPoolService service = service(jdbc, normal);
        var created = service.create(new ChannelPoolRequest("池", "WEIGHTED", null,
                List.of(new ChannelPoolRequest.Member(1L, 100, false, true))));

        assertThatThrownBy(() -> service.update(created.id(), new ChannelPoolRequest("池", "WEIGHTED", 99L,
                List.of(new ChannelPoolRequest.Member(1L, 100, false, true)))))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("CHANNEL_POOL_STALE"));
    }

    @Test
    void updateUsesAtomicExpectedVersionCompareAndSet() {
        JdbcTemplate jdbc = ChannelHealthTestSupport.jdbc("pool-cas");
        Channel normal = ChannelHealthTestSupport.channel(1L, Channel.Status.NORMAL);
        ChannelPoolService service = service(jdbc, normal);
        var created = service.create(new ChannelPoolRequest("池", "WEIGHTED", null,
                List.of(new ChannelPoolRequest.Member(1L, 100, false, true))));

        var updated = service.update(created.id(), new ChannelPoolRequest("池A", "WEIGHTED", created.version(),
                List.of(new ChannelPoolRequest.Member(1L, 100, false, true))));

        assertThat(updated.version()).isEqualTo(created.version() + 1);
        assertThatThrownBy(() -> service.update(created.id(), new ChannelPoolRequest("池B", "WEIGHTED", created.version(),
                List.of(new ChannelPoolRequest.Member(1L, 100, false, true)))))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("CHANNEL_POOL_STALE"));
        assertThat(service.get(created.id()).name()).isEqualTo("池A");
    }

    private static ChannelPoolService service(JdbcTemplate jdbc, Channel... channels) {
        ChannelRepository repository = mock(ChannelRepository.class);
        for (Channel channel : channels) {
            when(repository.findById(channel.getId())).thenReturn(Optional.of(channel));
        }
        return new ChannelPoolService(jdbc, repository, new ChannelCandidateEligibilityService());
    }
}
