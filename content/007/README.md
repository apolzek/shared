# Processing observability data at scale with Apache Flink

### Objectives

The goal of this PoC is to build an environment where telemetry data is ingested by a lightweight OpenTelemetry Collector, which acts purely as a forwarder, sending metrics to Kafka without any advanced processing logic. Instead, the processing will be handled by Apache Flink, a tool designed specifically for stream data processing. This PoC will focus solely on metric data. The final goal is to forward the processed metrics to Prometheus using Flink's Prometheus Sink connector. The Flink processing pipeline will focus on two main objectives:

1. Remove high-cardinality labels

```
checkout_request_latency_seconds{user_id="550e8400-e29b-41d4-a716-446655440000", env="prod", region="us-east"} 0.342
checkout_request_latency_seconds{user_id="c2f8b7a2-4b9e-4f7a-a98b-7e2f7b2f38c4", env="prod", region="us-east"} 0.291
checkout_request_latency_seconds{user_id="d1a2e84b-8cf5-4d9d-a18d-4cfa65ad7f91", env="prod", region="us-east"} 0.518
checkout_request_latency_seconds{user_id="aa6f8a92-bf24-4f7f-89c7-c71e93ef3d91", env="prod", region="us-east"} 0.445
checkout_request_latency_seconds{user_id="f7c26b1d-0198-46e4-a923-7de28a57b4a5", env="prod", region="us-east"} 0.387
```

```
order_payment_failures_total{order_id="a3f5d12c-0b2a-4dc7-83e5-9f72c3a421df", env="prod", region="us-east"} 1
order_payment_failures_total{order_id="91df21e1-1870-4d94-bd07-7fa6d6c7f1df", env="prod", region="us-east"} 1
order_payment_failures_total{order_id="77a8d92c-b302-4d6b-80e7-2f42b920dd24", env="prod", region="us-east"} 1
order_payment_failures_total{order_id="4a9b0d2a-50f2-4c64-9398-1a5e842f82b2", env="prod", region="us-east"} 1
order_payment_failures_total{order_id="6dfb91b2-39b1-47ab-a0ad-0a8b1b4e94c8", env="prod", region="us-east"} 1
```

```
process_cpu_percent{pid="48291", env="prod", region="us-east"} 15.3
process_cpu_percent{pid="48292", env="prod", region="us-east"} 12.1
process_cpu_percent{pid="48293", env="prod", region="us-east"} 18.7
process_cpu_percent{pid="48294", env="prod", region="us-east"} 10.2
process_cpu_percent{pid="48295", env="prod", region="us-east"} 22.5
```

```
temp_file_size_bytes{file_path="/tmp/upload/session_1691831294000/file.txt", env="prod", region="us-east"} 18432
temp_file_size_bytes{file_path="/tmp/upload/session_1691831295000/file.txt", env="prod", region="us-east"} 10240
temp_file_size_bytes{file_path="/tmp/upload/session_1691831296000/file.txt", env="prod", region="us-east"} 56320
temp_file_size_bytes{file_path="/tmp/upload/session_1691831297000/file.txt", env="prod", region="us-east"} 4096
temp_file_size_bytes{file_path="/tmp/upload/session_1691831298000/file.txt", env="prod", region="us-east"} 32768
```

```
auth_event_count{timestamp="2025-08-12T10:03:42.123456Z", env="prod", region="us-east"} 4
auth_event_count{timestamp="2025-08-12T10:03:43.123456Z", env="prod", region="us-east"} 3
auth_event_count{timestamp="2025-08-12T10:03:44.123456Z", env="prod", region="us-east"} 6
auth_event_count{timestamp="2025-08-12T10:03:45.123456Z", env="prod", region="us-east"} 2
auth_event_count{timestamp="2025-08-12T10:03:46.123456Z", env="prod", region="us-east"} 5
```

```
user_login_total{email="joao.silva@example.com", env="prod", region="us-east"} 7
user_login_total{email="maria.oliveira@example.com", env="prod", region="us-east"} 3
user_login_total{email="carlos.santos@example.com", env="prod", region="us-east"} 5
user_login_total{email="ana.paula@example.com", env="prod", region="us-east"} 4
user_login_total{email="pedro.almeida@example.com", env="prod", region="us-east"} 6
```

