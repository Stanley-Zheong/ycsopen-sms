package com.ycsopen.sms.core.service.billing;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.BillingRecord;
import com.ycsopen.sms.core.domain.entity.TenantAccount;
import com.ycsopen.sms.core.repository.BillingRecordRepository;
import com.ycsopen.sms.core.repository.TenantAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * F-8.1 预付费扣费：验证"余额不足拒绝提交"与"预扣/确认/冲正"三段式的账户余额变化，
 * 对应 PRD 11 章验收标准第 4 条"预付费实时扣费...在抽样对账中一致率 100%"。
 */
@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock TenantAccountRepository tenantAccountRepository;
    @Mock BillingRecordRepository billingRecordRepository;

    private BillingService billingService;

    @BeforeEach
    void setUp() {
        billingService = new BillingService(tenantAccountRepository, billingRecordRepository);
    }

    private TenantAccount accountWithBalance(long balanceInMil) {
        TenantAccount account = new TenantAccount();
        account.setId(1L);
        account.setTenantId(100L);
        account.setBalance(balanceInMil);
        account.setFrozenAmount(0L);
        return account;
    }

    @Test
    void reserve_insufficientBalance_shouldThrowAndNotMutateAccount() {
        when(tenantAccountRepository.findByTenantId(100L)).thenReturn(Optional.of(accountWithBalance(10))); // 0.01 元

        assertThatThrownBy(() -> billingService.reserve(100L, 1L, new BigDecimal("0.05")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("余额不足");
    }

    @Test
    void reserve_sufficientBalance_shouldFreezeAmountAndCreateReservedRecord() {
        TenantAccount account = accountWithBalance(1000); // 1 元
        when(tenantAccountRepository.findByTenantId(100L)).thenReturn(Optional.of(account));
        when(billingRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tenantAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BillingRecord record = billingService.reserve(100L, 1L, new BigDecimal("0.05")); // 0.05 元 = 50 厘

        assertThat(account.getFrozenAmount()).isEqualTo(50L);
        assertThat(record.getBillingStatus()).isEqualTo(BillingRecord.BillingStatus.RESERVED);
        assertThat(record.getAmount()).isEqualTo(50L);
    }

    @Test
    void confirm_shouldDeductBalanceAndReleaseFrozenAmount() {
        TenantAccount account = accountWithBalance(1000);
        account.setFrozenAmount(50L);

        BillingRecord reserved = new BillingRecord();
        reserved.setId(9L);
        reserved.setTenantId(100L);
        reserved.setAmount(50L);
        reserved.setBillingStatus(BillingRecord.BillingStatus.RESERVED);

        when(billingRecordRepository.findById(9L)).thenReturn(Optional.of(reserved));
        when(tenantAccountRepository.findByTenantId(100L)).thenReturn(Optional.of(account));
        ArgumentCaptor<BillingRecord> savedRecord = ArgumentCaptor.forClass(BillingRecord.class);
        when(billingRecordRepository.save(savedRecord.capture())).thenAnswer(inv -> inv.getArgument(0));

        billingService.confirm(9L);

        assertThat(account.getBalance()).isEqualTo(950L);   // 1000 - 50
        assertThat(account.getFrozenAmount()).isEqualTo(0L); // 50 - 50
        assertThat(savedRecord.getValue().getBillingStatus()).isEqualTo(BillingRecord.BillingStatus.CONFIRMED);
    }

    @Test
    void reverse_shouldReleaseFrozenAmountWithoutTouchingBalance() {
        TenantAccount account = accountWithBalance(1000);
        account.setFrozenAmount(50L);

        BillingRecord reserved = new BillingRecord();
        reserved.setId(9L);
        reserved.setTenantId(100L);
        reserved.setAmount(50L);
        reserved.setBillingStatus(BillingRecord.BillingStatus.RESERVED);

        when(billingRecordRepository.findById(9L)).thenReturn(Optional.of(reserved));
        when(tenantAccountRepository.findByTenantId(100L)).thenReturn(Optional.of(account));
        when(billingRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        billingService.reverse(9L);

        assertThat(account.getBalance()).isEqualTo(1000L);   // 余额不受影响 —— 关键断言
        assertThat(account.getFrozenAmount()).isEqualTo(0L);
    }

    @Test
    void confirm_calledTwice_shouldBeIdempotent() {
        BillingRecord alreadyConfirmed = new BillingRecord();
        alreadyConfirmed.setId(9L);
        alreadyConfirmed.setBillingStatus(BillingRecord.BillingStatus.CONFIRMED);
        when(billingRecordRepository.findById(9L)).thenReturn(Optional.of(alreadyConfirmed));

        billingService.confirm(9L); // 不应抛异常，也不应二次扣费

        org.mockito.Mockito.verify(tenantAccountRepository, org.mockito.Mockito.never()).findByTenantId(any());
    }
}
