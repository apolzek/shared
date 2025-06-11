```
ocb --config builder-config.yaml
```

```
cd dist
./otelcol-custom --config config.yaml
```

```
curl -X POST http://localhost:4318/v1/traces \
  -H "Content-Type: application/json" \
  --data @span.json
```

Ref

https://github.com/open-telemetry/opentelemetry-collector-releases/releases/
https://github.com/open-telemetry/opentelemetry-collector/tree/main/cmd/builder
https://opentelemetry.io/docs/collector/installation/