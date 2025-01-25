for i in {1..100}; do ./otelgen --otel-exporter-otlp-endpoint localhost:4317 --insecure traces single -s "otelgen"; sleep 0.5; done
