package com.example;

import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PrometheusMetricProcessor implements FlatMapFunction<String, Tuple4<String, String, String, Boolean>>, Serializable {
    private static final long serialVersionUID = 1L;
    private final CardinalityTracker tracker;

    public PrometheusMetricProcessor(CardinalityTracker tracker) {
        this.tracker = tracker;
    }

    private Logger getLogger() {
        return LoggerFactory.getLogger(PrometheusMetricProcessor.class);
    }

    @Override
    public void flatMap(String message, Collector<Tuple4<String, String, String, Boolean>> out) {
        getLogger().info("Processing message: {}", message); // Log de entrada
        String[] lines = message.split("\n");
        for (String line : lines) {
            getLogger().debug("Parsing line: {}", line); // Log de cada linha
            MetricParser.Metric parsed = MetricParser.parsePrometheusLine(line);
            if (parsed == null) {
                getLogger().warn("Skipping invalid line: {}", line); // Log para linhas inválidas
                continue;
            }

            tracker.addMetric(parsed.metricName, parsed.labels);

            Map<String, String> filteredLabels = new HashMap<>();
            List<String> removedLabels = new ArrayList<>();
            boolean hasHighCardinality = false;

            for (Map.Entry<String, String> entry : parsed.labels.entrySet()) {
                String labelName = entry.getKey();
                String labelValue = entry.getValue();
                if (tracker.shouldRemoveLabel(parsed.metricName, labelName)) {
                    removedLabels.add(String.format("%s=%s", labelName, labelValue));
                    hasHighCardinality = true;
                } else {
                    filteredLabels.put(labelName, labelValue);
                }
            }

            String processedLine = MetricParser.formatMetricLine(parsed.metricName, filteredLabels, parsed.value);
            if (!removedLabels.isEmpty()) {
                getLogger().info("🔄 METRIC PROCESSED:\n" +
                        "   BEFORE: {}\n" +
                        "   AFTER:  {}\n" +
                        "   REMOVED LABELS: {}\n",
                        parsed.originalLine, processedLine, String.join(", ", removedLabels));
            } else {
                getLogger().debug("No high cardinality labels for: {}", parsed.originalLine); // Log para métricas sem alta cardinalidade
            }

            out.collect(new Tuple4<>(
                    parsed.originalLine,
                    processedLine,
                    String.join(",", removedLabels),
                    hasHighCardinality
            ));
        }
    }
}