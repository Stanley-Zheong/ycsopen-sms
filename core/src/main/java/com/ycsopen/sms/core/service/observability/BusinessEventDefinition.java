package com.ycsopen.sms.core.service.observability;

import java.util.List;

/** Immutable contract for one PRD 7.1 business event. */
public record BusinessEventDefinition(
        String code,
        String name,
        String trigger,
        List<EventField> fields) {

    public BusinessEventDefinition {
        if (code == null || !code.matches("evt_[a-z0-9_]+")) {
            throw new IllegalArgumentException("business event code is invalid");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("business event name is required");
        }
        if (trigger == null || trigger.isBlank()) {
            throw new IllegalArgumentException("business event trigger is required");
        }
        fields = List.copyOf(fields);
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("business event fields are required");
        }
    }

    public boolean requiresField(String fieldName) {
        return fields.stream().anyMatch(field -> field.name().equals(fieldName) && field.required());
    }

    public boolean hasField(String fieldName) {
        return fields.stream().anyMatch(field -> field.name().equals(fieldName));
    }

    public record EventField(String name, boolean required, DataProtection protection) {
        public EventField {
            if (name == null || !name.matches("[a-z][A-Za-z0-9]*")) {
                throw new IllegalArgumentException("business event field name is invalid");
            }
            if (protection == null) {
                throw new IllegalArgumentException("business event field protection is required");
            }
        }
    }

    public enum DataProtection {
        PUBLIC,
        INTERNAL_ID,
        MASKED_PII,
        PROTECTED_VALUE
    }
}
