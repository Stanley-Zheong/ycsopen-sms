package com.ycsopen.sms.core.repository;

import com.ycsopen.sms.core.domain.entity.Signature;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignatureRepository extends JpaRepository<Signature, Long> {
}
