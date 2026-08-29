package com.ycsopen.sms.core.repository;

import com.ycsopen.sms.core.domain.entity.Template;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateRepository extends JpaRepository<Template, Long> {
}
