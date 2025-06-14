## Modern Monitoring Stack (logs, traces and metrics)

### Objectives

This lab aims to test and integrate key observability tools including Prometheus for metrics collection, Grafana for visualization, Node Exporter for system metrics, OpenTelemetry Collector for telemetry data processing, Tempo for distributed tracing, Loki for log aggregation, and Promtail for log forwarding. All components are containerized using Docker Compose and connected through a shared monitoring network, creating a unified platform for monitoring, logging, and tracing in modern cloud-native environments.

| Tool                    | Description                                                                                                              | Port |
| ----------------------- | ------------------------------------------------------------------------------------------------------------------------ | ---- |
| Grafana                 | Grafana is the open source analytics & monitoring solution for every database                                            | 3000 |
| Grafana Loki            | Loki is a horizontally scalable, highly available, multi-tenant log aggregation system inspired by Prometheus            | 3100 |
| Grafana Tempo           | Grafana Tempo is an open source, easy-to-use, and high-scale distributed tracing backend                                 | 3200 |
| Promtail                | Promtail is an agent which ships the contents of local logs to a private                                                 | 9080 |
| Prometheus              | Power your metrics and alerting with the leading open-source monitoring solution                                         | 9090 |
| Node exporter           | The node_exporter is designed to monitor the host system                                                                 | 9100 |
| OpenTelemetry Collector | The OpenTelemetry Collector offers a vendor-agnostic implementation of how to receive, process and export telemetry data | 4317 |
| Spring Application      | Spring Boot helps you accelerate application development                                                                 | 8080 |
|                         |


```mermaid
graph TD
    A[Node Exporter] -->|metrics| B[Prometheus]
    B -->|datasource| G[Grafana]

    H[Spring App 1] -->|scrap| J[Promtail]
    I[Spring App 2] -->|scrap| J
    J -->|logs| C[Loki]
    C -->|datasource| G

    H -->|traces| F[Otel Collector]
    I -->|traces| F
    F -->|traces| D[Tempo]
    D -->|datasource| G

    %% Optional styling (commented out if unsupported)
    %% style A fill:#e34c26,stroke:#000,color:#fff
    %% style B fill:#e34c26,stroke:#000,color:#fff
    %% style C fill:#f9c846,stroke:#000
    %% style D fill:#f46d3c,stroke:#000
    %% style F fill:#000,color:#fff
    %% style G fill:#f46800,stroke:#000
    %% style H fill:#8fd18f,stroke:#000
    %% style I fill:#8fd18f,stroke:#000
    %% style J fill:#ffdb3a,stroke:#000
```

### Prerequisites

- make
- docker
- docker-compose
- java
- maven/spring-boot
  
### Reproducing

```
docker compose up -d
cd apps/comment-service && mvn spring-boot:run
cd apps/comment-service && mvn spring-boot:run
curl http://localhost:8080/api/v1/posts/1
```

**Grafana**: http://localhost:3000 

### Results

The lab successfully demonstrated the integration of observability tools, with metrics, logs, and traces working seamlessly together. OpenTelemetry Collector proved to be the key component, acting as a flexible bridge between systems and enabling smooth data flow across the entire stack.

![image](./.image/grafana.png) 

### References