package com.example;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CardinalityTracker implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final int HIGH_CARDINALITY_THRESHOLD = 10;

    private final Map<String, Map<String, Set<String>>> labelValues;
    private final Map<String, Set<String>> highCardinalityLabels;
    private int totalHighCardinalityMetrics;

    public CardinalityTracker() {
        this.labelValues = new HashMap<>();
        this.highCardinalityLabels = new HashMap<>();
        this.totalHighCardinalityMetrics = 0;
    }

    private Logger getLogger() {
        return LoggerFactory.getLogger(CardinalityTracker.class);
    }

    public void addMetric(String metricName, Map<String, String> labels) {
        labelValues.computeIfAbsent(metricName, k -> new HashMap<>());
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            String labelName = entry.getKey();
            String labelValue = entry.getValue();
            labelValues.get(metricName).computeIfAbsent(labelName, k -> new HashSet<>()).add(labelValue);

            if (labelValues.get(metricName).get(labelName).size() > HIGH_CARDINALITY_THRESHOLD) {
                highCardinalityLabels.computeIfAbsent(metricName, k -> new HashSet<>()).add(labelName);
                totalHighCardinalityMetrics++;
                getLogger().info("🚨 HIGH CARDINALITY DETECTED:\n" +
                        "   Metric: {}\n" +
                        "   Label: {}\n" +
                        "   Unique values: {}\n" +
                        "   Threshold: {}\n" +
                        "   🔢 Total high-cardinality metrics so far: {}\n",
                        metricName, labelName, labelValues.get(metricName).get(labelName).size(),
                        HIGH_CARDINALITY_THRESHOLD, totalHighCardinalityMetrics);
            }
        }
    }

    public boolean shouldRemoveLabel(String metricName, String labelName) {
        return highCardinalityLabels.getOrDefault(metricName, new HashSet<>()).contains(labelName);
    }
}