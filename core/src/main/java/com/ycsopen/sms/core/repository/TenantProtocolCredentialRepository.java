package com.ycsopen.sms.core.repository;

import com.ycsopen.sms.core.domain.entity.TenantProtocolCredential;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TenantProtocolCredentialRepository extends JpaRepository<TenantProtocolCredential, Long> { }
