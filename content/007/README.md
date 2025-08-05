https://github.com/vuanhtuan1407/LogAnomalies.git

## Data Preparation
1. Download from [BGL.zip](https://zenodo.org/records/8196385/files/BGL.zip?download=1)
2. `BGL_templates.csv` can be found [here](https://github.com/logpai/loghub/blob/master/BGL/BGL_templates.csv)
2. Extract zip file and read data description at `/BGL/README.md`

docker exec -it loganomalies-flink-jobmanager-1 bash
./bin/flink run -py jobs/word_count.py
./bin/flink run -py jobs/anomaly_job.py

```
# Este teste deve retornar "Alert" porque tem HARDWARE=1 e ERROR=1
curl -X POST "http://host.docker.internal:8000/predict/" \
     -H "Content-Type: application/json" \
     -d '{
       "rack": 2,
       "midplane": 1,
       "node_type": 0,
       "node_no": 4,
       "control_IO": 0,
       "jid": 12345,
       "uid": 67890,
       "type": 1,
       "eventId": 123,
       "channel_A": 1,
       "channel_C": 0,
       "channel_D": 0,
       "channel_E": 0,
       "channel_S": 0,
       "component_APP": 0,
       "component_BGLMASTER": 0,
       "component_CMCS": 0,
       "component_DISCOVERY": 0,
       "component_HARDWARE": 1,
       "component_KERNEL": 0,
       "component_LINKCARD": 0,
       "component_MMCS": 0,
       "component_MONITOR": 0,
       "component_SERV_NET": 0,
       "level_ERROR": 1,
       "level_FAILURE": 0,
       "level_FATAL": 0,
       "level_INFO": 0,
       "level_SEVERE": 0,
       "level_WARNING": 0
     }'
```

```
# Este teste deve retornar "Non-alert" porque é só INFO normal
curl -X POST "http://localhost:8000/predict/" \
     -H "Content-Type: application/json" \
     -d '{
       "rack": 1,
       "midplane": 1,
       "node_type": 0,
       "node_no": 4,
       "control_IO": 0,
       "jid": 12345,
       "uid": 67890,
       "type": 1,
       "eventId": 999,
       "channel_A": 1,
       "channel_C": 0,
       "channel_D": 0,
       "channel_E": 0,
       "channel_S": 0,
       "component_APP": 1,
       "component_BGLMASTER": 0,
       "component_CMCS": 0,
       "component_DISCOVERY": 0,
       "component_HARDWARE": 0,
       "component_KERNEL": 0,
       "component_LINKCARD": 0,
       "component_MMCS": 0,
       "component_MONITOR": 0,
       "component_SERV_NET": 0,
       "level_ERROR": 0,
       "level_FAILURE": 0,
       "level_FATAL": 0,
       "level_INFO": 1,
       "level_SEVERE": 0,
       "level_WARNING": 0
     }'
```

$ ./bin/start-cluster.sh
 ps aux | grep flink
 $ ./bin/stop-cluster.sh
./bin/flink run examples/streaming/WordCount.jar


https://www.youtube.com/watch?v=trhsC9tcGU4
https://nightlies.apache.org/flink/flink-docs-master/
https://prometheus.io/docs/specs/prw/remote_write_spec/

http://localhost:8080/
http://localhost:5050/browser/


```
for i in $(seq 1 10000); do   echo "http_requests_total{method=\"GET\",endpoint=\"/api/v$((i % 100))\"} $((RANDOM % 5000))";    echo "active_sessions_total{user_id=\"user_$((RANDOM % 10000))\",session_id=\"sess_$(uuidgen)\"} 1";    echo "cpu_usage_percentage{core=\"$(($i % 4))\"} $(awk -v min=10 -v max=90 'BEGIN{srand(); print int(min+rand()*(max-min))}')";    echo "queue_length{queue=\"email\"} $((RANDOM % 50))"; done | kafka-console-producer --topic raw-logs --bootstrap-server kafka:29092
```

docker compose down --remove-orphans

flink run -c com.example.HighCardinalityJob target/h



for i in $(seq 1 100); do
  echo "user_event_total2{user_id=\"user_$((RANDOM % 10000))\", session_id=\"sess_$(uuidgen)\"} 1"
done | kafka-console-producer --topic raw-logs --bootstrap-server kafka:29092


kafka-console-producer \
  --topic raw-logs \
  --bootstrap-server kafka:29092 < sample.txt



mvn clean package
flink run -c com.example.HighCardinalityJob target/high-cardinality-detector-1.0-SNAPSHOT.jar



## Data Preparation
1. Download from [BGL.zip](https://zenodo.org/records/8196385/files/BGL.zip?download=1)
2. `BGL_templates.csv` can be found [here](https://github.com/logpai/loghub/blob/master/BGL/BGL_templates.csv)
2. Extract zip file and read data description at `/BGL/README.md`


docker exec -it loganomalies-flink-jobmanager-1 bash
./bin/flink run -py jobs/word_count.py
./bin/flink run -py jobs/anomaly_job.py

```
# Este teste deve retornar "Alert" porque tem HARDWARE=1 e ERROR=1
curl -X POST "http://host.docker.internal:8000/predict/" \
     -H "Content-Type: application/json" \
     -d '{
       "rack": 2,
       "midplane": 1,
       "node_type": 0,
       "node_no": 4,
       "control_IO": 0,
       "jid": 12345,
       "uid": 67890,
       "type": 1,
       "eventId": 123,
       "channel_A": 1,
       "channel_C": 0,
       "channel_D": 0,
       "channel_E": 0,
       "channel_S": 0,
       "component_APP": 0,
       "component_BGLMASTER": 0,
       "component_CMCS": 0,
       "component_DISCOVERY": 0,
       "component_HARDWARE": 1,
       "component_KERNEL": 0,
       "component_LINKCARD": 0,
       "component_MMCS": 0,
       "component_MONITOR": 0,
       "component_SERV_NET": 0,
       "level_ERROR": 1,
       "level_FAILURE": 0,
       "level_FATAL": 0,
       "level_INFO": 0,
       "level_SEVERE": 0,
       "level_WARNING": 0
     }'
```

```
# Este teste deve retornar "Non-alert" porque é só INFO normal
curl -X POST "http://localhost:8000/predict/" \
     -H "Content-Type: application/json" \
     -d '{
       "rack": 1,
       "midplane": 1,
       "node_type": 0,
       "node_no": 4,
       "control_IO": 0,
       "jid": 12345,
       "uid": 67890,
       "type": 1,
       "eventId": 999,
       "channel_A": 1,
       "channel_C": 0,
       "channel_D": 0,
       "channel_E": 0,
       "channel_S": 0,
       "component_APP": 1,
       "component_BGLMASTER": 0,
       "component_CMCS": 0,
       "component_DISCOVERY": 0,
       "component_HARDWARE": 0,
       "component_KERNEL": 0,
       "component_LINKCARD": 0,
       "component_MMCS": 0,
       "component_MONITOR": 0,
       "component_SERV_NET": 0,
       "level_ERROR": 0,
       "level_FAILURE": 0,
       "level_FATAL": 0,
       "level_INFO": 1,
       "level_SEVERE": 0,
       "level_WARNING": 0
     }'
```

$ ./bin/start-cluster.sh
 ps aux | grep flink
 $ ./bin/stop-cluster.sh
./bin/flink run examples/streaming/WordCount.jar


https://www.youtube.com/watch?v=trhsC9tcGU4
https://nightlies.apache.org/flink/flink-docs-master/
https://prometheus.io/docs/specs/prw/remote_write_spec/

http://localhost:8080/
http://localhost:5050/browser/


```
for i in $(seq 1 10000); do   echo "http_requests_total{method=\"GET\",endpoint=\"/api/v$((i % 100))\"} $((RANDOM % 5000))";    echo "active_sessions_total{user_id=\"user_$((RANDOM % 10000))\",session_id=\"sess_$(uuidgen)\"} 1";    echo "cpu_usage_percentage{core=\"$(($i % 4))\"} $(awk -v min=10 -v max=90 'BEGIN{srand(); print int(min+rand()*(max-min))}')";    echo "queue_length{queue=\"email\"} $((RANDOM % 50))"; done | kafka-console-producer --topic raw-logs --bootstrap-server kafka:29092
```

docker compose down --remove-orphans

flink run -c com.example.HighCardinalityJob target/h



for i in $(seq 1 100); do
  echo "user_event_total2{user_id=\"user_$((RANDOM % 10000))\", session_id=\"sess_$(uuidgen)\"} 1"
done | kafka-console-producer --topic raw-logs --bootstrap-server kafka:29092


kafka-console-producer \
  --topic raw-logs \
  --bootstrap-server kafka:29092 < sample.txt



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