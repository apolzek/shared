package com.example.flink.otlp;

import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.metrics.v1.ScopeMetrics;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.configuration.Configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.naming.Context;

import java.util.HashSet;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

import org.apache.flink.connector.prometheus.sink.PrometheusSink;
import org.apache.flink.connector.prometheus.sink.PrometheusSinkConfiguration.RetryConfiguration;
import org.apache.flink.connector.prometheus.sink.PrometheusSinkConfiguration.SinkWriterErrorHandlingBehaviorConfiguration;
import org.apache.flink.connector.prometheus.sink.PrometheusTimeSeries;
import org.apache.flink.connector.prometheus.sink.PrometheusTimeSeriesLabelsAndMetricNameKeySelector;
import static org.apache.flink.connector.prometheus.sink.PrometheusSinkConfiguration.OnErrorBehavior.DISCARD_AND_CONTINUE;
import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.api.common.functions.MapFunction;
import java.util.stream.Collectors;

public class OTLPMetricsConsumer {
    private static final Logger log = LoggerFactory.getLogger(OTLPMetricsConsumer.class);

        // PrometheusSink sink = PrometheusSink.builder()
        // .setPrometheusRemoteWriteUrl(prometheusRemoteWriteUrl)
        // .setRequestSigner(new AmazonManagedPrometheusWriteRequestSigner(prometheusRemoteWriteUrl, prometheusRegion)) // Optional
        // .build();
        
