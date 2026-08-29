package com.ycsopen.sms.core.web.dto;

import java.math.BigDecimal;

/** F-11.9 仪表盘投诉占比看板单行数据。 */
public record ComplaintRatioItemResponse(
        Long dimensionId,
        String dimensionName,
        long sendCount,
        long complaintCount,
        BigDecimal ratio,
        boolean overThreshold
) {
}
