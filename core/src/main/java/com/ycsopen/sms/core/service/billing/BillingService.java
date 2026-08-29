package com.ycsopen.sms.core.service.billing;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.BillingRecord;
import com.ycsopen.sms.core.domain.entity.TenantAccount;
import com.ycsopen.sms.core.repository.BillingRecordRepository;
import com.ycsopen.sms.core.repository.TenantAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * F-8.1 预付费模式：提交前预扣费用/冻结额度，发送成功确认扣费，失败且不计费的场景自动冲正释放额度。
 * <p>并发安全：{@link TenantAccount} 使用 JPA {@code @Version} 乐观锁（见实体类），
 * reserve() 在 CAS 失败时会抛出 {@link org.springframework.orm.ObjectOptimisticLockingFailureException}，
 * 调用方（MessageSubmitService）应捕获后重试或转为提交失败——这是应对"1000 TPS 并发扣费"
 * （PRD 2.3 节 KPI）的关键正确性保证，不能用简单的 "SELECT 再 UPDATE" 两步操作代替。</p>
 */
@Service
public class BillingService {

    private final TenantAccountRepository tenantAccountRepository;
    private final BillingRecordRepository billingRecordRepository;

    public BillingService(TenantAccountRepository tenantAccountRepository,
                           BillingRecordRepository billingRecordRepository) {
        this.tenantAccountRepository = tenantAccountRepository;
        this.billingRecordRepository = billingRecordRepository;
    }

    /** 提交前预扣（reserve）。余额不足抛 BusinessException，调用方据此拒绝本次提交（F-8.1 验收标准）。 */
    @Transactional
    public BillingRecord reserve(Long tenantId, Long taskRefId, BigDecimal unitPrice) {
        TenantAccount account = tenantAccountRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND", "机构账户不存在"));

        long amountInMil = unitPrice.movePointRight(3).longValueExact(); // 元 -> 厘
        if (account.getBalance() - account.getFrozenAmount() < amountInMil) {
            throw new BusinessException("INSUFFICIENT_BALANCE", "账户余额不足，提交被拒绝（F-8.1）");
        }

        account.setFrozenAmount(account.getFrozenAmount() + amountInMil);
        tenantAccountRepository.save(account); // 乐观锁字段 version 由 JPA 自动比对

        BillingRecord record = new BillingRecord();
        record.setTenantId(tenantId);
        record.setTaskRefId(taskRefId);
        record.setUnitPrice(unitPrice);
        record.setQuantity(1);
        record.setAmount(amountInMil);
        record.setBillingStatus(BillingRecord.BillingStatus.RESERVED);
        record.setBillingDate(LocalDate.now());
        return billingRecordRepository.save(record);
    }

    /** 发送成功后确认扣费：释放冻结额度，同时真正扣减余额。 */
    @Transactional
    public void confirm(Long billingRecordId) {
        BillingRecord record = billingRecordRepository.findById(billingRecordId)
                .orElseThrow(() -> new BusinessException("BILLING_RECORD_NOT_FOUND", "计费记录不存在"));
        if (record.getBillingStatus() != BillingRecord.BillingStatus.RESERVED) {
            return; // 幂等：已确认/已冲正的记录不重复处理
        }
        TenantAccount account = tenantAccountRepository.findByTenantId(record.getTenantId())
                .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND", "机构账户不存在"));

        account.setFrozenAmount(account.getFrozenAmount() - record.getAmount());
        account.setBalance(account.getBalance() - record.getAmount());
        tenantAccountRepository.save(account);

        record.setBillingStatus(BillingRecord.BillingStatus.CONFIRMED);
        billingRecordRepository.save(record);
    }

    /** 发送失败且按规则不计费：冲正，仅释放冻结额度，不扣余额（F-8.1"失败且不计费的场景自动冲正释放额度"）。 */
    @Transactional
    public void reverse(Long billingRecordId) {
        BillingRecord record = billingRecordRepository.findById(billingRecordId)
                .orElseThrow(() -> new BusinessException("BILLING_RECORD_NOT_FOUND", "计费记录不存在"));
        if (record.getBillingStatus() != BillingRecord.BillingStatus.RESERVED) {
            return;
        }
        TenantAccount account = tenantAccountRepository.findByTenantId(record.getTenantId())
                .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND", "机构账户不存在"));

        account.setFrozenAmount(account.getFrozenAmount() - record.getAmount());
        tenantAccountRepository.save(account);

        record.setBillingStatus(BillingRecord.BillingStatus.REVERSED);
        billingRecordRepository.save(record);
    }
}
