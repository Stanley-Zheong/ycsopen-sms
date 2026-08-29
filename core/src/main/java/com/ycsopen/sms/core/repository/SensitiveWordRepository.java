package com.ycsopen.sms.core.repository;

import com.ycsopen.sms.core.domain.entity.SensitiveWord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SensitiveWordRepository extends JpaRepository<SensitiveWord, Long> {
    List<SensitiveWord> findAllByStatus(SensitiveWord.Status status);
}
