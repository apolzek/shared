package com.example;

import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class HighCardinalityJob {
    private static final Logger LOG = LoggerFactory.getLogger(HighCardinalityJob.class);

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Configurações do Kafka
        Properties properties = new Properties();
        properties.setProperty("bootstrap.servers", "kafka:29092");
        properties.setProperty("group.id", "high-cardinality-detector");

        // Consumidor Kafka
        FlinkKafkaConsumer<String> kafkaSource = new FlinkKafkaConsumer<>(
                "raw-logs",
                new SimpleStringSchema(),
                properties
        );
        kafkaSource.setStartFromLatest();

        // Lê dados do Kafka
        DataStream<String> source = env.addSource(kafkaSource);

        // Instancia o tracker
        CardinalityTracker tracker = new CardinalityTracker();

        // Processa as métricas
        DataStream<Tuple4<String, String, String, Boolean>> processedMetrics = source
                .flatMap(new PrometheusMetricProcessor(tracker))
                .filter(tuple -> !tuple.f0.isEmpty());

        // Para depuração, imprime os resultados
        processedMetrics.print();

        LOG.info("🚀 Starting Prometheus High Cardinality Detector...");
        LOG.info("📊 Configuration:");
        LOG.info("   High Cardinality Threshold: {}", 10);
        LOG.info("   Monitoring Kafka topic 'raw-logs' for high cardinality labels...");

        // Executa o job
        env.execute("High Cardinality Detector");
    }
}