```
api_request_count{path="/users/abc123/orders/456", env="prod", region="us-east"} 15
api_request_count{path="/users/xyz789/orders/789", env="prod", region="us-east"} 23
api_request_count{path="/users/def456/orders/123", env="prod", region="us-east"} 9
api_request_count{path="/users/ghi321/orders/654", env="prod", region="us-east"} 18
api_request_count{path="/users/jkl654/orders/321", env="prod", region="us-east"} 12
```

```
db_query_duration_seconds{query="SELECT * FROM orders WHERE id = 12345", env="prod", region="us-east"} 0.052
db_query_duration_seconds{query="SELECT * FROM orders WHERE id = 67890", env="prod", region="us-east"} 0.034
db_query_duration_seconds{query="SELECT * FROM orders WHERE id = 24680", env="prod", region="us-east"} 0.045
db_query_duration_seconds{query="SELECT * FROM orders WHERE id = 13579", env="prod", region="us-east"} 0.060
db_query_duration_seconds{query="SELECT * FROM orders WHERE id = 11223", env="prod", region="us-east"} 0.048
```

2. Sanitize sensitive data that may appear in labels

```mermaid
graph TD
    A[Applications / Services] --> B[OpenTelemetry Collector]
    B --> C[Kafka - Telemetry Topic]
    C --> D[Apache Flink]
    D --> D1[Remove High-Cardinality Labels]
    D --> D2[Sanitize Sensitive Label Data]
    D1 --> E[Processed Metrics]
    D2 --> E
    E --> F[Prometheus - via Flink Prometheus Sink]
```

| Serviço                 | Porta(s) Exposta(s) | Versão da Imagem                             |
| ----------------------- | ------------------- | -------------------------------------------- |
| zookeeper               | 2181                | confluentinc/cp-zookeeper:7.9.0              |
| kafka                   | 29092               | confluentinc/cp-kafka:7.9.0                  |
| kafka-ui                | 8080                | provectuslabs/kafka-ui:latest                |
| opentelemetry-collector | 4317, 4318, 9115    | otel/opentelemetry-collector-contrib:0.118.0 |
| filebeat                | 5066                | elastic/filebeat:8.17.4                      |
| postgres                | 5432                | postgres:15                                  |
| pgadmin                 | 5050                | elestio/pgadmin                              |
| flink-jobmanager        | 8081, 6123, 9249    |                                              |
| flink-taskmanager       | 9250                |                                              |
| prometheus              | 9090                | prom/prometheus:latest                       |
| grafana                 | 3000                | grafana/grafana:latest                       |

### Reaseach

1. Stress Prometheus with high cardinality metrics and analyze behavor
2. 

### Solutions and questions

#### Straming vs Batch

#### regex and heuristics

```
Detectar padrões como:
- Números sequenciais longos: \d{8,}
- UUIDs: [0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}
- IPs: \d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}
- Timestamps: ISO8601, epoch, etc.
```

#### Entropia de Shannon

#### Cardinalidade por janela temporal: count distinct por minuto/hora

#### Distribuição de frequência: identificar valores únicos vs repetidos

####  Machine Learning clustering

#### State Backend: RocksDB 

#### Count-Min Sketch

#### T-Digest

#### Bloom Filters

### Prerequisites

- flink-2.1.0
- docker [*Docker version 28.3.3*]
- docker compose
- python [*Python 3.12.3*]
- java [*openjdk 21.0.8*]

### Reproducing

```
docker compose up -d
```

docker exec -it loganomalies-flink-jobmanager-1 bash
ps aux | grep flink
./bin/flink run -py jobs/word_count.py
./bin/flink run -py jobs/anomaly_job.py
docker compose down --remove-orphans
flink run -c com.example.HighCardinalityJob target/<file.jar>

for i in $(seq 1 100); do
  echo "user_event_total2{user_id=\"user_$((RANDOM % 10000))\", session_id=\"sess_$(uuidgen)\"} 1"
done | kafka-console-producer --topic raw-logs --bootstrap-server kafka:29092

kafka-console-producer \
  --topic raw-logs \
  --bootstrap-server kafka:29092 < sample.txt

docker compose down --remove-orphans

