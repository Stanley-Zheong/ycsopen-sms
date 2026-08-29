package com.ycsopen.sms.core.repository;

import com.ycsopen.sms.core.domain.entity.ComplaintRatioStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ComplaintRatioStatsRepository extends JpaRepository<ComplaintRatioStats, Long> {
    List<ComplaintRatioStats> findByStatMonthAndDimensionTypeOrderByRatioDesc(
            String statMonth, ComplaintRatioStats.DimensionType dimensionType);

    Optional<ComplaintRatioStats> findByStatMonthAndDimensionTypeAndDimensionId(
            String statMonth, ComplaintRatioStats.DimensionType dimensionType, Long dimensionId);
}