    public static void main(String[] args) throws Exception {
        // Configurar o ambiente Flink
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        
        log.info("=== Iniciando OTLP Metrics Consumer ===");
        
        // Configurações do Kafka
        Properties kafkaProps = new Properties();
        kafkaProps.setProperty("bootstrap.servers", "kafka:29092");
        kafkaProps.setProperty("group.id", "flink-otlp-consumer");
        kafkaProps.setProperty("auto.offset.reset", "latest");
        kafkaProps.setProperty("enable.auto.commit", "true");
        kafkaProps.setProperty("auto.commit.interval.ms", "1000");
        
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

        // Processar cardinalidade e filtrar labels problemáticos
        DataStream<OTLPMetricData> processedStream = metricsStream
            .keyBy(metric -> metric.serviceName + ":" + metric.metricName)
            .process(new HighCardinalityDetector())
            .name("cardinality-detector");

        // Imprimir métricas processadas
        processedStream.addSink(new MetricsPrettyPrintSink());

        Properties prometheusProps = new Properties();
        prometheusProps.setProperty("endpoint.url", "http://prometheus:9090/api/v1/write"); // Ajustar para seu endpoint
        prometheusProps.setProperty("aws.region", "us-east-1"); // Ajustar para sua região se usando AMP
        prometheusProps.setProperty("max.request.retry", "3");

        // Converter para formato Prometheus e enviar
        processedStream
            .map(new OTLPToPrometheusMapper())
            .name("otlp-to-prometheus-mapper")
            .keyBy(new PrometheusTimeSeriesLabelsAndMetricNameKeySelector())
            .sinkTo(createPrometheusSink(prometheusProps))
            .name("prometheus-sink")
            .uid("prometheus-sink");


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
        public final String originalAttributes; // Atributos originais antes da filtragem
        public final String removedLabels; // Labels que foram removidas
        public final long timestampUnix; // Timestamp Unix para formato Prometheus

        public OTLPMetricData(String timestamp, String serviceName, String metricName, 
                            String metricType, String value, String attributes, 
                            String unit, String description, boolean isError, String errorMessage) {
            this(timestamp, serviceName, metricName, metricType, value, attributes, unit, description, isError, errorMessage, null, null);
        }

        public OTLPMetricData(String timestamp, String serviceName, String metricName, 
                            String metricType, String value, String attributes, 
                            String unit, String description, boolean isError, String errorMessage,
                            String originalAttributes, String removedLabels) {
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
            this.originalAttributes = originalAttributes;
            this.removedLabels = removedLabels;
            this.timestampUnix = System.currentTimeMillis();
        }

        /**
         * Formatar métrica no estilo Prometheus: metric_name{label1="value1",label2="value2"} value timestamp
         */
        public String toPrometheusFormat() {
            if (isError) {
                return String.format("# ERROR: %s", errorMessage);
            }

            // Normalizar nome da métrica para padrão Prometheus
            String normalizedMetricName = normalizeMetricName(metricName);
            
            // Construir labels incluindo service_name
            StringBuilder labelsBuilder = new StringBuilder();
            
            // Sempre adicionar service_name como label
            labelsBuilder.append("service_name=\"").append(escapeLabelValue(serviceName)).append("\"");
            
            // Adicionar outros labels se existirem
            if (attributes != null && !attributes.trim().isEmpty()) {
                Map<String, String> parsedAttributes = parseAttributes(attributes);
                for (Map.Entry<String, String> entry : parsedAttributes.entrySet()) {
                    String labelKey = normalizeLabelName(entry.getKey());
                    String labelValue = escapeLabelValue(entry.getValue());
                    labelsBuilder.append(",").append(labelKey).append("=\"").append(labelValue).append("\"");
                }
            }
            
            String labelsString = labelsBuilder.toString();
            
            // Formatar valor para Prometheus (garantir formato numérico)
            String prometheusValue = normalizeValue(value);
            
            // Formato: metric_name{labels} value timestamp
            if (labelsString.isEmpty()) {
                return String.format("%s %s %d", normalizedMetricName, prometheusValue, timestampUnix);
            } else {
                return String.format("%s{%s} %s %d", normalizedMetricName, labelsString, prometheusValue, timestampUnix);
            }
        }

        /**
         * Versão do formato Prometheus sem timestamp (mais limpa para visualização)
         */
        public String toPrometheusFormatSimple() {
            if (isError) {
                return String.format("# ERROR: %s", errorMessage);
            }

            String normalizedMetricName = normalizeMetricName(metricName);
            StringBuilder labelsBuilder = new StringBuilder();
            
            labelsBuilder.append("service_name=\"").append(escapeLabelValue(serviceName)).append("\"");
            
            if (attributes != null && !attributes.trim().isEmpty()) {
                Map<String, String> parsedAttributes = parseAttributes(attributes);
                for (Map.Entry<String, String> entry : parsedAttributes.entrySet()) {
                    String labelKey = normalizeLabelName(entry.getKey());
                    String labelValue = escapeLabelValue(entry.getValue());
                    labelsBuilder.append(",").append(labelKey).append("=\"").append(labelValue).append("\"");
                }
            }
            
            String labelsString = labelsBuilder.toString();
            String prometheusValue = normalizeValue(value);
            
            if (labelsString.isEmpty()) {
                return String.format("%s %s", normalizedMetricName, prometheusValue);
            } else {
                return String.format("%s{%s} %s", normalizedMetricName, labelsString, prometheusValue);
            }
        }

        private String normalizeMetricName(String name) {
            if (name == null || name.trim().isEmpty()) {
                return "unknown_metric";
            }
            // Prometheus metric names devem seguir [a-zA-Z_:][a-zA-Z0-9_:]*
            return name.replaceAll("[^a-zA-Z0-9_:]", "_").replaceAll("^[^a-zA-Z_:]", "_");
        }

        private String normalizeLabelName(String name) {
            if (name == null || name.trim().isEmpty()) {
                return "unknown_label";
            }
            // Prometheus label names devem seguir [a-zA-Z_][a-zA-Z0-9_]*
            return name.replaceAll("[^a-zA-Z0-9_]", "_").replaceAll("^[^a-zA-Z_]", "_");
        }

        private String escapeLabelValue(String value) {
            if (value == null) {
                return "";
            }
            // Escapar caracteres especiais em valores de labels
            return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        }

        private String normalizeValue(String value) {
            if (value == null || value.trim().isEmpty() || "N/A".equals(value)) {
                return "0";
            }
            
            // Se já é um número válido, retorna como está
            try {
                Double.parseDouble(value);
                return value;
            } catch (NumberFormatException e) {
                // Se contém informações de histogram, extrair apenas o count
                if (value.startsWith("Count: ")) {
                    try {
                        String countPart = value.split(",")[0].replace("Count: ", "").trim();
                        return countPart;
                    } catch (Exception ex) {
                        return "0";
                    }
                }
                return "0";
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
                    attributes.put(kv[0].trim(), kv[1].trim());
                }
            }
            return attributes;
        }
    }

    private static Sink<PrometheusTimeSeries> createPrometheusSink(Properties prometheusSinkProperties) {
        String endpointUrl = prometheusSinkProperties.getProperty("endpoint.url");
        // Preconditions.checkNotNull(endpointUrl, "endpoint.url not defined");
        
        // int maxRequestRetryCount = PropertiesUtil.getInt(prometheusSinkProperties, "max.request.retry", DEFAULT_MAX_REQUEST_RETRY);
        // Preconditions.checkArgument(maxRequestRetryCount > 0, "max.request.retry must be > 0");
        
        // LOGGER.info("Prometheus sink: endpoint {}", endpointUrl);
        
        return PrometheusSink.builder()
                .setPrometheusRemoteWriteUrl(endpointUrl)
                .setRetryConfiguration(RetryConfiguration.builder()
                        .setMaxRetryCount(3).build())
                .setErrorHandlingBehaviorConfiguration(SinkWriterErrorHandlingBehaviorConfiguration.builder()
                        .onMaxRetryExceeded(DISCARD_AND_CONTINUE)
                        .build())
                .setMetricGroupName("kinesisAnalytics") // Forward connector metrics to CloudWatch
                .build();
    }


    //toaq
    // Deserializador melhorado
    public static class OTLPMetricsDeserializer implements DeserializationSchema<OTLPMetricData> {
        private static final Logger log = LoggerFactory.getLogger(OTLPMetricsDeserializer.class);
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

        @Override
        public OTLPMetricData deserialize(byte[] message) throws IOException {
            long startTime = System.nanoTime();
            
            try {
                if (message == null || message.length == 0) {
                    log.warn("Mensagem vazia ou nula recebida");
                    return createErrorMetric("Mensagem vazia ou nula");
                }

                log.debug("Processando mensagem de {} bytes", message.length);
                
                ExportMetricsServiceRequest request = ExportMetricsServiceRequest.parseFrom(message);
                
                // Processar a primeira métrica encontrada (para simplificar)
                for (ResourceMetrics resourceMetrics : request.getResourceMetricsList()) {
                    String serviceName = extractServiceName(resourceMetrics);
                    
                    for (ScopeMetrics scopeMetrics : resourceMetrics.getScopeMetricsList()) {
                        for (Metric metric : scopeMetrics.getMetricsList()) {
                            OTLPMetricData result = processMetric(metric, serviceName);
                            
                            long durationMicros = (System.nanoTime() - startTime) / 1_000;
                            log.debug("Deserialização concluída em {} µs", durationMicros);
                            
                            return result;
                        }
                    }
                }
                
                return createErrorMetric("Nenhuma métrica encontrada na mensagem");
                
            } catch (Exception e) {
                log.error("Erro ao deserializar mensagem OTLP: {}", e.getMessage(), e);
                return createErrorMetric("Erro de deserialização: " + e.getMessage());
            }
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
            String timestamp = LocalDateTime.now().format(FORMATTER);
            String metricName = metric.getName();
            String description = metric.getDescription();
            String unit = metric.getUnit();
            
            if (metric.hasGauge()) {
                return processGaugeMetric(metric, serviceName, timestamp);
            } else if (metric.hasSum()) {
                return processSumMetric(metric, serviceName, timestamp);
            } else if (metric.hasHistogram()) {
                return processHistogramMetric(metric, serviceName, timestamp);
            } else {
                return new OTLPMetricData(timestamp, serviceName, metricName, "unknown", 
                                        "N/A", "", unit, description, false, null);
            }
        }

        private OTLPMetricData processGaugeMetric(Metric metric, String serviceName, String timestamp) {
            if (!metric.getGauge().getDataPointsList().isEmpty()) {
                NumberDataPoint dataPoint = metric.getGauge().getDataPointsList().get(0);
                String value = extractValue(dataPoint);
                String attributes = extractAttributes(dataPoint);
                
                return new OTLPMetricData(timestamp, serviceName, metric.getName(), "Gauge", 
                                        value, attributes, metric.getUnit(), metric.getDescription(), false, null);
            }
            return createErrorMetric("Gauge sem data points");
        }

        private OTLPMetricData processSumMetric(Metric metric, String serviceName, String timestamp) {
            if (!metric.getSum().getDataPointsList().isEmpty()) {
                NumberDataPoint dataPoint = metric.getSum().getDataPointsList().get(0);
                String value = extractValue(dataPoint);
                String attributes = extractAttributes(dataPoint);
                String metricType = "Sum" + (metric.getSum().getIsMonotonic() ? " (Monotonic)" : " (Non-Monotonic)");
                
                return new OTLPMetricData(timestamp, serviceName, metric.getName(), metricType, 
                                        value, attributes, metric.getUnit(), metric.getDescription(), false, null);
            }
            return createErrorMetric("Sum sem data points");
        }

        private OTLPMetricData processHistogramMetric(Metric metric, String serviceName, String timestamp) {
            if (!metric.getHistogram().getDataPointsList().isEmpty()) {
                var histogramPoint = metric.getHistogram().getDataPointsList().get(0);
                String value = String.format("Count: %d, Sum: %.2f", 
                                           histogramPoint.getCount(), 
                                           histogramPoint.getSum());
                String attributes = extractHistogramAttributes(histogramPoint);
                
                return new OTLPMetricData(timestamp, serviceName, metric.getName(), "Histogram", 
                                        value, attributes, metric.getUnit(), metric.getDescription(), false, null);
            }
            return createErrorMetric("Histogram sem data points");
        }

        private String extractValue(NumberDataPoint dataPoint) {
            if (dataPoint.hasAsDouble()) {
                return String.format("%.6f", dataPoint.getAsDouble());
            } else if (dataPoint.hasAsInt()) {
                return String.valueOf(dataPoint.getAsInt());
            }
            return "N/A";
        }

        private String extractAttributes(NumberDataPoint dataPoint) {
            return dataPoint.getAttributesList().stream()
                .map(attr -> attr.getKey() + "=" + getAttributeValue(attr))
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        }

        private String extractHistogramAttributes(io.opentelemetry.proto.metrics.v1.HistogramDataPoint histogramPoint) {
            return histogramPoint.getAttributesList().stream()
                .map(attr -> attr.getKey() + "=" + getAttributeValue(attr))
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        }

        private String getAttributeValue(io.opentelemetry.proto.common.v1.KeyValue attr) {
            if (attr.getValue().hasStringValue()) {
                return attr.getValue().getStringValue();
            } else if (attr.getValue().hasIntValue()) {
                return String.valueOf(attr.getValue().getIntValue());
            } else if (attr.getValue().hasBoolValue()) {
                return String.valueOf(attr.getValue().getBoolValue());
            } else if (attr.getValue().hasDoubleValue()) {
                return String.valueOf(attr.getValue().getDoubleValue());
            }
            return "unknown";
        }

        private OTLPMetricData createErrorMetric(String errorMsg) {
            String timestamp = LocalDateTime.now().format(FORMATTER);
            return new OTLPMetricData(timestamp, "error", "error", "ERROR", 
                                    "N/A", "", "", "", true, errorMsg);
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


    // OTLP em Prometheus
    public static class OTLPToPrometheusMapper implements org.apache.flink.api.common.functions.MapFunction<OTLPMetricData, PrometheusTimeSeries> {
        
        @Override
        public PrometheusTimeSeries map(OTLPMetricData metric) throws Exception {
            if (metric.isError) {
                // Para métricas com erro, criar uma métrica de erro
                PrometheusTimeSeries.Label[] errorLabels = {
                    new PrometheusTimeSeries.Label("error_type", "deserialization_error"),
                    new PrometheusTimeSeries.Label("service_name", "otlp_consumer")
                };
                
                PrometheusTimeSeries.Sample[] errorSamples = {
                    new PrometheusTimeSeries.Sample(1.0, metric.timestampUnix)
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
                new PrometheusTimeSeries.Sample(prometheusValue, metric.timestampUnix)
            };
            
            // Usar o construtor correto do PrometheusTimeSeries
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
                    // Normalizar chave do label
                    key = normalizeLabelName(key);
                    attributes.put(key, value);
                }
            }
            return attributes;
        }
    }    
    

    // Detector de alta cardinalidade com state management otimizado (código inalterado)
    public static class HighCardinalityDetector extends KeyedProcessFunction<String, OTLPMetricData, OTLPMetricData> {
        private static final Logger log = LoggerFactory.getLogger(HighCardinalityDetector.class);
        
        // Threshold para considerar alta cardinalidade
        private static final int CARDINALITY_THRESHOLD = 10;
        
        // Estado para tracking de cardinalidade por label (usando String serializado)
        private transient MapState<String, String> labelCardinalityState;
        
        // Estado para labels já removidas (usando String concatenado)
        private transient ValueState<String> removedLabelsState;
        
        // Cache local para performance (reset a cada checkpoint)
        private transient Map<String, Set<String>> localCache;
        private transient Set<String> localRemovedLabels;

        @Override
        public void open(Configuration parameters) throws Exception {
            super.open(parameters);
            
            // Configurar estados usando String simples (evita problemas de generics)
            ValueStateDescriptor<String> removedLabelsDescriptor = 
                new ValueStateDescriptor<>("removed-labels", String.class);
            removedLabelsState = getRuntimeContext().getState(removedLabelsDescriptor);
            
            MapStateDescriptor<String, String> cardinalityDescriptor = 
                new MapStateDescriptor<>("label-cardinality", String.class, String.class);
            labelCardinalityState = getRuntimeContext().getMapState(cardinalityDescriptor);
            
            // Cache local para reduzir acesso ao state backend
            localCache = new HashMap<>();
            localRemovedLabels = new HashSet<>();
            
            log.info("HighCardinalityDetector inicializado para key: {}", 
                    getRuntimeContext().getIndexOfThisSubtask());
        }

        @Override
        public void processElement(OTLPMetricData metric, Context ctx, Collector<OTLPMetricData> out) throws Exception {
            
            if (metric.isError) {
                out.collect(metric);
                return;
            }
            
            // Carregar estado de labels removidas (apenas uma vez por key)
            if (localRemovedLabels.isEmpty()) {
                String removedLabelsStr = removedLabelsState.value();
                if (removedLabelsStr != null && !removedLabelsStr.trim().isEmpty()) {
                    String[] removedArray = removedLabelsStr.split(",");
                    for (String label : removedArray) {
                        if (!label.trim().isEmpty()) {
                            localRemovedLabels.add(label.trim());
                        }
                    }
                }
            }
            
            // Parse dos atributos existentes
            Map<String, String> currentAttributes = parseAttributes(metric.attributes);
            Map<String, String> filteredAttributes = new HashMap<>(currentAttributes);
            boolean attributesChanged = false;
            Set<String> removedInThisMetric = new HashSet<>();
            
            // Analisar cada label para cardinalidade
            for (Map.Entry<String, String> attr : currentAttributes.entrySet()) {
                String labelKey = attr.getKey();
                String labelValue = attr.getValue();
                
                // Skip labels já removidas
                if (localRemovedLabels.contains(labelKey)) {
                    filteredAttributes.remove(labelKey);
                    attributesChanged = true;
                    removedInThisMetric.add(labelKey);
                    continue;
                }
                
                // Verificar cardinalidade usando cache local primeiro
                Set<String> values = localCache.computeIfAbsent(labelKey, k -> {
                    try {
                        String stateValuesStr = labelCardinalityState.get(k);
                        if (stateValuesStr != null && !stateValuesStr.trim().isEmpty()) {
                            Set<String> stateValues = new HashSet<>();
                            String[] valuesArray = stateValuesStr.split("\\|");
                            for (String value : valuesArray) {
                                if (!value.trim().isEmpty()) {
                                    stateValues.add(value.trim());
                                }
                            }
                            return stateValues;
                        }
                        return new HashSet<>();
                    } catch (Exception e) {
                        log.warn("Erro ao acessar state para label {}: {}", k, e.getMessage());
                        return new HashSet<>();
                    }
                });
                
                // Adicionar novo valor
                boolean isNewValue = values.add(labelValue);
                
                // Verificar se excedeu threshold
                if (values.size() > CARDINALITY_THRESHOLD) {
                    
                    // Primeira vez que excede - gerar alerta
                    if (!localRemovedLabels.contains(labelKey)) {
                        printCardinalityAlert(metric, labelKey, values.size(), currentAttributes, filteredAttributes);
                        
                        // Marcar como removida
                        localRemovedLabels.add(labelKey);
                        
                        // Persistir no state (concatenar com vírgula)
                        String currentRemovedLabels = removedLabelsState.value();
                        if (currentRemovedLabels == null || currentRemovedLabels.trim().isEmpty()) {
                            removedLabelsState.update(labelKey);
                        } else {
                            removedLabelsState.update(currentRemovedLabels + "," + labelKey);
                        }
                        
                        // Limpar do estado de cardinalidade (liberar memória)
                        labelCardinalityState.remove(labelKey);
                        localCache.remove(labelKey);
                    }
                    
                    // Remover da métrica atual
                    filteredAttributes.remove(labelKey);
                    attributesChanged = true;
                    removedInThisMetric.add(labelKey);
                    
                } else if (isNewValue) {
                    // Valor novo mas ainda dentro do threshold - atualizar state
                    try {
                        // Serializar Set como String separado por |
                        String serializedValues = String.join("|", values);
                        labelCardinalityState.put(labelKey, serializedValues);
                    } catch (Exception e) {
                        log.warn("Erro ao atualizar state para label {}: {}", labelKey, e.getMessage());
                    }
                }
            }
            
            // Emitir métrica (modificada ou original)
            if (attributesChanged) {
                String newAttributesStr = formatAttributes(filteredAttributes);
                String originalAttributesStr = formatAttributes(currentAttributes);
                String removedLabelsStr = String.join(", ", removedInThisMetric);
                
                OTLPMetricData modifiedMetric = new OTLPMetricData(
                    metric.timestamp, metric.serviceName, metric.metricName,
                    metric.metricType, metric.value, newAttributesStr,
                    metric.unit, metric.description, false, null,
                    originalAttributesStr, removedLabelsStr
                );
                out.collect(modifiedMetric);
            } else {
                out.collect(metric);
            }
        }
        
        private void printCardinalityAlert(OTLPMetricData metric, String problematicLabel, 
                                         int cardinality, Map<String, String> before, 
                                         Map<String, String> after) {
            
            String separator = "🚨" + "=".repeat(100) + "🚨";
            
            System.out.println();
            System.out.println(separator);
            System.out.println("⚠️  ALTA CARDINALIDADE DETECTADA - LABEL REMOVIDA");
            System.out.println(separator);
            System.out.println("📊 Serviço: " + metric.serviceName);
            System.out.println("📈 Métrica: " + metric.metricName);
            System.out.println("🏷️  Label Problemática: " + problematicLabel);
            System.out.println("🔢 Cardinalidade: " + cardinality + " (threshold: " + CARDINALITY_THRESHOLD + ")");
            System.out.println("⏰ Timestamp: " + metric.timestamp);
            
            // Mostrar antes e depois
            System.out.println();
            System.out.println("📝 ANTES (com " + before.size() + " labels):");
            before.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String marker = entry.getKey().equals(problematicLabel) ? "❌ " : "✅ ";
                    System.out.println("   " + marker + entry.getKey() + "=" + entry.getValue());
                });
            
            after.remove(problematicLabel); // garantir remoção para display
            System.out.println();
            System.out.println("📝 DEPOIS (com " + after.size() + " labels):");
            after.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> 
                    System.out.println("   ✅ " + entry.getKey() + "=" + entry.getValue()));
            
            System.out.println();
            System.out.println("💡 Ação: Label '" + problematicLabel + "' será removida de futuras métricas desta série");
            System.out.println(separator);
            System.out.println();
            
            // Log estruturado para monitoring
            log.warn("High cardinality label removed: service={}, metric={}, label={}, cardinality={}", 
                    metric.serviceName, metric.metricName, problematicLabel, cardinality);
        }
        
        private Map<String, String> parseAttributes(String attributesStr) {
            Map<String, String> attributes = new HashMap<>();
            if (attributesStr == null || attributesStr.trim().isEmpty()) {
                return attributes;
            }
            
            // Parse formato "key1=value1, key2=value2"
            String[] pairs = attributesStr.split(",\\s*");
            for (String pair : pairs) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    attributes.put(kv[0].trim(), kv[1].trim());
                }
            }
            return attributes;
        }
        
        private String formatAttributes(Map<String, String> attributes) {
            if (attributes.isEmpty()) {
                return "";
            }
            
            return attributes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        }
    }

    // Sink customizado para impressão formatada com formato Prometheus
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
            
            // **FORMATO PROMETHEUS - DESTAQUE PRINCIPAL**
            System.out.println();
            System.out.println("🎯 FORMATO PROMETHEUS:");
            System.out.println("   " + metric.toPrometheusFormatSimple());
            System.out.println();
            
            // Verificar se houve remoção de labels
            if (metric.removedLabels != null && !metric.removedLabels.trim().isEmpty()) {
                System.out.println("🔥 LABELS REMOVIDAS POR ALTA CARDINALIDADE:");
                System.out.println("   🗑️  Labels removidas: " + metric.removedLabels);
                System.out.println();
                System.out.println("📝 COMPARAÇÃO FORMATO PROMETHEUS:");
                
                // Criar métrica temporária com atributos originais para mostrar diferença
                OTLPMetricData originalMetric = new OTLPMetricData(
                    metric.timestamp, metric.serviceName, metric.metricName,
                    metric.metricType, metric.value, metric.originalAttributes,
                    metric.unit, metric.description, false, null
                );
                
                System.out.println("   📥 ORIGINAL: " + originalMetric.toPrometheusFormatSimple());
                System.out.println("   📤 FILTRADA: " + metric.toPrometheusFormatSimple());
                System.out.println();
                
                System.out.println("📝 DETALHES DOS ATRIBUTOS:");
                System.out.println("   📥 ORIGINAL: " + (metric.originalAttributes.isEmpty() ? "(sem atributos)" : metric.originalAttributes));
                System.out.println("   📤 FILTRADA: " + (metric.attributes.isEmpty() ? "(sem atributos)" : metric.attributes));
                System.out.println();
            } else {
                if (!metric.attributes.isEmpty()) {
                    System.out.println("🏷️  Atributos: " + metric.attributes);
                } else {
                    System.out.println("🏷️  Atributos: (nenhum)");
                }
            }
            
            // Mostrar também o formato completo com timestamp (opcional)
            System.out.println("📅 FORMATO COMPLETO (com timestamp):");
            System.out.println("   " + metric.toPrometheusFormat());
            
            System.out.println(SEPARATOR);
            System.out.println();
            
            // Log estruturado
            if (metric.removedLabels != null && !metric.removedLabels.trim().isEmpty()) {
                log.info("Métrica com labels removidas: {} | Prometheus: {} | Removidas: {}", 
                        metric.metricName, metric.toPrometheusFormatSimple(), metric.removedLabels);
            } else {
                log.info("Métrica processada: {} | Prometheus: {}", 
                        metric.metricName, metric.toPrometheusFormatSimple());
            }
        }
    }
}