for i in $(seq 1 100); do
  echo "user_event_total2{user_id=\"user_$((RANDOM % 10000))\", session_id=\"sess_$(uuidgen)\"} 1"
done | kafka-console-producer --topic raw-logs --bootstrap-server kafka:29092



http://localhost:8080/
http://localhost:5050/browser/


kafka-console-producer \
  --topic raw-logs \
  --bootstrap-server kafka:29092 < sample.txt


mvn clean package
flink run -c com.example.HighCardinalityJob target/high-cardinality-detector-1.0-SNAPSHOT.jar


### Results



### References
🔗


https://grafana.com/blog/2022/10/20/how-to-manage-high-cardinality-metrics-in-prometheus-and-kubernetes/
https://medium.com/@platform.engineers/optimizing-prometheus-storage-handling-high-cardinality-metrics-at-scale-31140c92a7e4
https://last9.io/blog/how-to-manage-high-cardinality-metrics-in-prometheus/
https://grafana.com/blog/2022/12/02/monitoring-high-cardinality-jobs-with-grafana-grafana-loki-and-prometheus/
https://data-mozart.com/cardinality-your-majesty/


Flink
https://aws.amazon.com/pt/blogs/big-data/process-millions-of-observability-events-with-apache-flink-and-write-directly-to-prometheus/
https://mateus-oliveira.medium.com/produzindo-dados-no-apache-kafka-com-python-d072b6aae298
https://dalelane.co.uk/blog/?p=5483
https://blog.devops.dev/unlock-the-power-of-flink-metrics-with-prometheus-and-grafana-docker-compose-example-30d904f996e5
https://current.confluent.io/post-conference-videos-2025/flink-kafka-and-prometheus-better-together-to-improve-efficiency-of-your-observability-platform-lnd25
https://risingwave.com/blog/flink-and-prometheus-cloud-native-monitoring-of-streaming-applications/
https://fosdem.org/2025/schedule/event/fosdem-2025-5726-apache-flink-and-prometheus-better-together-to-improve-the-efficiency-of-your-observability-platform-at-scale/
https://fosdem.org/2025/events/attachments/fosdem-2025-5726-apache-flink-and-prometheus-better-together-to-improve-the-efficiency-of-your-observability-platform-at-scale/slides/238397/FOSDEM_-_9RvAzEV.pdf
https://nightlies.apache.org/flink/flink-docs-master/docs/concepts/overview/
https://nightlies.apache.org/flink/flink-docs-master/docs/deployment/overview/#session-mode
https://www.youtube.com/watch?v=v3rnbzLXwx8

https://github.com/vuanhtuan1407/LogAnomalies.git

https://www.youtube.com/watch?v=trhsC9tcGU4
https://nightlies.apache.org/flink/flink-docs-master/
https://prometheus.io/docs/specs/prw/remote_write_spec/


```
curl -X POST \
  http://localhost:4318/v1/metrics \
  -H "Content-Type: application/json" \
  -d '{
    "resourceMetrics": [
      {
        "resource": {
          "attributes": [
            {
              "key": "service.name",
              "value": { "stringValue": "curl-test-service" }
            }
          ]
        },
        "scopeMetrics": [
          {
            "metrics": [
              {
                "name": "test_metric",
                "description": "Métrica de teste via curl",
                "unit": "1",
                "sum": {
                  "dataPoints": [
                    {
                      "asDouble": 42,
                      "timeUnixNano": "1691145600000000000"
                    }
                  ],
                  "aggregationTemporality": 2,
                  "isMonotonic": false
                }
              }
            ]
          }
        ]
      }
    ]
  }'
```


link run -c com.example.flink.otlp.OTLPMetricsConsumer flink-otlp-consumer-1.0-SNAPSHOT.jar


https://github.com/open-telemetry/opentelemetry-proto-java
https://github.com/apache/flink-connector-prometheus
https://nightlies.apache.org/flink/flink-docs-master/docs/connectors/datastream/prometheus/
https://github.com/aws-samples/flink-prometheus-iot-demo
https://github.com/vuanhtuan1407/LogAnomalies
https://prometheus.io/docs/specs/prw/remote_write_spec
https://github.com/apache/flink-connector-prometheus
https://flink.apache.org/2024/12/05/introducing-the-new-prometheus-connector/