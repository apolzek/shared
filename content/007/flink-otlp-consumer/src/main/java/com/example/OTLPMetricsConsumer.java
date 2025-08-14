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

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.List;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;

public class OTLPMetricsConsumer {
    private static final Logger log = LoggerFactory.getLogger(OTLPMetricsConsumer.class);

    public static void main(String[] args) throws Exception {
        // Configurar o ambiente Flink
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        
        log.info("=== Iniciando OTLP Metrics Consumer ===");
        
        // Configurar a fonte Kafka
        KafkaSource<OTLPMetricData> source = KafkaSource.<OTLPMetricData>builder()
                .setBootstrapServers("kafka:29092")
                .setTopics("otel-metrics")
                .setGroupId("flink-otlp-consumer")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new OTLPMetricsDeserializer())
                .build();

        // Criar stream de dados
        DataStream<OTLPMetricData> metricsStream = env.fromSource(
            source, 
            org.apache.flink.api.common.eventtime.WatermarkStrategy.noWatermarks(), 
            "kafka-otlp-source"
        );

        // Processar e imprimir as métricas de forma amigável
        metricsStream.addSink(new MetricsPrettyPrintSink());

        // Executar o job
        log.info("=== Executando o job Flink ===");
        env.execute("OTLP Metrics Consumer");
    }

    // Classe para representar dados de métrica processados
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

    // Deserializador melhorado com tratamento robusto de erros
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

    // Sink customizado para impressão formatada
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
            
            System.out.println(SEPARATOR);
            System.out.println();
            
            // Log também para o sistema de logging
            log.info("Métrica processada: {} = {} [{}] do serviço '{}'", 
                    metric.metricName, metric.value, metric.metricType, metric.serviceName);
        }
    }
}