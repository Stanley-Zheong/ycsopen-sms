package com.ycsopen.sms.core.domain.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Keeps fixed-width database padding out of domain values on every supported JDBC driver. */
@Converter
public class FixedWidthCharConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute;
    }

    @Override
    public String convertToEntityAttribute(String databaseValue) {
        return removePadding(databaseValue);
    }

    public static String removePadding(String value) {
        return value == null ? null : value.stripTrailing();
    }
}
