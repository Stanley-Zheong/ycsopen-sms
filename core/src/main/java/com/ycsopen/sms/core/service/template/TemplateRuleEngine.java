package com.ycsopen.sms.core.service.template;

import com.ycsopen.sms.core.common.exception.BusinessException;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Phase 13 shared parser/renderer for template variables and parameter rules. */
final class TemplateRuleEngine {
    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([A-Za-z][A-Za-z0-9_]*)}");
    private static final Pattern ANY_PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern RULE = Pattern.compile("([A-Za-z][A-Za-z0-9_]*):(digits|text|number)\\((\\d+)-(\\d+)\\)");
    private static final Set<String> UNSAFE_TERMS = Set.of("博彩", "赌博", "贷款", "代开发票", "私彩");

    private TemplateRuleEngine() {
    }

    static List<String> variables(String content) {
        String safeContent = required(content, "TEMPLATE_CONTENT_REQUIRED", 500);
        for (String term : UNSAFE_TERMS) {
            if (safeContent.contains(term)) {
                throw new BusinessException("TEMPLATE_CONTENT_UNSAFE", "模板内容包含高风险词");
            }
        }
        Matcher any = ANY_PLACEHOLDER.matcher(safeContent);
        List<String> names = new ArrayList<>();
        while (any.find()) {
            String raw = any.group(1);
            if (!raw.matches("[A-Za-z][A-Za-z0-9_]*")) {
                throw new BusinessException("TEMPLATE_VARIABLE_SYNTAX_INVALID", "变量占位符格式不合法");
            }
            if (!names.contains(raw)) {
                names.add(raw);
            }
        }
        return names;
    }

    static Map<String, Rule> rules(String ruleText, List<String> variables) {
        if (ruleText == null || ruleText.trim().isEmpty()) {
            return Map.of();
        }
        Map<String, Rule> rules = new LinkedHashMap<>();
        for (String part : ruleText.split(";")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Matcher matcher = RULE.matcher(trimmed);
            if (!matcher.matches()) {
                throw new BusinessException("TEMPLATE_PARAM_RULE_INVALID", "参数规则格式不合法");
            }
            String name = matcher.group(1);
            int min = Integer.parseInt(matcher.group(3));
            int max = Integer.parseInt(matcher.group(4));
            if (min > max || !variables.contains(name) || rules.containsKey(name)) {
                throw new BusinessException("TEMPLATE_PARAM_RULE_INVALID", "参数规则与变量不匹配");
            }
            rules.put(name, new Rule(matcher.group(2), min, max));
        }
        return rules;
    }

    static String render(String content, List<String> variables, String ruleText, Map<String, String> params) {
        Map<String, String> values = params == null ? Map.of() : params;
        Set<String> expected = new LinkedHashSet<>(variables);
        Set<String> actual = new LinkedHashSet<>(values.keySet());
        Set<String> missing = new LinkedHashSet<>(expected);
        missing.removeAll(actual);
        if (!missing.isEmpty()) {
            throw new BusinessException("TEMPLATE_VARIABLE_MISSING", "模板变量缺失");
        }
        Set<String> extra = new LinkedHashSet<>(actual);
        extra.removeAll(expected);
        if (!extra.isEmpty()) {
            throw new BusinessException("TEMPLATE_VARIABLE_EXTRA", "模板变量多余");
        }
        Map<String, Rule> rules = rules(ruleText, variables);
        String rendered = content;
        for (String variable : variables) {
            String value = values.get(variable);
            validateValue(variable, value, rules.get(variable));
            rendered = rendered.replace("${" + variable + "}", value);
        }
        return rendered;
    }

    static String required(String value, String code, int max) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(code, "必填字段不能为空");
        }
        String cleaned = value.trim();
        if (cleaned.length() > max) {
            throw new BusinessException("TEMPLATE_FIELD_TOO_LONG", "字段超过长度限制");
        }
        return cleaned;
    }

    static String optional(String value, int max) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String cleaned = value.trim();
        if (cleaned.length() > max) {
            throw new BusinessException("TEMPLATE_FIELD_TOO_LONG", "字段超过长度限制");
        }
        return cleaned;
    }

    static String joinVariables(List<String> variables) {
        return String.join(",", variables);
    }

    static List<String> splitVariables(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(",")).filter(item -> !item.isBlank()).toList();
    }

    private static void validateValue(String name, String value, Rule rule) {
        if (value == null) {
            throw new BusinessException("TEMPLATE_VARIABLE_MISSING", "模板变量缺失");
        }
        if (value.contains("<") || value.contains(">") || value.contains("${") || value.contains("\n") || value.contains("\r")) {
            throw new BusinessException("TEMPLATE_VARIABLE_RULE_MISMATCH", "变量值不符合规则");
        }
        if (rule == null) {
            return;
        }
        int length = value.length();
        if (length < rule.min() || length > rule.max()) {
            throw new BusinessException("TEMPLATE_VARIABLE_RULE_MISMATCH", "变量值长度不符合规则");
        }
        boolean matches = switch (rule.kind()) {
            case "digits" -> value.matches("\\d+");
            case "number" -> value.matches("\\d+(?:\\.\\d+)?");
            default -> true;
        };
        if (!matches) {
            throw new BusinessException("TEMPLATE_VARIABLE_RULE_MISMATCH", "变量值类型不符合规则");
        }
    }

    record Rule(String kind, int min, int max) { }
}
