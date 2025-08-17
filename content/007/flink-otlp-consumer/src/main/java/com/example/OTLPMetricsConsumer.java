package com.example.flink.otlp;

import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.metrics.v1.ScopeMetrics;
import io.opentelemetry.proto.common.v1.KeyValue;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.flink.connector.prometheus.sink.PrometheusSink;
import org.apache.flink.connector.prometheus.sink.PrometheusSinkConfiguration.RetryConfiguration;
import org.apache.flink.connector.prometheus.sink.PrometheusSinkConfiguration.SinkWriterErrorHandlingBehaviorConfiguration;
import org.apache.flink.connector.prometheus.sink.PrometheusTimeSeries;
import org.apache.flink.connector.prometheus.sink.PrometheusTimeSeriesLabelsAndMetricNameKeySelector;
import static org.apache.flink.connector.prometheus.sink.PrometheusSinkConfiguration.OnErrorBehavior.DISCARD_AND_CONTINUE;
import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.api.common.functions.MapFunction;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.List;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class OTLPMetricsConsumer {
    private static final Logger log = LoggerFactory.getLogger(OTLPMetricsConsumer.class);

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        
        log.info("=== Iniciando OTLP Metrics Consumer ===");
        
        KafkaSource<OTLPMetricData> source = KafkaSource.<OTLPMetricData>builder()
                .setBootstrapServers("kafka:29092")
                .setTopics("otel-metrics")
                .setGroupId("flink-otlp-consumer")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new OTLPMetricsDeserializer())
                .build();

        DataStream<OTLPMetricData> metricsStream = env.fromSource(
            source, 
            org.apache.flink.api.common.eventtime.WatermarkStrategy.noWatermarks(), 
            "kafka-otlp-source"
        );

        metricsStream.addSink(new MetricsPrettyPrintSink());

        Properties prometheusProps = new Properties();
        prometheusProps.setProperty("endpoint.url", "http://prometheus:9090/api/v1/write");
        // prometheusProps.setProperty("aws.region", "us-east-1");
        prometheusProps.setProperty("max.request.retry", "3");

        metricsStream
            .map(new OTLPToPrometheusMapper())
            .name("otlp-to-prometheus-mapper")
            .keyBy(new PrometheusTimeSeriesLabelsAndMetricNameKeySelector())
            .sinkTo(createPrometheusSink(prometheusProps))
            .name("prometheus-sink")
            .uid("prometheus-sink");

        log.info("=== Executando o job Flink ===");
        env.execute("OTLP Metrics Consumer");
    }

    private static Sink<PrometheusTimeSeries> createPrometheusSink(Properties prometheusSinkProperties) {
        String endpointUrl = prometheusSinkProperties.getProperty("endpoint.url");
        
        if (endpointUrl == null || endpointUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("endpoint.url not defined");
        }
        
        int maxRequestRetryCount = Integer.parseInt(
            prometheusSinkProperties.getProperty("max.request.retry", "3"));
        
        if (maxRequestRetryCount <= 0) {
            throw new IllegalArgumentException("max.request.retry must be > 0");
        }
        
        log.info("Prometheus sink: endpoint {}", endpointUrl);
        
        return PrometheusSink.builder()
                .setPrometheusRemoteWriteUrl(endpointUrl)
                .setRetryConfiguration(RetryConfiguration.builder()
                        .setMaxRetryCount(maxRequestRetryCount).build())
                .setErrorHandlingBehaviorConfiguration(SinkWriterErrorHandlingBehaviorConfiguration.builder()
                        .onMaxRetryExceeded(DISCARD_AND_CONTINUE)
                        .build())
                .setMetricGroupName("otlpMetrics") // Nome do grupo de métricas
                .build();
    }

    public static class OTLPToPrometheusMapper implements MapFunction<OTLPMetricData, PrometheusTimeSeries> {
        
        @Override
        public PrometheusTimeSeries map(OTLPMetricData metric) throws Exception {
            if (metric.isError) {
                PrometheusTimeSeries.Label[] errorLabels = {
                    new PrometheusTimeSeries.Label("error_type", "deserialization_error"),
                    new PrometheusTimeSeries.Label("service_name", "otlp_consumer")
                };
                
                PrometheusTimeSeries.Sample[] errorSamples = {
                    new PrometheusTimeSeries.Sample(1.0, System.currentTimeMillis())
                };
                
                return new PrometheusTimeSeries(
                    "otlp_processing_errors_total",
                    errorLabels,
                    errorSamples
                );
            }

            // Normalizar nome da métrica para padrão Prometheus
            String normalizedMetricName = normalizeMetricName(metric.metricName);
            
            // Construir labels
            Map<String, String> labelsMap = new HashMap<>();
            labelsMap.put("service_name", metric.serviceName);
            labelsMap.put("metric_type", metric.metricType.toLowerCase().replace(" ", "_").replace("(", "").replace(")", ""));
            
            // Adicionar unit como label se existir
            if (metric.unit != null && !metric.unit.trim().isEmpty()) {
                labelsMap.put("unit", metric.unit);
            }
            
            // Parse e adicionar outros labels dos atributos
            if (metric.attributes != null && !metric.attributes.trim().isEmpty()) {
                Map<String, String> parsedAttributes = parseAttributes(metric.attributes);
                for (Map.Entry<String, String> entry : parsedAttributes.entrySet()) {
                    String labelKey = normalizeLabelName(entry.getKey());
                    labelsMap.put(labelKey, entry.getValue());
                }
            }
            
            // Converter valor para double
            double prometheusValue = parseValueToDouble(metric.value);
            
            // Converter Map para array de Labels
            PrometheusTimeSeries.Label[] labels = labelsMap.entrySet().stream()
                .map(entry -> new PrometheusTimeSeries.Label(entry.getKey(), entry.getValue()))
                .toArray(PrometheusTimeSeries.Label[]::new);
            
            // Criar array de Samples
            PrometheusTimeSeries.Sample[] samples = {
                new PrometheusTimeSeries.Sample(prometheusValue, System.currentTimeMillis())
            };
            
            return new PrometheusTimeSeries(
                normalizedMetricName,
                labels,
                samples
            );
        }
        
        private String normalizeMetricName(String name) {
            if (name == null || name.trim().isEmpty()) {
                return "unknown_metric";
            }
            // Remover caracteres especiais e garantir que comece com letra ou underscore
            String normalized = name.replaceAll("[^a-zA-Z0-9_:]", "_");
            if (!normalized.matches("^[a-zA-Z_:].*")) {
                normalized = "_" + normalized;
            }
            return normalized;
        }

        private String normalizeLabelName(String name) {
            if (name == null || name.trim().isEmpty()) {
                return "unknown_label";
            }
            // Labels não podem ter dois pontos
            String normalized = name.replaceAll("[^a-zA-Z0-9_]", "_");
            if (!normalized.matches("^[a-zA-Z_].*")) {
                normalized = "_" + normalized;
            }
            return normalized;
        }
        
        private double parseValueToDouble(String value) {
            if (value == null || value.trim().isEmpty() || "N/A".equals(value)) {
                return 0.0;
            }
            
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                // Para histogramas, extrair apenas o count
                if (value.startsWith("Count: ")) {
                    try {
                        String countPart = value.split(",")[0].replace("Count: ", "").trim();
                        return Double.parseDouble(countPart);
                    } catch (Exception ex) {
                        return 0.0;
                    }
                }
                return 0.0;
            }
        }
        
        private Map<String, String> parseAttributes(String attributesStr) {
            Map<String, String> attributes = new HashMap<>();
            if (attributesStr == null || attributesStr.trim().isEmpty()) {
                return attributes;
            }
            
            String[] pairs = attributesStr.split(",\\s*");
            for (String pair : pairs) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    String key = kv[0].trim();
                    String value = kv[1].trim();
                    // Remover aspas se existirem
                    if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
                        value = value.substring(1, value.length() - 1);
                    }
                    // Normalizar chave do label
                    key = normalizeLabelName(key);
                    attributes.put(key, value);
                }
            }
            return attributes;
        }
    }

    // Classe para representar dados de métrica processados (mantido igual)
    public static class OTLPMetricData {
        public final String timestamp;
        public final String serviceName;
        public final String metricName;
        public final String metricType;
        public final String value;
        public final String attributes;
        public final String unit;
        public final String description;
        public final boolean isError;
        public final String errorMessage;
        public final String rawData; // Para debug

        public OTLPMetricData(String timestamp, String serviceName, String metricName, 
                            String metricType, String value, String attributes, 
                            String unit, String description, boolean isError, String errorMessage) {
            this.timestamp = timestamp;
            this.serviceName = serviceName;
            this.metricName = metricName;
            this.metricType = metricType;
            this.value = value;
            this.attributes = attributes;
            this.unit = unit;
            this.description = description;
            this.isError = isError;
            this.errorMessage = errorMessage;
            this.rawData = null;
        }

        public OTLPMetricData(String timestamp, String serviceName, String metricName, 
                            String metricType, String value, String attributes, 
                            String unit, String description, boolean isError, String errorMessage, String rawData) {
            this.timestamp = timestamp;
            this.serviceName = serviceName;
            this.metricName = metricName;
            this.metricType = metricType;
            this.value = value;
            this.attributes = attributes;
            this.unit = unit;
            this.description = description;
            this.isError = isError;
            this.errorMessage = errorMessage;
            this.rawData = rawData;
        }
    }

    // Deserializador melhorado (mantido igual ao código menor)
    public static class OTLPMetricsDeserializer implements DeserializationSchema<OTLPMetricData> {
        private static final Logger log = LoggerFactory.getLogger(OTLPMetricsDeserializer.class);
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

        @Override
        public OTLPMetricData deserialize(byte[] message) throws IOException {
            long startTime = System.nanoTime();
            
            try {
                if (message == null || message.length == 0) {
                    log.warn("Mensagem vazia ou nula recebida");
                    return createErrorMetric("Mensagem vazia ou nula", null);
                }

                log.debug("Processando mensagem de {} bytes", message.length);
                
                // Primeiro, vamos tentar identificar se a mensagem está no formato correto
                String hexDump = bytesToHex(message, 32); // Primeiros 32 bytes em hex
                log.debug("Hex dump dos primeiros bytes: {}", hexDump);
                
                // Tentar diferentes abordagens de parsing
                ExportMetricsServiceRequest request = null;
                
                try {
                    // Tentativa 1: Parsing direto
                    request = ExportMetricsServiceRequest.parseFrom(message);
                } catch (Exception e1) {
                    log.warn("Falha no parsing direto: {}", e1.getMessage());
                    
                    try {
                        // Tentativa 2: Parsing com builder (mais tolerante)
                        request = ExportMetricsServiceRequest.newBuilder()
                            .mergeFrom(message)
                            .build();
                    } catch (Exception e2) {
                        log.warn("Falha no parsing com builder: {}", e2.getMessage());
                        
                        // Tentativa 3: Verificar se é uma mensagem de texto ou JSON
                        String textContent = tryDecodeAsText(message);
                        if (textContent != null) {
                            return createErrorMetric("Mensagem parece ser texto/JSON, não protobuf: " + textContent.substring(0, Math.min(100, textContent.length())), hexDump);
                        }
                        
                        // Se chegou até aqui, é realmente um erro de protobuf
                        throw e1; // Relançar o erro original
                    }
                }
                
                if (request == null) {
                    return createErrorMetric("Não foi possível parsear a mensagem como ExportMetricsServiceRequest", hexDump);
                }
                
                log.debug("Request parseado com sucesso. ResourceMetrics count: {}", request.getResourceMetricsCount());
                
                // Processar métricas
                List<OTLPMetricData> metrics = processAllMetrics(request);
                
                if (metrics.isEmpty()) {
                    return createErrorMetric("Nenhuma métrica encontrada na mensagem", hexDump);
                }
                
                long durationMicros = (System.nanoTime() - startTime) / 1_000;
                log.debug("Deserialização concluída em {} µs. Métricas processadas: {}", durationMicros, metrics.size());
                
                // Retorna a primeira métrica (para simplificar)
                return metrics.get(0);
                
            } catch (Exception e) {
                log.error("Erro ao deserializar mensagem OTLP: {}", e.getMessage(), e);
                String hexDump = bytesToHex(message, 64);
                return createErrorMetric("Erro de deserialização: " + e.getMessage(), hexDump);
            }
        }

        private List<OTLPMetricData> processAllMetrics(ExportMetricsServiceRequest request) {
            return request.getResourceMetricsList().stream()
                .flatMap(resourceMetrics -> {
                    String serviceName = extractServiceName(resourceMetrics);
                    return resourceMetrics.getScopeMetricsList().stream()
                        .flatMap(scopeMetrics -> scopeMetrics.getMetricsList().stream()
                            .map(metric -> processMetric(metric, serviceName)));
                })
                .collect(Collectors.toList());
        }

        private String extractServiceName(ResourceMetrics resourceMetrics) {
            if (resourceMetrics.hasResource()) {
                return resourceMetrics.getResource().getAttributesList().stream()
                    .filter(attr -> "service.name".equals(attr.getKey()))
                    .findFirst()
                    .map(attr -> attr.getValue().getStringValue())
                    .orElse("unknown-service");
            }
            return "unknown-service";
        }

        private OTLPMetricData processMetric(Metric metric, String serviceName) {
            String timestamp = formatCurrentTime();
            String metricName = metric.getName();
            String description = metric.getDescription();
            String unit = metric.getUnit();
            
            try {
                if (metric.hasGauge()) {
                    return processGaugeMetric(metric, serviceName, timestamp);
                } else if (metric.hasSum()) {
                    return processSumMetric(metric, serviceName, timestamp);
                } else if (metric.hasHistogram()) {
                    return processHistogramMetric(metric, serviceName, timestamp);
                } else if (metric.hasExponentialHistogram()) {
                    return processExponentialHistogramMetric(metric, serviceName, timestamp);
                } else if (metric.hasSummary()) {
                    return processSummaryMetric(metric, serviceName, timestamp);
                } else {
                    return new OTLPMetricData(timestamp, serviceName, metricName, "unknown", 
                                            "N/A", "", unit, description, false, null);
                }
            } catch (Exception e) {
                log.warn("Erro ao processar métrica '{}': {}", metricName, e.getMessage());
                return new OTLPMetricData(timestamp, serviceName, metricName, "error", 
                                        "N/A", "", unit, description, true, 
                                        "Erro ao processar: " + e.getMessage());
            }
        }

        private OTLPMetricData processGaugeMetric(Metric metric, String serviceName, String timestamp) {
            if (!metric.getGauge().getDataPointsList().isEmpty()) {
                NumberDataPoint dataPoint = metric.getGauge().getDataPointsList().get(0);
                String value = extractValue(dataPoint);
                String attributes = extractAttributes(dataPoint.getAttributesList());
                
                return new OTLPMetricData(timestamp, serviceName, metric.getName(), "Gauge", 
                                        value, attributes, metric.getUnit(), metric.getDescription(), false, null);
            }
            return createErrorMetric("Gauge sem data points", null);
        }

        private OTLPMetricData processSumMetric(Metric metric, String serviceName, String timestamp) {
            if (!metric.getSum().getDataPointsList().isEmpty()) {
                NumberDataPoint dataPoint = metric.getSum().getDataPointsList().get(0);
                String value = extractValue(dataPoint);
                String attributes = extractAttributes(dataPoint.getAttributesList());
                String metricType = "Sum" + (metric.getSum().getIsMonotonic() ? " (Monotonic)" : " (Non-Monotonic)");
                
                return new OTLPMetricData(timestamp, serviceName, metric.getName(), metricType, 
                                        value, attributes, metric.getUnit(), metric.getDescription(), false, null);
            }
            return createErrorMetric("Sum sem data points", null);
        }

        private OTLPMetricData processHistogramMetric(Metric metric, String serviceName, String timestamp) {
            if (!metric.getHistogram().getDataPointsList().isEmpty()) {
                var histogramPoint = metric.getHistogram().getDataPointsList().get(0);
                String value = String.format("Count: %d, Sum: %.2f", 
                                           histogramPoint.getCount(), 
                                           histogramPoint.getSum());
                String attributes = extractAttributes(histogramPoint.getAttributesList());
                
                return new OTLPMetricData(timestamp, serviceName, metric.getName(), "Histogram", 
                                        value, attributes, metric.getUnit(), metric.getDescription(), false, null);
            }
            return createErrorMetric("Histogram sem data points", null);
        }

        private OTLPMetricData processExponentialHistogramMetric(Metric metric, String serviceName, String timestamp) {
            if (!metric.getExponentialHistogram().getDataPointsList().isEmpty()) {
                var expHistPoint = metric.getExponentialHistogram().getDataPointsList().get(0);
                String value = String.format("Count: %d, Sum: %.2f, Scale: %d", 
                                           expHistPoint.getCount(), 
                                           expHistPoint.getSum(),
                                           expHistPoint.getScale());
                String attributes = extractAttributes(expHistPoint.getAttributesList());
                
                return new OTLPMetricData(timestamp, serviceName, metric.getName(), "ExponentialHistogram", 
                                        value, attributes, metric.getUnit(), metric.getDescription(), false, null);
            }
            return createErrorMetric("ExponentialHistogram sem data points", null);
        }

        private OTLPMetricData processSummaryMetric(Metric metric, String serviceName, String timestamp) {
            if (!metric.getSummary().getDataPointsList().isEmpty()) {
                var summaryPoint = metric.getSummary().getDataPointsList().get(0);
                String value = String.format("Count: %d, Sum: %.2f", 
                                           summaryPoint.getCount(), 
                                           summaryPoint.getSum());
                String attributes = extractAttributes(summaryPoint.getAttributesList());
                
                return new OTLPMetricData(timestamp, serviceName, metric.getName(), "Summary", 
                                        value, attributes, metric.getUnit(), metric.getDescription(), false, null);
            }
            return createErrorMetric("Summary sem data points", null);
        }

        private String extractValue(NumberDataPoint dataPoint) {
            if (dataPoint.hasAsDouble()) {
                return String.format("%.6f", dataPoint.getAsDouble());
            } else if (dataPoint.hasAsInt()) {
                return String.valueOf(dataPoint.getAsInt());
            }
            return "N/A";
        }

        private String extractAttributes(List<KeyValue> attributesList) {
            if (attributesList.isEmpty()) {
                return "";
            }
            
            return attributesList.stream()
                .map(attr -> attr.getKey() + "=" + getAttributeValue(attr))
                .collect(Collectors.joining(", "));
        }

        private String getAttributeValue(KeyValue attr) {
            if (attr.getValue().hasStringValue()) {
                return "\"" + attr.getValue().getStringValue() + "\"";
            } else if (attr.getValue().hasIntValue()) {
                return String.valueOf(attr.getValue().getIntValue());
            } else if (attr.getValue().hasBoolValue()) {
                return String.valueOf(attr.getValue().getBoolValue());
            } else if (attr.getValue().hasDoubleValue()) {
                return String.valueOf(attr.getValue().getDoubleValue());
            } else if (attr.getValue().hasArrayValue()) {
                return "[array]";
            } else if (attr.getValue().hasKvlistValue()) {
                return "{kvlist}";
            } else if (attr.getValue().hasBytesValue()) {
                return "[bytes]";
            }
            return "unknown";
        }

        private String tryDecodeAsText(byte[] message) {
            try {
                String text = new String(message, StandardCharsets.UTF_8);
                // Verifica se é um texto legível (não binário)
                if (text.chars().allMatch(c -> c >= 32 && c <= 126 || Character.isWhitespace(c))) {
                    return text;
                }
            } catch (Exception e) {
                // Ignora
            }
            return null;
        }

        private String bytesToHex(byte[] bytes, int maxBytes) {
            if (bytes == null) return "null";
            
            StringBuilder sb = new StringBuilder();
            int limit = Math.min(bytes.length, maxBytes);
            for (int i = 0; i < limit; i++) {
                sb.append(String.format("%02x ", bytes[i]));
                if (i > 0 && (i + 1) % 16 == 0) {
                    sb.append("\n");
                }
            }
            if (bytes.length > maxBytes) {
                sb.append("... (truncated)");
            }
            return sb.toString();
        }

        private String formatCurrentTime() {
            return LocalDateTime.now().format(FORMATTER);
        }

        private OTLPMetricData createErrorMetric(String errorMsg, String rawData) {
            String timestamp = formatCurrentTime();
            return new OTLPMetricData(timestamp, "error", "error", "ERROR", 
                                    "N/A", "", "", "", true, errorMsg, rawData);
        }

        @Override
        public boolean isEndOfStream(OTLPMetricData nextElement) {
            return false;
        }

        @Override
        public TypeInformation<OTLPMetricData> getProducedType() {
            return TypeInformation.of(OTLPMetricData.class);
        }
    }

    // Sink customizado para impressão formatada (mantido igual)
    public static class MetricsPrettyPrintSink implements SinkFunction<OTLPMetricData> {
        private static final Logger log = LoggerFactory.getLogger(MetricsPrettyPrintSink.class);
        private static final String SEPARATOR = "═".repeat(80);
        private static final String LINE = "─".repeat(80);

        @Override
        public void invoke(OTLPMetricData metric, Context context) throws Exception {
            if (metric.isError) {
                printError(metric);
            } else {
                printMetric(metric);
            }
        }

        private void printError(OTLPMetricData metric) {
            System.err.println(SEPARATOR);
            System.err.println("❌ ERRO NO PROCESSAMENTO");
            System.err.println(LINE);
            System.err.println("⏰ Timestamp: " + metric.timestamp);
            System.err.println("🚨 Erro: " + metric.errorMessage);
            
            if (metric.rawData != null) {
                System.err.println("🔍 Raw Data (hex):");
                System.err.println(metric.rawData);
            }
            
            System.err.println(SEPARATOR);
            System.err.println();
        }

        private void printMetric(OTLPMetricData metric) {
            System.out.println(SEPARATOR);
            System.out.println("📊 NOVA MÉTRICA OTLP");
            System.out.println(LINE);
            System.out.println("⏰ Timestamp: " + metric.timestamp);
            System.out.println("🔧 Serviço: " + metric.serviceName);
            System.out.println("📈 Nome: " + metric.metricName);
            System.out.println("📋 Tipo: " + metric.metricType);
            System.out.println("📏 Unidade: " + (metric.unit.isEmpty() ? "N/A" : metric.unit));
            System.out.println("💭 Descrição: " + (metric.description.isEmpty() ? "N/A" : metric.description));
            System.out.println("📊 Valor: " + metric.value);
            
            if (!metric.attributes.isEmpty()) {
                System.out.println("🏷️  Atributos: " + metric.attributes);
            }
            
            System.out.println("🎯 ENVIANDO PARA PROMETHEUS via Remote Write");
            System.out.println(SEPARATOR);
            System.out.println();
            
            // Log também para o sistema de logging
            log.info("Métrica processada e enviada para Prometheus: {} = {} [{}] do serviço '{}'", 
                    metric.metricName, metric.value, metric.metricType, metric.serviceName);
        }
    }
}