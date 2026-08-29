package com.ycsopen.sms.core.repository;

import com.ycsopen.sms.core.domain.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
}
