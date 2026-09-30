package com.morrow.ats.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.List;

@Converter
public class SkillListConverter implements AttributeConverter<List<String>, String> {
    @Override
    public String convertToDatabaseColumn(List<String> skills) {
        return skills == null ? "" : String.join("\n", skills);
    }

    @Override
    public List<String> convertToEntityAttribute(String value) {
        return value == null || value.isBlank() ? List.of() : Arrays.asList(value.split("\\R"));
    }
}