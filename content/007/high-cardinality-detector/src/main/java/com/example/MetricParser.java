package com.example;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MetricParser {
    private static final Pattern METRIC_PATTERN = Pattern.compile(
            "^([a-zA-Z_:][a-zA-Z0-9_:]*?)(\\{([^}]*)\\})?\\s+(\\d+\\.?\\d*)(\\s+\\d+)?$");
    private static final Pattern LABEL_PATTERN = Pattern.compile(
            "([a-zA-Z_][a-zA-Z0-9_]*)=\"([^\"]*)\"");

    public static class Metric {
        public String metricName;
        public Map<String, String> labels;
        public String value;
        public String originalLine;

        public Metric(String metricName, Map<String, String> labels, String value, String originalLine) {
            this.metricName = metricName;
            this.labels = labels;
            this.value = value;
            this.originalLine = originalLine;
        }
    }

    public static Metric parsePrometheusLine(String line) {
        if (line == null || line.trim().isEmpty() || line.startsWith("#")) {
            return null;
        }

        Matcher matcher = METRIC_PATTERN.matcher(line.trim());
        if (!matcher.matches()) {
            System.err.println("Failed to parse line: " + line); // Adicionar log para debug
            return null;
        }

        String metricName = matcher.group(1);
        String labelsStr = matcher.group(3) != null ? matcher.group(3) : "";
        String value = matcher.group(4);

        Map<String, String> labels = new HashMap<>();
        Matcher labelMatcher = LABEL_PATTERN.matcher(labelsStr);
        while (labelMatcher.find()) {
            labels.put(labelMatcher.group(1), labelMatcher.group(2));
        }

        return new Metric(metricName, labels, value, line);
    }

    public static String formatMetricLine(String metricName, Map<String, String> labels, String value) {
        if (labels.isEmpty()) {
            return String.format("%s %s", metricName, value);
        }
        String labelsStr = labels.entrySet().stream()
                .map(e -> String.format("%s=\"%s\"", e.getKey(), e.getValue()))
                .collect(Collectors.joining(","));
        return String.format("%s{%s} %s", metricName, labelsStr, value);
    }
}